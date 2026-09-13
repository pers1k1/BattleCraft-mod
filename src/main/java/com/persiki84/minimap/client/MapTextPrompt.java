package com.persiki84.minimap.client;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.zones.mark.MapMark;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

// WHY: экран карты не строится на ManagerScreen, и поле ввода из меню туда не встаёт: строка
// WHY: набирается своим маленьким окном, которое живёт только пока его ждут
public final class MapTextPrompt {
    private static final float WIDTH = 200.0f;
    private static final float HEIGHT = 20.0f;
    private static final float TITLE_HEIGHT = 13.0f;
    private static final float PAD = 6.0f;
    private static final float TITLE_SCALE = 0.8f;
    private static final float TEXT_SCALE = 1.0f;
    private static final float CARET_WIDTH = 1.2f;
    private static final float CARET_GAP = 1.0f;
    private static final long BLINK_MS = 1060L;
    private static final float OPEN_SPEED = 15.0f;
    private static final float OPEN_RISE = 7.0f;

    private final Smooth entrance = new Smooth(0.0f, OPEN_SPEED);
    private Component title;
    private String text = "";
    private Component shownText = Component.empty();
    private Consumer<String> commit;
    private boolean open;
    private long typedAt;

    public boolean open() {
        return open;
    }

    public void ask(Component prompt, String initial, Consumer<String> apply) {
        title = prompt;
        setText(initial == null ? "" : initial);
        commit = apply;
        open = true;
        entrance.snap(0.0f);
        typedAt = System.currentTimeMillis();
        UiSound.press();
    }

    public void close() {
        open = false;
        commit = null;
        setText("");
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        if (!open) return;

        float shown = entrance.to(1.0f, UiFrame.delta());
        float total = TITLE_HEIGHT + HEIGHT + PAD * 2.0f;
        float x = (screenWidth - WIDTH) / 2.0f;
        float y = screenHeight / 2.0f - total / 2.0f + (1.0f - shown) * OPEN_RISE;
        UiGlass.panel(graphics, x, y, WIDTH, total, UiMetrics.radius(HEIGHT), shown);

        float titleScale = UiRender.crisp(graphics, TITLE_SCALE);
        UiRender.labelCentered(graphics, font, title, x + WIDTH / 2.0f,
                UiRender.centerY(y + PAD, TITLE_HEIGHT, titleScale), titleScale,
                UiTheme.alpha(UiAccent.textDim(), shown));

        float boxY = y + PAD + TITLE_HEIGHT;
        float scale = UiRender.crisp(graphics, TEXT_SCALE);
        float textLeft = x + PAD;
        UiRender.textScaled(graphics, font, shownText, textLeft,
                UiRender.centerY(boxY, HEIGHT, scale), scale, UiTheme.alpha(UiAccent.text(), shown), false);
        renderCaret(graphics, font, textLeft, boxY, scale);
    }

    private void renderCaret(GuiGraphics graphics, Font font, float textLeft, float boxY, float scale) {
        if (!blinking()) return;

        float caretX = textLeft + UiRender.width(font, text) * scale + CARET_GAP;
        float height = font.lineHeight * scale;
        UiRender.rect(graphics, caretX, UiRender.centerY(boxY, HEIGHT, scale), CARET_WIDTH, height,
                UiAccent.color());
    }

    // WHY: строка рисуется каждый кадр, а Component.literal это аллокация: готовый текст
    // WHY: собирается на вводе, а не в отрисовке
    private void setText(String value) {
        text = value;
        shownText = Component.literal(value);
    }

    private boolean blinking() {
        long since = System.currentTimeMillis() - typedAt;
        return since < BLINK_MS / 2 || (since / (BLINK_MS / 2)) % 2 == 0;
    }

    public boolean charTyped(char symbol) {
        if (!open || text.length() >= MapMark.LINE_LIMIT) return open;

        setText(text + symbol);
        typedAt = System.currentTimeMillis();
        return true;
    }

    public boolean keyPressed(int key) {
        if (!open) return false;

        typedAt = System.currentTimeMillis();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!text.isEmpty()) setText(text.substring(0, text.length() - 1));
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            String value = text.trim();
            Consumer<String> apply = commit;
            close();
            if (!value.isEmpty() && apply != null) apply.accept(value);
            return true;
        }
        return true;
    }
}
