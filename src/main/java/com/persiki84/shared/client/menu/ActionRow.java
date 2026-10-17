package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiSwap;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public class ActionRow extends MenuRow {
    private static final float VALUE_WIDTH = 132.0f;
    private static final int MIN_ALPHA = 4;

    private final Supplier<Component> value;
    private final Runnable action;
    private final UiSwap swap = new UiSwap();
    private TickerLine line = new TickerLine(1.0f);
    private Component steadyValue;
    private Component countedValue;
    private String lastText;
    private boolean counting;
    private boolean alerting;

    public ActionRow(int x, int y, int width, int height, Component label,
                     Supplier<Component> value, Runnable action) {
        super(x, y, width, height, label);
        this.value = value;
        this.action = action;
    }

    public ActionRow alerting() {
        this.alerting = true;
        return this;
    }

    @Override
    protected void renderValue(GuiGraphics graphics, int mouseX, int mouseY, float focus) {
        Component shown = steady(value.get());
        int color = valueColor(focus);
        if (counting(shown)) {
            swap.settle(shown);
            if ((color >>> 24) >= MIN_ALPHA) line.box(graphics, font(), shown, getX() + width - PAD - VALUE_WIDTH,
                    getY(), height, VALUE_WIDTH, LABEL_SCALE, color);
            return;
        }
        line.follow(shown);
        float phase = swap.advance(shown, UiFrame.delta());
        Component leaving = swap.outgoing();
        if (leaving != null) paintValue(graphics, leaving, -phase * UiSwap.LIFT, UiTheme.alpha(color, 1.0f - phase));
        paintValue(graphics, shown, (1.0f - phase) * UiSwap.LIFT, UiTheme.alpha(color, phase));
    }

    // WHY: источник значения собирает компонент заново на каждый кадр; равный прошлому заменяется
    // WHY: прошлой ссылкой, и дальше тикер, смена и проверка цифр узнают его по ссылке, не разворачивая
    // WHY: в строку каждый кадр
    private Component steady(Component next) {
        if (steadyValue != null && (next == steadyValue || next.equals(steadyValue))) return steadyValue;
        steadyValue = next;
        return next;
    }

    // WHY: смена числа на число идёт по цифрам, как таймер острова; смена слова, где цифр нет хотя бы
    // WHY: с одной стороны, по-прежнему уезжает целиком
    private boolean counting(Component shown) {
        if (shown == countedValue) return counting;
        countedValue = shown;
        String text = shown.getString();
        if (!text.equals(lastText)) {
            counting = lastText != null && hasDigit(lastText) && hasDigit(text);
            lastText = text;
        }
        return counting;
    }

    private static boolean hasDigit(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (Character.isDigit(text.charAt(index))) return true;
        }
        return false;
    }

    private void paintValue(GuiGraphics graphics, Component text, float shift, int color) {
        if ((color >>> 24) < MIN_ALPHA || blank(text)) return;

        UiRestFrame.shift(graphics, 0.0f, shift);
        try {
            UiRender.textTrackedBox(graphics, font(), text, getX() + width - PAD - VALUE_WIDTH, getY(), height,
                    VALUE_WIDTH, LABEL_SCALE, 0.0f, color, false, 1.0f);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private boolean blank(Component text) {
        return text == countedValue ? lastText.isEmpty() : text.getString().isEmpty();
    }

    @Override
    public void adopt(MenuRow previous) {
        super.adopt(previous);
        if (!(previous instanceof ActionRow older)) return;
        swap.take(older.swap);
        line = older.line;
        steadyValue = older.steadyValue;
        countedValue = older.countedValue;
        lastText = older.lastText;
        counting = older.counting;
    }

    private int valueColor(float focus) {
        if (blocked()) return UiAccent.textFaint();
        if (alerting) return UiTheme.mix(UiPalette.alert(), UiTheme.WHITE, focus * 0.15f);
        if (!this.active) return UiAccent.textFaint();
        return UiTheme.mix(UiAccent.text(), UiTheme.WHITE, focus);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
        UiSound.press();
        flash();
        action.run();
    }
}
