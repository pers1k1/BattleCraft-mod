package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.goggles.GogglesClient;
import com.persiki84.battlecraft.compat.goggles.Goggles;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Toggle;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public final class GogglesHud {
    private static final float WIDTH = 84.0f;
    private static final float HEIGHT = 30.0f;
    private static final float PADDING = 8.0f;
    private static final float TRACK_HEIGHT = 2.5f;
    private static final float CHARGE_SCALE = 1.9f;
    private static final float UNIT_SCALE = 0.95f;
    private static final float MODE_SCALE = 0.7f;
    private static final float COUNTER_DROP = 2.5f;
    private static final float UNIT_GAP = 4.0f;
    private static final float MODE_RISE = 3.0f;
    private static final float LOW_SHARE = 0.15f;
    private static final float SWAP_SPEED = 13.0f;
    private static final float SAMPLE_CHARGE = 0.62f;
    private static final int SAMPLE_MODE = 0;
    private static final int PERCENT = 100;
    private static final String UNIT = "%";

    private static final Toggle visibility = new Toggle(10.0f, 120L);
    private static final Smooth fill = new Smooth(14.0f);
    private static final Smooth previewFill = new Smooth(14.0f);
    private static final Smooth swap = new Smooth(1.0f, SWAP_SPEED);

    private static int shownMode = Goggles.MODE_NONE;
    private static int leavingMode = Goggles.MODE_NONE;

    private GogglesHud() {}

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) return;
        if (!Goggles.available() || !HudLayout.visible(HudSlot.GOGGLES)) return;

        float scale = UiScale.push(graphics);
        try {
            render(graphics, mc, screenWidth / scale, screenHeight / scale);
        } finally {
            UiScale.pop(graphics);
        }
    };

    private static void render(GuiGraphics graphics, Minecraft mc, float screenWidth, float screenHeight) {
        float delta = UiFrame.delta();
        float alpha = visibility.update(GogglesClient.visorOn(), delta);
        if (alpha <= 0.01f) {
            if (visibility.cleared()) forget();
            return;
        }

        want(GogglesClient.mode());
        float appear = UiAnim.easeOut(alpha);
        HudBox box = HudLayout.place(HudSlot.GOGGLES, WIDTH, HEIGHT, screenWidth, screenHeight);
        float x = box.x() + HudLayout.slideX(HudSlot.GOGGLES, (1.0f - appear) * 18.0f);
        float y = box.y();

        HudLayout.push(graphics, box);
        try {
            drawCard(graphics, mc, x, y, alpha * box.alpha(), delta, GogglesClient.battery(), fill);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    public static void preview(GuiGraphics graphics, HudBox box, float alpha) {
        int held = shownMode;
        if (shownMode == Goggles.MODE_NONE) shownMode = SAMPLE_MODE;
        try {
            HudLayout.sample(HudSlot.GOGGLES, WIDTH, HEIGHT);
            drawCard(graphics, Minecraft.getInstance(), box.x(), box.y(), alpha, UiFrame.delta(),
                    SAMPLE_CHARGE, previewFill);
        } finally {
            shownMode = held;
        }
    }

    private static void want(int mode) {
        if (mode == shownMode) return;

        leavingMode = shownMode;
        shownMode = mode;
        swap.snap(0.0f);
    }

    private static void forget() {
        fill.snap(0.0f);
        swap.snap(1.0f);
        shownMode = Goggles.MODE_NONE;
        leavingMode = Goggles.MODE_NONE;
    }

    private static void drawCard(GuiGraphics graphics, Minecraft mc, float x, float y, float alpha,
                                 float delta, float charge, Smooth track) {
        boolean low = charge <= LOW_SHARE;
        float pulse = low ? UiAnim.pulse(900.0f, 0.65f, 1.0f) : 1.0f;
        int base = HudLayout.tint(HudSlot.GOGGLES, UiAccent.color());
        int accent = low ? UiTheme.mix(base, UiPalette.alert(), 0.7f) : base;

        UiVital.card(graphics, x, y, WIDTH, HEIGHT, UiMetrics.radius(HEIGHT), alpha);
        drawCharge(graphics, mc, x, y, alpha, pulse, accent, charge);
        drawMode(graphics, mc, x, y, alpha, delta);

        float trackY = y + HEIGHT - PADDING + 1.0f;
        UiGlass.sunken(graphics, x + PADDING, trackY, WIDTH - PADDING * 2.0f, TRACK_HEIGHT,
                TRACK_HEIGHT / 2.0f, alpha);
        UiGlass.progress(graphics, x + PADDING, trackY, WIDTH - PADDING * 2.0f, TRACK_HEIGHT,
                track.to(UiAnim.clamp01(charge), delta), accent, alpha * pulse);
    }

    private static void drawCharge(GuiGraphics graphics, Minecraft mc, float x, float y, float alpha,
                                   float pulse, int accent, float charge) {
        String value = String.valueOf(Math.round(charge * PERCENT));
        float row = HEIGHT - PADDING - TRACK_HEIGHT;

        UiRender.labelScaled(graphics, mc.font, value, x + PADDING,
                UiRender.centerY(y + COUNTER_DROP, row, CHARGE_SCALE), CHARGE_SCALE,
                UiTheme.alpha(accent, alpha * pulse));

        float valueWidth = UiRender.widthLabel(mc.font, value) * CHARGE_SCALE;
        UiRender.labelScaled(graphics, mc.font, UNIT, x + PADDING + valueWidth + UNIT_GAP,
                UiRender.centerY(y + COUNTER_DROP, row, UNIT_SCALE), UNIT_SCALE,
                UiTheme.alpha(HudInk.textDim(), alpha));
    }

    private static void drawMode(GuiGraphics graphics, Minecraft mc, float x, float y, float alpha,
                                 float delta) {
        float phase = UiAnim.easeOut(swap.to(1.0f, delta));
        float right = x + WIDTH - PADDING;
        float top = y + PADDING * 0.6f;

        if (phase < 0.999f) {
            paintMode(graphics, mc, leavingMode, right, top - phase * MODE_RISE, alpha * (1.0f - phase));
        }
        paintMode(graphics, mc, shownMode, right, top + (1.0f - phase) * MODE_RISE, alpha * phase);
    }

    private static void paintMode(GuiGraphics graphics, Minecraft mc, int mode, float right, float top,
                                  float alpha) {
        String key = Goggles.modeKey(mode);
        if (key == null || alpha <= 0.01f) return;

        UiRender.textRight(graphics, mc.font, Component.translatable(key), right, top, MODE_SCALE,
                UiTheme.alpha(HudInk.textFaint(), alpha), false);
    }
}
