package com.persiki84.zones.client;

import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.capturepoints.client.ClientCaptureData;
import com.persiki84.capturepoints.client.KeyBindings;
import com.persiki84.knockdown.cap.KnockdownCapability;
import com.persiki84.knockdown.cap.KnockdownProvider;
import com.persiki84.shared.client.ui.KeyLabel;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiHud;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import com.persiki84.shared.client.ui.UiWorldTag;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneType;
import com.persiki84.zones.client.render.ZoneMarkers;
import com.persiki84.battlecraft.client.ClientModules;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.battlecraft.modules.ModuleId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;
import java.util.Objects;

public final class ZoneHud {
    private static final float PROMPT_SCALE = 0.9f;
    private static final float PADDING = UiMetrics.PAD_WIDE;
    private static final float ROW_GAP = UiMetrics.GAP;
    private static final long ANNOUNCE_MILLIS = 4000L;
    private static final float MARKER_SCALE = 0.75f;
    private static final float MARKER_IDLE_ALPHA = UiWorldTag.IDLE_ALPHA;
    private static final float MARKER_FOCUS_ALPHA = UiWorldTag.FOCUS_ALPHA;
    private static final float HOVER_NEAR = 14.0f;
    private static final float HOVER_FAR = 46.0f;
    private static final float MARKER_DOT = 1.6f;
    private static final float MARKER_GONE = 0.01f;
    private static final float STACK_SPEED = 12.0f;

    private static final Smooth stackHeight = new Smooth(STACK_SPEED);

    private static String lastZoneId;
    private static ZoneType lastZoneType;
    private static Component actionPrompt;

    private ZoneHud() {}

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.player.isSpectator()
                || !ClientModules.allows(ModuleId.ZONES)) {
            ZonePrompts.clear();
            return;
        }

        float scale = UiScale.push(graphics);
        try {
            trackZone();
            renderMarkers(graphics, minecraft, scale);
            render(graphics, minecraft, screenWidth / scale, screenHeight / scale);
        } finally {
            UiScale.pop(graphics);
        }
    };

    private static void trackZone() {
        Zone zone = ZoneOccupancy.current();
        String zoneId = zone == null ? null : zone.id();

        if (!Objects.equals(zoneId, lastZoneId)) {
            onZoneChanged(zone);
            lastZoneId = zoneId;
        }
        updateActionPrompt(zone);
    }

    private static void onZoneChanged(Zone zone) {
        if (zone == null && shelters(lastZoneType)) {
            ZonePrompts.announce(Component.translatable("zones.hud.safe_left"), ANNOUNCE_MILLIS);
        }
        if (zone != null && shelters(zone.type())) {
            ZonePrompts.announce(Component.translatable("zones.hud.safe"), ANNOUNCE_MILLIS);
        }
        lastZoneType = zone == null ? null : zone.type();
    }

    private static void updateActionPrompt(Zone zone) {
        boolean unable = ClientCaptureData.isLocalPlayerCapturing() || knockedOut();
        Component wanted = zone == null || unable ? null : actionFor(zone);
        if (Objects.equals(describe(wanted), describe(actionPrompt))) return;

        if (actionPrompt != null) {
            ZonePrompts.release(actionPrompt);
        }
        if (wanted != null) {
            ZonePrompts.hold(wanted);
        }
        actionPrompt = wanted;
    }

    private static String describe(Component value) {
        return value == null ? null : value.getString();
    }

    private static Component actionFor(Zone zone) {
        if (shelters(zone.type())) return null;

        String key = KeyLabel.of(KeyBindings.CAPTURE_KEY);
        if (zone.type() == ZoneType.SHOP) return Component.translatable("zones.hud.shop", key);
        if (startsItself(zone)) return null;
        return Component.translatable("zones.hud.capture", key);
    }

    private static boolean startsItself(Zone zone) {
        return zone.type() == ZoneType.CAPTURE_POINT
                && ClientCaptureData.getPointMode(zone.id()).startsByPresence();
    }

    private static boolean shelters(ZoneType type) {
        return type == ZoneType.BASE;
    }

    private static boolean knockedOut() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;

        KnockdownCapability cap = minecraft.player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).orElse(null);
        return cap != null && cap.isKnocked();
    }

    private static void renderMarkers(GuiGraphics graphics, Minecraft minecraft, float scale) {
        float delta = UiFrame.delta();
        float centerScreenX = minecraft.getWindow().getGuiScaledWidth() / 2.0f;
        float centerScreenY = minecraft.getWindow().getGuiScaledHeight() / 2.0f;
        float markerScale = UiRender.crisp(graphics, MARKER_SCALE * HudConfig.markerScale());
        float height = UiWorldTag.height(minecraft.font, markerScale);

        for (ZoneMarkers.Marker marker : ZoneMarkers.live()) {
            float presence = marker.presence(delta);
            if (presence <= MARKER_GONE) continue;

            float focus = marker.focus(aimedAt(marker, centerScreenX, centerScreenY, scale), delta);
            float alpha = MARKER_IDLE_ALPHA + (MARKER_FOCUS_ALPHA - MARKER_IDLE_ALPHA) * focus;
            float ranged = UiAnim.easeOut(marker.ranged(delta));
            drawMarker(graphics, minecraft, marker, scale, markerScale, height, alpha, focus, ranged, presence);
        }
    }

    // WHY: вне прицела плашка сворачивается в точку и выходит из общего стека меток: во весь размер
    // WHY: она расталкивала соседей и закрывала собой вид, хотя игрок на неё не смотрит
    private static void drawMarker(GuiGraphics graphics, Minecraft minecraft, ZoneMarkers.Marker marker,
                                   float scale, float markerScale, float height, float alpha,
                                   float focus, float ranged, float presence) {
        float x = marker.screenX() / scale;
        float y = marker.screenY() / scale;
        float plate = focus * focus;

        drawDot(graphics, marker, x, y, focus, presence * (1.0f - plate));
        if (plate <= MARKER_GONE) return;

        UiWorldTag.render(graphics, minecraft.font, marker.label(), marker.rangeLabel(), ranged,
                x, y - height, height, markerScale, marker.color(), alpha, focus,
                presence * plate, marker.stack());
    }

    private static void drawDot(GuiGraphics graphics, ZoneMarkers.Marker marker, float x, float y,
                                float focus, float shown) {
        if (shown <= MARKER_GONE) return;

        UiRender.dot(graphics, x, y, MARKER_DOT * HudConfig.markerScale(),
                UiTheme.withAlpha(marker.color(), (0.45f + 0.55f * focus) * shown));
    }

    private static float aimedAt(ZoneMarkers.Marker marker, float centerX, float centerY, float scale) {
        float dx = marker.screenX() - centerX;
        float dy = marker.screenY() - centerY;
        float away = (float) Math.sqrt(dx * dx + dy * dy) / scale;
        return 1.0f - UiAnim.clamp01((away - HOVER_NEAR) / (HOVER_FAR - HOVER_NEAR));
    }

    private static void render(GuiGraphics graphics, Minecraft minecraft, float screenWidth, float screenHeight) {
        float delta = UiFrame.delta();
        boolean suppressed = minecraft.screen != null || UiHud.rosterOpen()
                || ClientCaptureData.isLocalPlayerCapturing();
        List<ZonePrompts.Prompt> prompts = ZonePrompts.update(delta, suppressed);

        float promptScale = UiRender.crisp(graphics, PROMPT_SCALE);
        float row = rowHeight(minecraft, promptScale);
        float step = row + ROW_GAP;
        int rows = shownRows(prompts);
        if (rows == 0 || !HudLayout.visible(HudSlot.ZONE_PROMPT)) {
            stackHeight.snap(row);
            return;
        }

        HudBox box = HudLayout.place(HudSlot.ZONE_PROMPT, widestPrompt(minecraft, prompts, promptScale),
                stackHeight.to(row + (rows - 1) * step, delta), screenWidth, screenHeight);

        HudLayout.push(graphics, box);
        try {
            drawStack(graphics, minecraft, prompts, box, row, step, promptScale);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    private static void drawStack(GuiGraphics graphics, Minecraft minecraft, List<ZonePrompts.Prompt> prompts,
                                  HudBox box, float row, float step, float scale) {
        for (ZonePrompts.Prompt prompt : prompts) {
            float alpha = prompt.alpha();
            if (alpha <= 0.01f) continue;

            drawPrompt(graphics, minecraft, prompt.text(), box.localCenterX(),
                    box.y() + prompt.slot() * step, row, scale, alpha);
        }
    }

    private static int shownRows(List<ZonePrompts.Prompt> prompts) {
        int rows = 0;
        for (ZonePrompts.Prompt prompt : prompts) {
            if (prompt.alpha() > 0.01f) rows++;
        }
        return rows;
    }

    private static float widestPrompt(Minecraft minecraft, List<ZonePrompts.Prompt> prompts, float scale) {
        float span = 0.0f;
        for (ZonePrompts.Prompt prompt : prompts) {
            if (prompt.alpha() <= 0.01f) continue;
            span = Math.max(span, promptWidth(minecraft, prompt.text(), scale));
        }
        return span;
    }

    private static float promptWidth(Minecraft minecraft, Component text, float scale) {
        return UiRender.width(minecraft.font, text) * scale + PADDING * 2.0f;
    }

    private static float rowHeight(Minecraft minecraft, float scale) {
        return UiMetrics.snap(minecraft.font.lineHeight * scale + UiMetrics.GAP_WIDE);
    }

    public static void preview(GuiGraphics graphics, float x, float y, float width, float height, float alpha) {
        Minecraft minecraft = Minecraft.getInstance();
        float scale = UiRender.crisp(graphics, PROMPT_SCALE);
        Component text = Component.translatable("battlecraft.custom.sample.zone_prompt");
        float span = promptWidth(minecraft, text, scale);
        float row = rowHeight(minecraft, scale);

        HudLayout.sample(HudSlot.ZONE_PROMPT, span, row);
        drawPrompt(graphics, minecraft, text, x + span / 2.0f, y, row, scale, alpha);
    }

    private static void drawPrompt(GuiGraphics graphics, Minecraft minecraft, Component text,
                                   float centerX, float y, float height, float scale, float alpha) {
        float width = promptWidth(minecraft, text, scale);
        float x = centerX - width / 2.0f;

        UiVital.card(graphics, x, y, width, height, UiMetrics.radius(height), alpha * 0.92f);
        UiRender.textCentered(graphics, minecraft.font, text, centerX,
                UiRender.centerY(y, height, scale), scale,
                UiTheme.alpha(HudLayout.tint(HudSlot.ZONE_PROMPT, HudInk.text()), alpha), false);
    }
}
