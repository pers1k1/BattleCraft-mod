package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SliderRow extends MenuRow {
    private static final float TRACK_HEIGHT = 3.4f;
    private static final float VALUE_WIDTH = 46.0f;
    private static final float VALUE_GAP = 6.0f;
    private static final float KNOB = 4.2f;
    private static final float FILL_SPEED = 22.0f;
    private static final float READOUT_SCALE = 0.9f;

    private final Supplier<Float> value;
    private final Consumer<Float> apply;
    private final float minimum;
    private final float maximum;
    private final float step;
    private final Smooth fill = new Smooth(0.0f, FILL_SPEED);

    private boolean settled;
    private int decimals = 2;
    private float scale = 1.0f;
    private String suffix = "";
    private boolean dragging;

    public SliderRow(int x, int y, int width, int height, Component label,
                     Supplier<Float> value, Consumer<Float> apply, float minimum, float maximum, float step) {
        super(x, y, width, height, label);
        this.value = value;
        this.apply = apply;
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
    }

    public SliderRow readout(int decimals, float scale, String suffix) {
        this.decimals = decimals;
        this.scale = scale;
        this.suffix = suffix;
        return this;
    }

    @Override
    protected void renderValue(GuiGraphics graphics, int mouseX, int mouseY, float focus) {
        float current = value.get();
        float ratio = ratioOf(current);
        if (!settled) {
            settled = true;
            fill.snap(ratio);
        }
        float shown = fill.to(ratio, UiFrame.delta());

        float trackX = trackLeft();
        float trackWidth = trackWidth();
        float trackY = getY() + (height - TRACK_HEIGHT) / 2.0f;

        UiGlass.sunken(graphics, trackX, trackY, trackWidth, TRACK_HEIGHT, TRACK_HEIGHT / 2.0f, 0.95f);
        UiGlass.progress(graphics, trackX, trackY, trackWidth, TRACK_HEIGHT, shown,
                valueTint(UiTheme.mix(UiAccent.dim(), UiAccent.color(), 0.35f + 0.65f * focus)), valueAlpha());

        float knobRadius = KNOB + focus * 0.8f + (dragging ? 0.7f : 0.0f);
        float knobX = trackX + trackWidth * UiAnim.clamp01(shown);
        UiGlass.inner(graphics, knobX - knobRadius, trackY + TRACK_HEIGHT / 2.0f - knobRadius,
                knobRadius * 2.0f, knobRadius * 2.0f, knobRadius, valueAlpha(), 0.55f + 0.45f * focus);

        UiRender.textRight(graphics, font(), readoutOf(current), getX() + width - PAD,
                UiRender.centerY(getY(), height, READOUT_SCALE), READOUT_SCALE,
                faded() ? UiAccent.textFaint() : UiAccent.text(), false);
    }

    private float readoutValue = Float.NaN;
    private String readoutText = "";

    private String readoutOf(float current) {
        if (current == readoutValue) return readoutText;

        readoutValue = current;
        readoutText = String.format(Locale.ROOT, "%." + decimals + "f", current * scale) + suffix;
        return readoutText;
    }

    private float ratioOf(float current) {
        if (maximum - minimum <= 1.0E-5f) return 0.0f;
        return UiAnim.clamp01((current - minimum) / (maximum - minimum));
    }

    private float trackLeft() {
        return valueLeft();
    }

    private float trackWidth() {
        return Math.max(8.0f, getX() + width - PAD - VALUE_WIDTH - VALUE_GAP - trackLeft());
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
        if (mouseX < trackLeft() - KNOB || mouseX > trackLeft() + trackWidth() + KNOB) return;

        dragging = true;
        UiSound.press();
        seek(mouseX);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (dragging) seek(mouseX);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        dragging = false;
        super.onRelease(mouseX, mouseY);
    }

    private void seek(double mouseX) {
        float ratio = UiAnim.clamp01((float) ((mouseX - trackLeft()) / trackWidth()));
        float raw = minimum + (maximum - minimum) * ratio;
        apply.accept(quantize(raw));
    }

    private float quantize(float raw) {
        if (step <= 0.0f) return raw;

        float steps = Math.round((raw - minimum) / step);
        return Math.max(minimum, Math.min(maximum, minimum + steps * step));
    }
}
