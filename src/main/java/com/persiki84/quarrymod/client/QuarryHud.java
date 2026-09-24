package com.persiki84.quarrymod.client;

import com.persiki84.battlecraft.client.ClientModules;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiHud;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiWorldTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public final class QuarryHud {
    private static final float MARKER_SCALE = 0.72f;
    private static final float MARKER_DOT = 1.5f;
    private static final float DOT_IDLE_ALPHA = 0.55f;
    private static final float MARKER_GONE = 0.01f;
    private static final float HOVER_NEAR = 12.0f;
    private static final float HOVER_FAR = 40.0f;
    private static final float DETAIL_ALWAYS = 1.0f;

    private QuarryHud() {}

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.player.isSpectator()) return;
        if (!ClientModules.allows(ModuleId.QUARRY) || UiHud.rosterOpen()) return;

        float scale = UiScale.push(graphics);
        try {
            renderMarkers(graphics, minecraft, scale);
        } finally {
            UiScale.pop(graphics);
        }
    };

    private static void renderMarkers(GuiGraphics graphics, Minecraft minecraft, float scale) {
        float delta = UiFrame.delta();
        float centerX = minecraft.getWindow().getGuiScaledWidth() / 2.0f;
        float centerY = minecraft.getWindow().getGuiScaledHeight() / 2.0f;
        float markerScale = UiRender.crisp(graphics, MARKER_SCALE * HudConfig.markerScale());
        float height = UiWorldTag.height(minecraft.font, markerScale);

        for (QuarryMarkers.Marker marker : QuarryMarkers.live()) {
            float presence = marker.presence(delta);
            if (presence <= MARKER_GONE) continue;

            float focus = marker.focus(aimedAt(marker, centerX, centerY, scale), delta);
            drawMarker(graphics, minecraft, marker, scale, markerScale, height, focus, presence);
        }
    }

    // WHY: вне прицела плашка сворачивается в точку и выходит из общего стека меток: иначе поле
    // WHY: выработок расталкивает соседние метки и закрывает собой всю шахту
    private static void drawMarker(GuiGraphics graphics, Minecraft minecraft, QuarryMarkers.Marker marker,
                                   float scale, float markerScale, float height, float focus, float presence) {
        float x = marker.screenX() / scale;
        float y = marker.screenY() / scale;
        float plate = focus * focus;

        drawDot(graphics, marker, x, y, focus, presence * (1.0f - plate));
        if (plate <= MARKER_GONE) return;

        float alpha = UiWorldTag.IDLE_ALPHA + (UiWorldTag.FOCUS_ALPHA - UiWorldTag.IDLE_ALPHA) * focus;
        UiWorldTag.render(graphics, minecraft.font, marker.label(), marker.detail(), DETAIL_ALWAYS,
                x, y - height, height, markerScale, marker.color(), alpha, focus,
                presence * plate, marker.stack());
    }

    private static void drawDot(GuiGraphics graphics, QuarryMarkers.Marker marker, float x, float y,
                                float focus, float shown) {
        if (shown <= MARKER_GONE) return;

        UiRender.dot(graphics, x, y, MARKER_DOT * HudConfig.markerScale(),
                UiTheme.withAlpha(marker.color(), (DOT_IDLE_ALPHA + (1.0f - DOT_IDLE_ALPHA) * focus) * shown));
    }

    private static float aimedAt(QuarryMarkers.Marker marker, float centerX, float centerY, float scale) {
        float dx = marker.screenX() - centerX;
        float dy = marker.screenY() - centerY;
        float away = (float) Math.sqrt(dx * dx + dy * dy) / scale;
        return 1.0f - UiAnim.clamp01((away - HOVER_NEAR) / (HOVER_FAR - HOVER_NEAR));
    }
}
