package com.persiki84.battlecraft.client.hud;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public final class VitalVeil {
    private static final float ONSET = 0.45f;
    private static final float EDGE_ALPHA = 0.50f;
    private static final float THROB_LIFT = 0.35f;
    private static final float RUSH_FLOOR = 0.02f;
    private static final float RUSH_ALPHA = 0.34f;
    private static final float RUSH_REST = 0.30f;
    private static final float RUSH_THROB_LIFT = 1.05f;
    private static final float SIDE_BAND = 0.30f;
    private static final float TOP_BAND = 0.24f;
    private static final float THROB_FALL = 4.2f;

    private static final Smooth throb = new Smooth(0.0f, THROB_FALL);
    private static long lastBeat = Long.MIN_VALUE;

    private VitalVeil() {}

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) return;

        float veil = closing();
        if (veil <= 0.004f) return;

        draw(graphics, screenWidth, screenHeight, UiTheme.withAlpha(0x000000, veil));
    };

    private static float closing() {
        float fatigue = fatigueDepth();
        float rush = rushDepth();
        beat(fatigue + rush);
        if (fatigue + rush <= 0.0f) return 0.0f;

        float pulse = throb.get();
        float veil = Math.max(fatigue * EDGE_ALPHA * (1.0f + THROB_LIFT * pulse),
                rush * RUSH_ALPHA * (RUSH_REST + RUSH_THROB_LIFT * pulse));
        return UiAnim.clamp01(veil) * UiVital.vigor();
    }

    private static float fatigueDepth() {
        float fatigue = UiVital.exertion();
        if (fatigue <= ONSET) return 0.0f;
        return (fatigue - ONSET) / (1.0f - ONSET);
    }

    private static float rushDepth() {
        float rush = UiVital.rush();
        return rush < RUSH_FLOOR ? 0.0f : rush;
    }

    private static void beat(float depth) {
        long beat = UiVital.beatIndex();
        if (depth > 0.0f && lastBeat != Long.MIN_VALUE && beat > lastBeat) {
            throb.snap(1.0f);
        }
        lastBeat = beat;
        throb.to(0.0f, UiFrame.delta());
    }

    private static void draw(GuiGraphics graphics, int screenWidth, int screenHeight, int edge) {
        int clear = UiTheme.withAlpha(0x000000, 0.0f);
        float sides = screenWidth * SIDE_BAND;
        float caps = screenHeight * TOP_BAND;

        UiRender.gradientAcross(graphics, 0.0f, 0.0f, sides, screenHeight, edge, clear);
        UiRender.gradientAcross(graphics, screenWidth - sides, 0.0f, sides, screenHeight, clear, edge);
        UiRender.gradient(graphics, 0.0f, 0.0f, screenWidth, caps, edge, clear);
        UiRender.gradient(graphics, 0.0f, screenHeight - caps, screenWidth, caps, clear, edge);
    }
}
