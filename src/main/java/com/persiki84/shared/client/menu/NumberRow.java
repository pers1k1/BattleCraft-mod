package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiInput;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class NumberRow extends MenuRow {
    private static final float STEPPER = 15.0f;
    private static final float VALUE_WIDTH = 54.0f;
    private static final float SIGN = 6.0f;
    private static final float SIGN_THICKNESS = 1.6f;
    private static final float ROLL_LIFT = 5.0f;
    private static final float ROLL_SPEED = 19.0f;
    private static final float PRESS_SPEED = 26.0f;
    private static final long FIRST_REPEAT_MS = 340L;
    private static final long FAST_REPEAT_MS = 28L;
    private static final long SLOW_REPEAT_MS = 210L;
    private static final long RAMP_MS = 2200L;
    private static final long BOOST_AFTER_MS = 3200L;
    private static final int BOOST_FACTOR = 8;
    private static final int MAX_DIGITS = 9;

    private final IntSupplier value;
    private final IntConsumer apply;
    private final int minimum;
    private final int maximum;
    private final int step;

    private final Pending pending = new Pending();
    private final Smooth roll = new Smooth(1.0f, ROLL_SPEED);
    private final Smooth minusPress = new Smooth(0.0f, PRESS_SPEED);
    private final Smooth plusPress = new Smooth(0.0f, PRESS_SPEED);

    private int shown = Integer.MIN_VALUE;
    private int previous = Integer.MIN_VALUE;
    private int direction;
    private int holding;
    private long holdStart;
    private long lastRepeat;
    private boolean typing;
    private String typed = "";
    private Component floorLabel;
    private int divisor = 1;
    private int decimals;

    public NumberRow(int x, int y, int width, int height, Component label,
                     IntSupplier value, IntConsumer apply, int minimum, int maximum, int step) {
        super(x, y, width, height, label);
        this.value = value;
        this.apply = apply;
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
    }

    public NumberRow floorLabel(Component label) {
        this.floorLabel = label;
        return this;
    }

    public NumberRow scaledBy(int divisor) {
        this.divisor = Math.max(1, divisor);
        this.decimals = String.valueOf(this.divisor).length() - 1;
        return this;
    }

    private int displayValue = Integer.MIN_VALUE;
    private String displayText;

    private String display(int raw) {
        if (raw == displayValue && displayText != null) return displayText;

        displayValue = raw;
        displayText = divisor == 1
                ? String.valueOf(raw)
                : String.format(Locale.ROOT, "%." + decimals + "f", raw / (float) divisor);
        return displayText;
    }

    @Override
    protected void renderValue(GuiGraphics graphics, int mouseX, int mouseY, float focus) {
        advanceHold();
        trackValue();

        float top = getY() + (height - STEPPER) / 2.0f;
        float delta = UiFrame.delta();
        stepper(graphics, minusLeft(), top, false, hovering(mouseX, mouseY, minusLeft(), top),
                minusPress.to(holding < 0 ? 1.0f : 0.0f, delta));
        stepper(graphics, plusLeft(), top, true, hovering(mouseX, mouseY, plusLeft(), top),
                plusPress.to(holding > 0 ? 1.0f : 0.0f, delta));

        if (typing) {
            renderTyping(graphics);
            return;
        }
        renderRolling(graphics, delta);
    }

    private int currentValue() {
        return pending.resolve(value.getAsInt());
    }

    private void trackValue() {
        int current = currentValue();
        if (shown == Integer.MIN_VALUE) {
            shown = current;
            previous = current;
            return;
        }
        if (current == shown) return;

        direction = current > shown ? 1 : -1;
        previous = shown;
        shown = current;
        roll.snap(0.0f);
    }

    private void renderRolling(GuiGraphics graphics, float delta) {
        float progress = UiAnim.easeOut(roll.to(1.0f, delta));
        float centerX = valueCenter();
        int tint = this.active ? UiAccent.text() : UiAccent.textFaint();

        if (floorLabel != null && shown == minimum) {
            UiRender.textTrackedFit(graphics, font(), floorLabel, centerX, getY(), height,
                    VALUE_WIDTH, LABEL_SCALE, 0.0f, tint, false);
            return;
        }

        if (progress < 0.999f && previous != shown) {
            float outLift = -direction * ROLL_LIFT * progress;
            UiRender.textCentered(graphics, font(), display(previous), centerX,
                    UiRender.centerY(getY(), height, LABEL_SCALE) + outLift, LABEL_SCALE,
                    UiTheme.alpha(tint, 1.0f - progress), false);
        }

        float inLift = direction * ROLL_LIFT * (1.0f - progress);
        UiRender.textCentered(graphics, font(), display(shown), centerX,
                UiRender.centerY(getY(), height, LABEL_SCALE) + inLift, LABEL_SCALE,
                UiTheme.alpha(tint, progress), false);
    }

    private void renderTyping(GuiGraphics graphics) {
        float centerX = valueCenter();
        String caret = (System.currentTimeMillis() / 500L) % 2L == 0L ? "_" : "";
        UiRender.textCentered(graphics, font(), typed + caret, centerX,
                UiRender.centerY(getY(), height, LABEL_SCALE), LABEL_SCALE, UiTheme.WHITE, false);
    }

    private float valueCenter() {
        return (minusLeft() + STEPPER + plusLeft()) / 2.0f;
    }

    private void advanceHold() {
        if (holding == 0) return;
        if (!UiInput.mouseDown()) {
            holding = 0;
            return;
        }

        long now = System.currentTimeMillis();
        long held = now - holdStart;
        if (held < FIRST_REPEAT_MS || now - lastRepeat < repeatInterval(held)) return;

        lastRepeat = now;
        push(holding * step * (held > BOOST_AFTER_MS ? BOOST_FACTOR : 1), false);
    }

    private static long repeatInterval(long held) {
        float ramp = Mth.clamp((held - FIRST_REPEAT_MS) / (float) RAMP_MS, 0.0f, 1.0f);
        return (long) (SLOW_REPEAT_MS + (FAST_REPEAT_MS - SLOW_REPEAT_MS) * UiAnim.easeOut(ramp));
    }

    private boolean hovering(int mouseX, int mouseY, float x, float y) {
        return mouseX >= x && mouseX <= x + STEPPER && mouseY >= y && mouseY <= y + STEPPER;
    }

    private void stepper(GuiGraphics graphics, float x, float y, boolean plus, boolean hovered, float pressed) {
        float shrink = pressed * 1.4f;
        float size = STEPPER - shrink;
        float left = x + shrink / 2.0f;
        float top = y + shrink / 2.0f;

        UiGlass.panel(graphics, left, top, size, size, size * 0.32f, 1.0f,
                (hovered ? 0.7f : 0.2f) + pressed * 0.5f);

        float centerX = left + size / 2.0f;
        float centerY = top + size / 2.0f;
        int tint = this.active ? UiAccent.text() : UiAccent.textFaint();

        UiRender.panel(graphics, centerX - SIGN / 2.0f, centerY - SIGN_THICKNESS / 2.0f,
                SIGN, SIGN_THICKNESS, SIGN_THICKNESS / 2.0f, tint);
        if (plus) {
            UiRender.panel(graphics, centerX - SIGN_THICKNESS / 2.0f, centerY - SIGN / 2.0f,
                    SIGN_THICKNESS, SIGN, SIGN_THICKNESS / 2.0f, tint);
        }
    }

    private float plusLeft() {
        return getX() + width - PAD - STEPPER;
    }

    private float minusLeft() {
        return plusLeft() - VALUE_WIDTH - STEPPER;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
        float top = getY() + (height - STEPPER) / 2.0f;
        if (mouseY < top || mouseY > top + STEPPER) return;

        if (mouseX >= plusLeft() && mouseX <= plusLeft() + STEPPER) {
            beginHold(1);
        } else if (mouseX >= minusLeft() && mouseX <= minusLeft() + STEPPER) {
            beginHold(-1);
        } else if (mouseX >= minusLeft() + STEPPER && mouseX <= plusLeft()) {
            beginTyping();
        }
    }

    private void beginHold(int sign) {
        stopTyping();
        holding = sign;
        holdStart = System.currentTimeMillis();
        lastRepeat = holdStart;
        push(sign * step, true);
    }

    @Override
    public boolean capturing() {
        return typing;
    }

    private void beginTyping() {
        UiSound.press();
        typing = true;
        typed = "";
        setFocused(true);
    }

    private void stopTyping() {
        typing = false;
        typed = "";
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        holding = 0;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char symbol, int modifiers) {
        if (!typing) return false;
        if (symbol == '-' && typed.isEmpty() && minimum < 0) {
            typed = "-";
            return true;
        }
        // WHY: цифровой блок на русской раскладке даёт запятую, и дробное значение молча
        // WHY: превращалось в целое: 0,5 набиралось как 05
        if ((symbol == '.' || symbol == ',') && divisor > 1 && !typed.contains(".")) {
            typed += '.';
            return true;
        }
        if (symbol < '0' || symbol > '9' || digits(typed) >= MAX_DIGITS) return false;

        typed += symbol;
        return true;
    }

    private static int digits(String entered) {
        int count = 0;
        for (int index = 0; index < entered.length(); index++) {
            if (Character.isDigit(entered.charAt(index))) count++;
        }
        return count;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!typing) return super.keyPressed(key, scanCode, modifiers);

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            stopTyping();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!typed.isEmpty()) typed = typed.substring(0, typed.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            commitTyped();
            return true;
        }
        return true;
    }

    private void commitTyped() {
        String entered = typed;
        stopTyping();
        if (entered.isEmpty() || entered.equals("-")) return;

        try {
            push(parse(entered) - currentValue(), true);
        } catch (NumberFormatException ignored) {
            UiSound.chip(false);
        }
    }

    private int parse(String entered) {
        if (divisor == 1) return Integer.parseInt(entered);
        return (int) Math.round(Double.parseDouble(entered) * divisor);
    }

    private void push(int delta, boolean audible) {
        int current = currentValue();
        int next = Math.max(minimum, Math.min(maximum, current + delta));
        if (next == current) return;

        pending.want(next);
        if (audible) {
            UiSound.press();
            flash();
        }
        apply.accept(next);
    }
}
