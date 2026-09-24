package com.persiki84.minimap.client;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// WHY: на колесе мыши раньше висел один телепорт, а теперь действий несколько: окно выбора живёт
// WHY: прямо на карте, потому что все они привязаны к точке, по которой щёлкнули
public final class MapActionMenu {
    private static final float ROW_HEIGHT = 15.0f;
    private static final float ROW_GAP = 1.5f;
    private static final float PAD = 4.0f;
    private static final float MIN_WIDTH = 96.0f;
    private static final float LABEL_SCALE = 0.85f;
    private static final float SWATCH = 9.0f;
    private static final float EDGE = 4.0f;
    private static final float OPEN_SPEED = 16.0f;
    private static final float OPEN_RISE = 6.0f;
    private static final float ROW_STAGGER = 0.11f;
    private static final float SHOWN = 0.995f;
    private static final float GONE = 0.004f;

    public record Item(Component label, int swatch, Runnable run) {
        public static Item of(String key, Runnable run) {
            return new Item(Component.translatable(key), 0, run);
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final Smooth presence = new Smooth(0.0f, OPEN_SPEED);
    private float x;
    private float y;
    private float width;
    private boolean open;

    public boolean open() {
        return open;
    }

    // WHY: строки остаются до конца ухода: окно сворачивается той же лесенкой в обратную
    // WHY: сторону, и без строк от него осталось бы пустое стекло
    public void close() {
        open = false;
    }

    public void show(Font font, double pointerX, double pointerY, int screenWidth, int screenHeight,
                     List<Item> shown) {
        items.clear();
        items.addAll(shown);
        if (items.isEmpty()) return;

        width = Math.max(MIN_WIDTH, widest(font) + PAD * 2.0f + SWATCH);
        x = (float) Math.min(pointerX, screenWidth - width - EDGE);
        y = (float) Math.min(pointerY, screenHeight - height() - EDGE);
        open = true;
        presence.snap(0.0f);
        UiSound.press();
    }

    private float widest(Font font) {
        float widest = 0.0f;
        for (Item item : items) {
            widest = Math.max(widest, UiRender.width(font, item.label()) * LABEL_SCALE);
        }
        return widest;
    }

    public float height() {
        return items.size() * (ROW_HEIGHT + ROW_GAP) - ROW_GAP + PAD * 2.0f;
    }

    // WHY: строки въезжают лесенкой от своего номера, а не одним куском: окно открывается на
    // WHY: щелчок посреди карты, и мгновенная плашка читается как скачок кадра
    public void render(GuiGraphics graphics, Font font, double mouseX, double mouseY) {
        float shown = presence.to(open ? 1.0f : 0.0f, UiFrame.delta());
        if (!open && shown <= GONE) {
            items.clear();
            return;
        }
        if (items.isEmpty()) return;

        float total = height();
        UiGlass.panel(graphics, x, y + (1.0f - shown) * OPEN_RISE, width, total,
                UiMetrics.radius(ROW_HEIGHT), shown);

        float rowY = y + PAD;
        int index = 0;
        for (Item item : items) {
            float step = UiAnim.clamp01((shown - index * ROW_STAGGER) / (1.0f - ROW_STAGGER));
            renderRow(graphics, font, item, rowY + (1.0f - step) * OPEN_RISE, step,
                    open && shown > SHOWN && inside(mouseX, mouseY, rowY));
            rowY += ROW_HEIGHT + ROW_GAP;
            index++;
        }
    }

    private void renderRow(GuiGraphics graphics, Font font, Item item, float rowY, float step, boolean hovered) {
        if (step <= 0.01f) return;

        if (hovered) {
            UiRender.panel(graphics, x + PAD / 2.0f, rowY, width - PAD, ROW_HEIGHT,
                    UiMetrics.radius(ROW_HEIGHT), UiTheme.withAlpha(UiAccent.color(), 0.22f * step));
        }

        float textLeft = x + PAD;
        if (item.swatch() != 0) {
            UiRender.dot(graphics, textLeft + SWATCH / 2.0f, rowY + ROW_HEIGHT / 2.0f,
                    SWATCH / 2.0f, UiTheme.alpha(item.swatch(), step));
            textLeft += SWATCH + PAD;
        }

        float scale = UiRender.crisp(graphics, LABEL_SCALE);
        UiRender.labelScaled(graphics, font, item.label(), textLeft,
                UiRender.centerY(rowY, ROW_HEIGHT, scale), scale,
                UiTheme.alpha(hovered ? UiAccent.color() : UiAccent.text(), step));
    }

    private boolean inside(double mouseX, double mouseY, float rowY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
    }

    public boolean click(double mouseX, double mouseY) {
        if (!open) return false;

        float rowY = y + PAD;
        for (Item item : items) {
            if (inside(mouseX, mouseY, rowY)) {
                close();
                UiSound.press();
                item.run().run();
                return true;
            }
            rowY += ROW_HEIGHT + ROW_GAP;
        }

        boolean onPanel = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height();
        close();
        return onPanel;
    }
}
