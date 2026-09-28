package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.shared.client.ui.UiIcon;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Spring;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.List;

final class DesktopDock {
    record Item(UiIcon.Kind glyph, String labelKey, Runnable action, boolean divided) {
    }

    private static final float TILE = 26.0f;
    // WHY: в Dock macOS 27 иконки лежат прямо в стеклянной таблетке, отдельной плитки под значком нет,
    // WHY: а наведение только чуть подсвечивает круг под ним
    private static final float HOVER_GLOW = 0.06f;
    private static final float GAP = 5.0f;
    private static final float PAD = 5.0f;
    private static final float DIVIDER = 9.0f;
    private static final float BOTTOM = 6.0f;
    // WHY: владелец счёл увеличение соседей и прыжки Dock слишком броскими: растёт и светлеет только
    // WHY: значок под курсором, и едва заметно, а запуск - короткое мягкое нажатие без прыжка
    private static final float HOVER_SCALE = 0.08f;
    private static final float GROW_SPEED = 16.0f;
    private static final float LABEL_SPEED = 18.0f;
    private static final float LABEL_GAP = 7.0f;
    private static final float LABEL_SCALE = 0.85f;
    private static final float LAUNCH_SECONDS = 0.25f;
    private static final float LAUNCH_AFTER = 0.12f;

    private final List<Item> items;
    private final Smooth[] grow;
    private final Smooth[] label;
    private final Component[] names;
    private final float[] nameWidths;
    private final Spring press = new Spring(0.28f, 0.55f, 0.0f);
    private Language namedLanguage;
    private double namedScale;
    private int hovered = -1;
    private int launching = -1;
    private float launchClock;
    private long stamp = -1L;
    private float left;
    private float top;

    DesktopDock(List<Item> items) {
        this.items = items;
        this.grow = new Smooth[items.size()];
        this.label = new Smooth[items.size()];
        this.names = new Component[items.size()];
        this.nameWidths = new float[items.size()];
        for (int index = 0; index < items.size(); index++) {
            grow[index] = new Smooth(0.0f, GROW_SPEED);
            label[index] = new Smooth(0.0f, LABEL_SPEED);
            names[index] = Component.translatable(items.get(index).labelKey());
        }
    }

    float height() {
        return TILE + PAD * 2.0f;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= left && mouseX <= left + width() && mouseY >= top && mouseY <= top + height();
    }

    float right() {
        return left + width();
    }

    // WHY: Dock рисуется в два прохода. Стекло, разделитель, круг наведения и подписи ложатся на кадр
    // WHY: напрямую, а значки идут вторым проходом, который стол на засыпании кладёт в слой размытия:
    // WHY: полупрозрачная заливка поверх стекла в офскрине заменила бы альфу стекла своей, и сквозь
    // WHY: Dock проступили бы обои
    void renderBody(GuiGraphics graphics, Font font, float screenWidth, float screenHeight, int mouseX, int mouseY,
                    float alpha) {
        float width = width();
        left = (screenWidth - width) / 2.0f;
        top = screenHeight - BOTTOM - height();
        advance(mouseX, mouseY);
        UiGlass.hush(graphics, left, top, width, height(), height() / 2.0f, alpha);
        UiGlass.window(graphics, left, top, width, height(), height() / 2.0f, alpha);
        float x = left + PAD;
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).divided()) x = divider(graphics, x, alpha);
            glow(graphics, index, x, alpha);
            x += size(index) + GAP;
        }
        labels(graphics, font, alpha);
    }

    void renderIcons(GuiGraphics graphics, float alpha) {
        float x = left + PAD;
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).divided()) x += DIVIDER;
            icon(graphics, index, x, alpha);
            x += size(index) + GAP;
        }
    }

    private float width() {
        float width = PAD * 2.0f - GAP;
        for (int index = 0; index < items.size(); index++) {
            width += size(index) + GAP + (items.get(index).divided() ? DIVIDER : 0.0f);
        }
        return width;
    }

    private float size(int index) {
        return TILE;
    }

    private float divider(GuiGraphics graphics, float x, float alpha) {
        float centre = x + DIVIDER / 2.0f - GAP / 2.0f;
        UiRender.panel(graphics, centre - 0.5f, top + PAD + 3.0f, 1.0f, TILE - 6.0f, 0.5f,
                UiTheme.alpha(UiAccent.text(), 0.22f * alpha));
        return x + DIVIDER;
    }

    private void glow(GuiGraphics graphics, int index, float x, float alpha) {
        float size = size(index);
        float lift = grow[index].get();
        UiRender.panel(graphics, x, top + PAD, size, size, size / 2.0f,
                UiTheme.alpha(UiTheme.WHITE, HOVER_GLOW * lift * alpha));
    }

    private void icon(GuiGraphics graphics, int index, float x, float alpha) {
        float size = size(index);
        float lift = grow[index].get();
        float scale = (1.0f + HOVER_SCALE * lift) * (index == launching ? 1.0f - press.get() * 0.08f : 1.0f);
        int ink = UiTheme.mix(UiAccent.text(), UiTheme.WHITE, lift * 0.35f);
        UiIcon.draw(graphics, items.get(index).glyph(), x + size / 2.0f, top + PAD + size / 2.0f,
                size * scale * 0.56f, UiTheme.alpha(ink, alpha));
    }

    private void labels(GuiGraphics graphics, Font font, float alpha) {
        float x = left + PAD;
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).divided()) x += DIVIDER;
            float size = size(index);
            float shown = UiAnim.easeOut(label[index].get()) * alpha;
            if (shown > 0.01f) label(graphics, font, index, x + size / 2.0f, top + PAD + TILE - size, shown);
            x += size + GAP;
        }
    }

    private void label(GuiGraphics graphics, Font font, int index, float centerX, float tileTop, float shown) {
        Component text = names[index];
        float width = nameWidth(font, index) + 12.0f;
        float height = 13.0f;
        float y = tileTop - LABEL_GAP - height;
        UiGlass.window(graphics, centerX - width / 2.0f, y, width, height, height / 2.0f, shown);
        UiRender.textCentered(graphics, font, text, centerX, y + 3.5f, LABEL_SCALE,
                UiTheme.alpha(UiAccent.text(), shown), false);
    }

    // WHY: ширина подписи зависит от языка и от начертания под масштаб интерфейса, и меряется только
    // WHY: на их смене: замер в кадре собирал бы строку заново, пока курсор стоит над значком
    private float nameWidth(Font font, int index) {
        Language language = Language.getInstance();
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        if (language != namedLanguage || guiScale != namedScale) {
            namedLanguage = language;
            namedScale = guiScale;
            for (int item = 0; item < names.length; item++) {
                nameWidths[item] = UiRender.width(font, names[item]) * LABEL_SCALE;
            }
        }
        return nameWidths[index];
    }

    private void advance(int mouseX, int mouseY) {
        long frame = UiFrame.frame();
        if (frame == stamp) return;
        stamp = frame;
        float delta = UiFrame.delta();
        boolean inside = mouseY >= top && mouseY <= top + height();
        int wasHovered = hovered;
        hovered = inside ? itemAt(mouseX) : -1;
        if (hovered >= 0 && hovered != wasHovered) UiSound.hover();
        for (int index = 0; index < items.size(); index++) {
            grow[index].to(index == hovered ? 1.0f : 0.0f, delta);
            label[index].to(index == hovered ? 1.0f : 0.0f, delta);
        }
        press.to(0.0f, delta);
        launch(delta);
    }

    private void launch(float delta) {
        if (launching < 0) return;
        float before = launchClock;
        launchClock += delta;
        if (before < LAUNCH_AFTER && launchClock >= LAUNCH_AFTER) items.get(launching).action().run();
        if (launchClock >= LAUNCH_SECONDS) launching = -1;
    }

    private int itemAt(double mouseX) {
        float x = left + PAD;
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).divided()) x += DIVIDER;
            float size = size(index);
            if (mouseX >= x - GAP / 2.0f && mouseX < x + size + GAP / 2.0f) return index;
            x += size + GAP;
        }
        return -1;
    }

    // WHY: запуск ждёт конца нажатия в кадре, и отложенный запуск ушедшего экрана сработал бы
    // WHY: при возврате на стол, спустя сколько угодно времени. Подсветка и подпись значка, с
    // WHY: которого ушли, гаснут сразу: иначе возврат начинался бы с их угасания
    void forget() {
        launching = -1;
        press.snap(0.0f);
        for (int index = 0; index < items.size(); index++) {
            grow[index].snap(0.0f);
            label[index].snap(0.0f);
        }
    }

    boolean click(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY) || launching >= 0) return contains(mouseX, mouseY);
        int index = itemAt(mouseX);
        if (index < 0) return true;
        launching = index;
        launchClock = 0.0f;
        press.snap(1.0f);
        UiSound.press();
        return true;
    }
}
