package com.persiki84.minimap.client;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiField;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.zones.mark.MapMark;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

// WHY: экран карты не строится на ManagerScreen, и поле ввода из меню туда не встаёт: строка
// WHY: набирается своим маленьким окном, которое живёт только пока его ждут
public final class MapTextPrompt {
    private static final int WIDTH = 200;
    private static final int FIELD_HEIGHT = 18;
    private static final float TITLE_HEIGHT = 13.0f;
    private static final float PAD = 6.0f;
    private static final float TITLE_SCALE = 0.8f;
    private static final float OPEN_SPEED = 15.0f;
    private static final float RISE = 7.0f;
    private static final float BORN_SCALE = 0.94f;
    private static final float GONE = 0.004f;
    private static final float RIM = 1.0f;
    private static final float RIM_ALPHA = 0.6f;
    private static final float FOCUS_ALPHA = 0.22f;

    private final EditBox box;
    private final Smooth presence = new Smooth(0.0f, OPEN_SPEED);
    private Component title = Component.empty();
    private Consumer<String> commit;
    private boolean open;
    private float left;
    private float top;

    public MapTextPrompt() {
        box = new EditBox(Minecraft.getInstance().font, 0, 0, 1, FIELD_HEIGHT, Component.empty());
        box.setBordered(false);
        box.setMaxLength(MapMark.LINE_LIMIT);
        UiField.own(box);
    }

    public boolean open() {
        return open;
    }

    public void ask(Component prompt, String initial, Consumer<String> apply) {
        title = prompt;
        box.setValue(initial == null ? "" : initial);
        box.setFocused(true);
        commit = apply;
        open = true;
        UiSound.press();
    }

    // WHY: набранное остаётся в поле до конца ухода: окно гаснет с тем текстом, который в нём
    // WHY: был, а пустая строка посреди затухания читается как сброс ввода
    public void close() {
        open = false;
        commit = null;
        box.setFocused(false);
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        float shown = presence.to(open ? 1.0f : 0.0f, UiFrame.delta());
        if (!open && shown <= GONE) return;

        float eased = UiAnim.easeOut(shown);
        float total = TITLE_HEIGHT + FIELD_HEIGHT + PAD * 2.0f;
        left = Math.round((screenWidth - WIDTH) / 2.0f);
        top = Math.round(screenHeight / 2.0f - total / 2.0f);
        float centerX = left + WIDTH / 2.0f;
        float bottom = top + total;

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, bottom + (1.0f - eased) * RISE, 0.0f);
        graphics.pose().scale(scale(eased), scale(eased), 1.0f);
        graphics.pose().translate(-centerX, -bottom, 0.0f);
        try {
            paint(graphics, font, total, shown);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static float scale(float eased) {
        return BORN_SCALE + (1.0f - BORN_SCALE) * eased;
    }

    private void paint(GuiGraphics graphics, Font font, float total, float shown) {
        UiGlass.panel(graphics, left, top, WIDTH, total, UiMetrics.radius(FIELD_HEIGHT), shown);

        float titleScale = UiRender.crisp(graphics, TITLE_SCALE);
        UiRender.labelCentered(graphics, font, title, left + WIDTH / 2.0f,
                UiRender.centerY(top + PAD, TITLE_HEIGHT, titleScale), titleScale,
                UiTheme.alpha(UiAccent.textDim(), shown));

        float wellX = left + PAD;
        float wellY = top + PAD + TITLE_HEIGHT;
        float wellWidth = WIDTH - PAD * 2.0f;
        paintWell(graphics, wellX, wellY, wellWidth, shown);

        box.setX(Math.round(wellX + UiField.PAD_X));
        box.setY(Math.round(wellY));
        box.setWidth(Math.round(wellWidth - UiField.PAD_X * 2.0f));
        UiField.fade(box, shown);
        box.render(graphics, -1, -1, 0.0f);
    }

    private void paintWell(GuiGraphics graphics, float x, float y, float width, float shown) {
        float focus = UiField.focus(box);
        float radius = UiMetrics.radius(FIELD_HEIGHT);

        UiGlass.sunken(graphics, x, y, width, FIELD_HEIGHT, radius, shown);
        if (focus <= 0.01f) return;

        UiGlass.inner(graphics, x, y, width, FIELD_HEIGHT, radius, FOCUS_ALPHA * focus * shown, 0.4f);
        UiRender.rim(graphics, x, y, width, FIELD_HEIGHT, radius, RIM,
                UiTheme.alpha(UiAccent.color(), RIM_ALPHA * focus * shown));
    }

    // WHY: щелчок мимо окна снимает его, как и меню действий: иначе единственный выход это
    // WHY: Escape, а мышь на карте в это время молча ничего не делает
    public boolean click(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (box.mouseClicked(mouseX, mouseY, button)) return true;

        boolean onPanel = mouseX >= left && mouseX <= left + WIDTH
                && mouseY >= top && mouseY <= top + TITLE_HEIGHT + FIELD_HEIGHT + PAD * 2.0f;
        if (!onPanel) close();
        return true;
    }

    public boolean charTyped(char symbol, int modifiers) {
        if (!open) return false;

        box.charTyped(symbol, modifiers);
        return true;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!open) return false;

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        box.keyPressed(key, scan, modifiers);
        return true;
    }

    private void submit() {
        String value = box.getValue().trim();
        Consumer<String> apply = commit;
        close();
        if (!value.isEmpty() && apply != null) apply.accept(value);
    }
}
