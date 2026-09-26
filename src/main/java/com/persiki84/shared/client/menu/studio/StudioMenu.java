package com.persiki84.shared.client.menu.studio;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

// WHY: действие над вещью берётся с самой вещи правой кнопкой: владелец ищет удаление и копию
// WHY: там, где смотрит, а не в колонке свойств. Меню раскрывается от точки щелчка, чтобы было
// WHY: видно, к чему оно относится, а необратимое спрашивает второе нажатие прямо в строке
public final class StudioMenu {
    private static final float ROW = 17.0f;
    private static final float PAD = 4.0f;
    private static final float TEXT_PAD = 9.0f;
    private static final float MIN_WIDTH = 118.0f;
    private static final float RADIUS = 7.0f;
    private static final float LABEL_SCALE = 0.8f;
    private static final float OPEN_SPEED = 16.0f;
    private static final float CLOSE_SPEED = 24.0f;
    private static final float PILL_SPEED = 24.0f;
    private static final float ROW_MS = 150.0f;
    private static final float STAGGER_MS = 22.0f;
    private static final float ROW_SLIDE = 7.0f;
    private static final float BORN_SCALE = 0.86f;
    private static final float DEPTH = 320.0f;
    private static final float EDGE = 4.0f;
    private static final long ARM_MS = 3000L;
    private static final Component SURE = Component.translatable("studio.menu.sure");

    public record Action(Component label, Runnable run, boolean danger, boolean confirm, boolean enabled) {
        public static Action of(String key, Runnable run) {
            return new Action(Component.translatable(key), run, false, false, true);
        }

        public static Action danger(String key, Runnable run) {
            return new Action(Component.translatable(key), run, true, false, true);
        }

        public static Action careful(String key, Runnable run) {
            return new Action(Component.translatable(key), run, true, true, true);
        }

        public Action when(boolean allowed) {
            return new Action(label, run, danger, confirm, allowed);
        }
    }

    private final Smooth shown = new Smooth(0.0f, OPEN_SPEED);
    private final Smooth pill = new Smooth(0.0f, PILL_SPEED);
    private final Smooth pillShown = new Smooth(0.0f, PILL_SPEED);
    private List<Action> actions = List.of();
    private boolean open;
    private long openedAt;
    private float left;
    private float top;
    private float width;
    private float originX;
    private float originY;
    private int hovered = -1;
    private int armed = -1;
    private long armedAt;

    public void show(List<Action> offered, double mouseX, double mouseY, float screenWidth, float screenHeight) {
        if (offered.isEmpty()) return;
        actions = offered;
        width = measure(offered);
        float height = height();
        boolean flipX = mouseX + width + EDGE > screenWidth;
        boolean flipY = mouseY + height + EDGE > screenHeight;
        left = (float) (flipX ? mouseX - width : mouseX);
        top = (float) (flipY ? mouseY - height : mouseY);
        left = Math.max(EDGE, left);
        top = Math.max(EDGE, top);
        originX = (float) mouseX;
        originY = (float) mouseY;
        open = true;
        openedAt = System.currentTimeMillis();
        hovered = -1;
        armed = -1;
        shown.snap(0.0f);
        pillShown.snap(0.0f);
        UiSound.press();
    }

    private static float measure(List<Action> offered) {
        Font font = Minecraft.getInstance().font;
        float widest = 0.0f;
        for (Action action : offered) {
            widest = Math.max(widest, UiRender.width(font, action.label()) * LABEL_SCALE);
        }
        widest = Math.max(widest, UiRender.width(font, SURE) * LABEL_SCALE);
        return Math.max(MIN_WIDTH, widest + TEXT_PAD * 2.0f + PAD * 2.0f);
    }

    private float height() {
        return actions.size() * ROW + PAD * 2.0f;
    }

    public void close() {
        open = false;
        armed = -1;
    }

    public boolean showing() {
        return open;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height();
    }

    private int rowAt(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) return -1;
        int row = (int) Math.floor((mouseY - top - PAD) / ROW);
        return row >= 0 && row < actions.size() ? row : -1;
    }

    public boolean click(double mouseX, double mouseY) {
        if (!open || !contains(mouseX, mouseY)) return false;
        int row = rowAt(mouseX, mouseY);
        if (row < 0) return true;
        Action action = actions.get(row);
        if (!action.enabled()) {
            UiSound.deny();
            return true;
        }
        if (action.confirm() && !confirmed(row)) return true;
        close();
        UiSound.confirm();
        action.run().run();
        return true;
    }

    private boolean confirmed(int row) {
        long now = System.currentTimeMillis();
        if (armed == row && now - armedAt < ARM_MS) return true;
        armed = row;
        armedAt = now;
        UiSound.alert();
        return false;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        float delta = UiFrame.delta();
        float amount = shown.to(open ? 1.0f : 0.0f, open ? OPEN_SPEED : CLOSE_SPEED, delta);
        if (!open && amount <= 0.01f) return;

        trackHover(mouseX, mouseY);
        float alpha = UiAnim.easeOut(amount);
        float scale = open ? BORN_SCALE + (1.0f - BORN_SCALE) * UiAnim.easeOutBack(amount)
                : BORN_SCALE + (1.0f - BORN_SCALE) * amount;
        graphics.pose().pushPose();
        graphics.pose().translate(originX, originY, DEPTH);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-originX, -originY, 0.0f);
        try {
            UiGlass.window(graphics, left, top, width, height(), RADIUS, alpha);
            UiGlass.layer(graphics);
            paintPill(graphics, alpha, delta);
            paintRows(graphics, alpha);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    private void trackHover(int mouseX, int mouseY) {
        int row = open ? rowAt(mouseX, mouseY) : -1;
        if (row >= 0 && !actions.get(row).enabled()) row = -1;
        if (row >= 0 && row != hovered) UiSound.hover();
        hovered = row;
    }

    private void paintPill(GuiGraphics graphics, float alpha, float delta) {
        float targetY = top + PAD + Math.max(0, hovered) * ROW;
        if (pillShown.get() <= 0.02f) pill.snap(targetY);
        float y = pill.to(targetY, delta);
        float lit = pillShown.to(hovered >= 0 ? 1.0f : 0.0f, delta) * alpha;
        if (lit <= 0.01f) return;
        boolean danger = hovered >= 0 && actions.get(hovered).danger();
        int tone = danger ? UiPalette.alert() : UiAccent.color();
        float x = left + PAD;
        float w = width - PAD * 2.0f;
        UiRender.panel(graphics, x, y, w, ROW, RADIUS - PAD, UiTheme.alpha(tone, 0.22f * lit));
        UiRender.rim(graphics, x, y, w, ROW, RADIUS - PAD, 1.0f, UiTheme.alpha(tone, 0.55f * lit));
    }

    private void paintRows(GuiGraphics graphics, float alpha) {
        Font font = Minecraft.getInstance().font;
        long age = System.currentTimeMillis() - openedAt;
        for (int row = 0; row < actions.size(); row++) {
            Action action = actions.get(row);
            float appear = UiAnim.easeOut((age - row * STAGGER_MS) / ROW_MS) * alpha;
            float y = top + PAD + row * ROW;
            if (row > 0 && action.danger() && !actions.get(row - 1).danger()) paintRule(graphics, y, appear);
            Component label = row == armed && System.currentTimeMillis() - armedAt < ARM_MS ? SURE : action.label();
            UiRender.textTrackedBox(graphics, font, label, left + PAD + TEXT_PAD + (1.0f - appear) * ROW_SLIDE, y,
                    ROW, width - PAD * 2.0f - TEXT_PAD * 2.0f, LABEL_SCALE, 0.0f,
                    UiTheme.alpha(color(action, row), appear), false, 0.0f);
        }
    }

    private void paintRule(GuiGraphics graphics, float y, float appear) {
        UiRender.rect(graphics, left + PAD + TEXT_PAD, y - 0.5f, width - (PAD + TEXT_PAD) * 2.0f, 1.0f,
                UiTheme.alpha(UiAccent.textFaint(), 0.35f * appear));
    }

    private int color(Action action, int row) {
        if (!action.enabled()) return UiAccent.textFaint();
        if (action.danger()) return UiPalette.alert();
        return row == hovered ? UiTheme.mix(UiAccent.text(), UiTheme.WHITE, 0.6f) : UiAccent.text();
    }
}
