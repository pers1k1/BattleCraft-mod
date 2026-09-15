package com.persiki84.minimap.client;

import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneType;
import com.persiki84.zones.client.ClientMarkData;
import com.persiki84.zones.client.ClientZoneData;
import com.persiki84.zones.client.render.ZoneColors;
import com.persiki84.zones.mark.MapMark;
import com.mojang.blaze3d.systems.RenderSystem;
import com.persiki84.capturepoints.client.ClientCaptureData;
import com.persiki84.minimap.network.MapMarkerSyncPacket;
import com.persiki84.minimap.network.MapWorldMarkerSyncPacket;
import com.persiki84.minimap.network.PlayerPositionSyncPacket;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.battlecraft.client.ClientModules;
import com.persiki84.battlecraft.modules.ModuleId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.lwjgl.opengl.GL11;

import java.util.Map;

public class MinimapOverlay {
    private static final int PADDING = 6;
    private static final float COMPACT_SCALE = 0.8f;
    private static final float LABEL_SCALE = 0.75f;
    private static final float PLATE_PAD_X = 3.0f;
    private static final float PLATE_PAD_Y = 1.5f;
    private static final float MARKER_CLEARANCE = 1.6f;
    private static final float PLATE_HEIGHT = 8.0f;
    private static final int GRID_COLOR = 0x1AFFFFFF;
    private static final int FIELD_COLOR = 0xE61C1C1F;
    private static final int PLATE_COLOR = 0xE6434349;
    private static final int PLATE_TEXT = 0xFFF2F2F4;
    private static final float BASE_RADIUS = 3.4f;
    private static final float MARKER_DOT = 1.5f;
    private static final Component BASE_LABEL = Component.translatable("zones.marker.base");

    public static boolean isVisible() {
        if (!ClientMapData.enableMinimap) return false;
        if (!ClientModules.allows(ModuleId.MINIMAP) || !HudLayout.visible(HudSlot.MINIMAP)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.renderDebug || mc.options.hideGui) return false;
        return com.persiki84.battlecraft.client.ClientGameData.isSoftDisabled()
                || com.persiki84.battlecraft.client.ClientGameData.getCurrentPhase() != com.persiki84.battlecraft.BattleCraftManager.GamePhase.LOBBY;
    }

    public static float renderSize() {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        return ClientMapData.minimapSize * (gui > 3.5 ? 1.0f : COMPACT_SCALE);
    }

    public static float hudBottom() {
        if (!isVisible()) return 0.0f;

        HudBox box = HudLayout.box(HudSlot.MINIMAP);
        return box.drawn() ? box.y() + renderSize() : PADDING + renderSize();
    }

    public static final IGuiOverlay HUD_MINIMAP = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        if (!isVisible()) return;
        Minecraft mc = Minecraft.getInstance();

        float scale = UiScale.push(guiGraphics);
        try {
            render(guiGraphics, mc, partialTick, screenWidth / scale, screenHeight / scale, scale);
        } finally {
            UiScale.pop(guiGraphics);
        }
    };

    private static void render(GuiGraphics guiGraphics, Minecraft mc, float partialTick,
                               float logicalWidth, float logicalHeight, float scale) {
        float size = renderSize();
        float zoom = ClientMapData.minimapZoom;
        HudBox box = HudLayout.place(HudSlot.MINIMAP, size, size, logicalWidth, logicalHeight);
        float x = box.x();
        float y = box.y();
        float radius = Math.min(14.0f, size * 0.16f);
        float cx = x + size / 2.0f;
        float cy = y + size / 2.0f;

        double mapX = Mth.lerp(partialTick, mc.player.xo, mc.player.getX());
        double mapZ = Mth.lerp(partialTick, mc.player.zo, mc.player.getZ());

        UiRender.panel(guiGraphics, x, y, size, size, radius, FIELD_COLOR);

        clipToShape(guiGraphics, x, y, size, radius);
        MapTextureManager.renderMap(guiGraphics, mapX, mapZ, zoom, cx, cy, x, y, x + size, y + size);
        renderGrid(guiGraphics, x, y, size, zoom, cx, cy, mapX, mapZ);
        releaseShapeClip();

        guiGraphics.enableScissor((int) (x * scale), (int) (y * scale), (int) ((x + size) * scale), (int) ((y + size) * scale));
        renderObjectives(guiGraphics, mapX, mapZ, zoom, cx, cy);
        renderMarkers(guiGraphics, mc, mapX, mapZ, zoom, cx, cy);

        float yaw = Mth.lerp(partialTick, mc.player.yRotO, mc.player.getYRot());
        UiRender.arrow(guiGraphics, cx, cy, yaw, 3.2f, UiAccent.color(), UiTheme.alpha(UiPalette.panelDeep(), 0.75f));
        guiGraphics.disableScissor();

        label(guiGraphics, mc, Mth.floor(mapX) + " " + Mth.floor(mapZ), cx, y + size - 9.0f, PLATE_TEXT);
    }

    private static void clipToShape(GuiGraphics guiGraphics, float x, float y, float size, float radius) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.colorMask(false, false, false, false);

        UiRender.panel(guiGraphics, x, y, size, size, radius, 0xFFFFFFFF);

        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthFunc(GL11.GL_EQUAL);
    }

    private static void releaseShapeClip() {
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    private static void renderGrid(GuiGraphics guiGraphics, float x, float y, float size, float zoom,
                                   float cx, float cy, double mapX, double mapZ) {
        double maxWorldX = mapX + ((x + size) - cx) / zoom;
        long firstGridX = (long) Math.ceil((mapX - (cx - x) / zoom) / 100.0) * 100;
        for (double wx = firstGridX; wx <= maxWorldX; wx += 100.0) {
            UiRender.rect(guiGraphics, (float) (cx + (wx - mapX) * zoom), y, 1.0f, size, GRID_COLOR);
        }

        double maxWorldZ = mapZ + ((y + size) - cy) / zoom;
        long firstGridZ = (long) Math.ceil((mapZ - (cy - y) / zoom) / 100.0) * 100;
        for (double wz = firstGridZ; wz <= maxWorldZ; wz += 100.0) {
            UiRender.rect(guiGraphics, x, (float) (cy + (wz - mapZ) * zoom), size, 1.0f, GRID_COLOR);
        }
    }

    private static void renderObjectives(GuiGraphics guiGraphics, double mapX, double mapZ, float zoom, float cx, float cy) {
        boolean active = !com.persiki84.battlecraft.client.ClientGameData.isSoftDisabled()
                && com.persiki84.battlecraft.client.ClientGameData.getCurrentPhase() == com.persiki84.battlecraft.BattleCraftManager.GamePhase.ACTIVE;
        if (!active) return;

        for (Map.Entry<String, String> entry : ClientCaptureData.getAllPointOwners().entrySet()) {
            BlockPos pos = ClientCaptureData.getPointPosition(entry.getKey());
            if (pos != null) {
                renderPoint(guiGraphics, pos.getX(), pos.getZ(), mapX, mapZ, zoom, cx, cy,
                        MapRenderUtil.getTeamColor(entry.getValue()), entry.getKey());
            }
        }
        if (!ClientCaptureData.areAllPointsCapturedBySameTeam()) return;

        for (Map.Entry<String, String> entry : ClientCaptureData.getAllFinalPointOwners().entrySet()) {
            BlockPos pos = ClientCaptureData.getFinalPointPosition(entry.getKey());
            if (pos != null) {
                renderPoint(guiGraphics, pos.getX(), pos.getZ(), mapX, mapZ, zoom, cx, cy, UiAccent.color(), entry.getKey());
            }
        }
    }

    private static void renderMarkers(GuiGraphics guiGraphics, Minecraft mc, double mapX, double mapZ,
                                      float zoom, float cx, float cy) {
        renderBases(guiGraphics, mapX, mapZ, zoom, cx, cy);

        for (MapWorldMarkerSyncPacket.WorldMarker marker : ClientMapData.getWorldMarkers()) {
            renderWorldMarker(guiGraphics, marker.x, marker.z, mapX, mapZ, zoom, cx, cy, Component.translatable(marker.key).getString());
        }

        int pinged = 0;
        for (MapMark mark : ClientMarkData.all()) {
            if (mc.level == null || !mc.level.dimension().location().equals(mark.dimension())) continue;
            if (MapLabels.isLabel(mark)) continue;
            pinged++;
            renderMarker(guiGraphics, mark.position().getX() + 0.5, mark.position().getZ() + 0.5,
                    mapX, mapZ, zoom, cx, cy, mark.color(), mark.label(),
                    MarkerPings.age(MarkerKeys.of(mark), mark.position().getX(), mark.position().getZ()));
        }

        for (MapMarkerSyncPacket.MarkerData marker : ClientMapData.getMarkers()) {
            if (!ClientMapData.showOtherMarkers && !marker.playerName.equals(mc.player.getScoreboardName())) continue;
            String name = marker.isTeam ? marker.playerName : Component.translatable("minimap.label.personal").getString();
            pinged++;
            renderMarker(guiGraphics, marker.x, marker.z, mapX, mapZ, zoom, cx, cy, UiAccent.color(), name,
                    MarkerPings.age(MarkerKeys.of(marker), marker.x, marker.z));
        }
        MarkerPings.sweep(pinged);

        for (PlayerPositionSyncPacket.PlayerPos other : ClientMapData.getPlayers()) {
            if (other.playerId.equals(mc.player.getUUID())) continue;
            renderPlayerDot(guiGraphics, other.x, other.z, mapX, mapZ, zoom, cx, cy, MapRenderUtil.getPlayerTeamColor(other.playerName));
        }
    }

    private static void renderBases(GuiGraphics guiGraphics, double mapX, double mapZ, float zoom, float cx, float cy) {
        for (Zone zone : ClientZoneData.all()) {
            if (zone.type() != ZoneType.BASE || !ZoneColors.visibleToOwnTeam(zone)) continue;

            renderBase(guiGraphics, zone.area().centerX(), zone.area().centerZ(), mapX, mapZ, zoom, cx, cy,
                    0xFF000000 | ZoneColors.packed(zone));
        }
    }

    private static void renderBase(GuiGraphics guiGraphics, double bx, double bz, double mapX, double mapZ,
                                   float zoom, float cx, float cy, int color) {
        double dx = (bx - mapX) * zoom;
        double dz = (bz - mapZ) * zoom;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float maxR = (renderSize() - 4.0f) / 2.0f - 4.0f;
        boolean clamped = dist > maxR;
        if (clamped) {
            dx = dx / dist * maxR;
            dz = dz / dist * maxR;
        }

        float screenX = (float) (cx + dx);
        float screenY = (float) (cy + dz);
        int shown = UiTheme.muted(color, 0.2f);
        UiRender.ring(guiGraphics, screenX, screenY, BASE_RADIUS, 2.8f, 1.0f, UiPalette.panelDeep());
        UiRender.ring(guiGraphics, screenX, screenY, BASE_RADIUS, 1.6f, 1.0f, shown);
        UiRender.dot(guiGraphics, screenX, screenY, 1.1f, shown);

        if (!clamped) {
            labelAbove(guiGraphics, Minecraft.getInstance(), BASE_LABEL.getString(), screenX, screenY,
                    BASE_RADIUS + 1.4f, PLATE_TEXT);
        }
    }

    private static void labelAbove(GuiGraphics guiGraphics, Minecraft mc, String value, float centerX,
                                   float markerY, float markerRadius, int color) {
        float scale = UiRender.crisp(guiGraphics, LABEL_SCALE);
        float height = PLATE_HEIGHT * scale + PLATE_PAD_Y * 2.0f;
        float plateY = markerY - markerRadius - MARKER_CLEARANCE - height;
        plate(guiGraphics, mc, value, centerX, plateY, height, scale);
        UiRender.labelCentered(guiGraphics, mc.font, value, centerX,
                UiRender.centerY(plateY, height, scale), scale, color);
    }

    private static void label(GuiGraphics guiGraphics, Minecraft mc, String value, float centerX, float y, int color) {
        float scale = UiRender.crisp(guiGraphics, LABEL_SCALE);
        float height = PLATE_HEIGHT * scale + PLATE_PAD_Y * 2.0f;
        float plateY = y - PLATE_PAD_Y;
        plate(guiGraphics, mc, value, centerX, plateY, height, scale);
        UiRender.labelCentered(guiGraphics, mc.font, value, centerX,
                UiRender.centerY(plateY, height, scale), scale, color);
    }

    private static void plate(GuiGraphics guiGraphics, Minecraft mc, String value, float centerX, float plateY,
                              float height, float scale) {
        float width = UiRender.measure(guiGraphics, mc.font, Component.literal(value), scale) + PLATE_PAD_X * 2.0f;
        UiRender.panel(guiGraphics, centerX - width / 2.0f, plateY, width, height, height / 2.0f, PLATE_COLOR);
    }

    private static void renderPlayerDot(GuiGraphics guiGraphics, double px, double pz, double mapX, double mapZ, float zoom, float cx, float cy, int color) {
        double dx = (px - mapX) * zoom;
        double dz = (pz - mapZ) * zoom;
        float maxR = (renderSize() - 4.0f) / 2.0f - 3.0f;
        if (Math.sqrt(dx * dx + dz * dz) > maxR) return;

        float screenX = (float) (cx + dx);
        float screenY = (float) (cy + dz);
        UiRender.dot(guiGraphics, screenX, screenY, 2.6f, UiPalette.panelDeep());
        UiRender.dot(guiGraphics, screenX, screenY, 2.0f, UiTheme.muted(color, 0.2f));
    }

    private static void renderMarker(GuiGraphics guiGraphics, double mx, double mz, double mapX, double mapZ,
                                     float zoom, float cx, float cy, int color, String name, float age) {
        double dx = (mx - mapX) * zoom;
        double dz = (mz - mapZ) * zoom;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float maxR = (renderSize() - 4.0f) / 2.0f - 4.0f;
        boolean clamped = dist > maxR;
        if (clamped) {
            dx = dx / dist * maxR;
            dz = dz / dist * maxR;
        }

        float screenX = (float) (cx + dx);
        float screenY = (float) (cy + dz);
        int shown = UiTheme.muted(color, 0.2f);
        float pop = MarkerPings.pop(age);
        MarkerPings.ripple(guiGraphics, screenX, screenY, MARKER_DOT, shown, age);
        UiRender.dot(guiGraphics, screenX, screenY, MARKER_DOT * pop + 0.6f, UiPalette.panelDeep());
        UiRender.dot(guiGraphics, screenX, screenY, MARKER_DOT * pop, shown);

        if (!clamped) {
            labelAbove(guiGraphics, Minecraft.getInstance(), name, screenX, screenY, 2.1f, PLATE_TEXT);
        }
    }

    private static void renderWorldMarker(GuiGraphics guiGraphics, double mx, double mz, double mapX, double mapZ, float zoom, float cx, float cy, String name) {
        double dx = (mx - mapX) * zoom;
        double dz = (mz - mapZ) * zoom;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float maxR = (renderSize() - 4.0f) / 2.0f - 4.0f;
        boolean clamped = dist > maxR;
        if (clamped) {
            dx = dx / dist * maxR;
            dz = dz / dist * maxR;
        }

        float screenX = (float) (cx + dx);
        float screenY = (float) (cy + dz);
        UiRender.panel(guiGraphics, screenX - 3.1f, screenY - 3.1f, 6.2f, 6.2f, 2.0f, UiPalette.panelDeep());
        UiRender.panel(guiGraphics, screenX - 2.5f, screenY - 2.5f, 5.0f, 5.0f, 1.5f, UiAccent.color());

        if (!clamped) {
            labelAbove(guiGraphics, Minecraft.getInstance(), name, screenX, screenY, 3.1f, PLATE_TEXT);
        }
    }

    private static void renderPoint(GuiGraphics guiGraphics, double px, double pz, double mapX, double mapZ, float zoom, float cx, float cy, int color, String name) {
        double dx = (px - mapX) * zoom;
        double dz = (pz - mapZ) * zoom;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float maxR = (renderSize() - 4.0f) / 2.0f - 4.0f;
        boolean clamped = dist > maxR;
        if (clamped) {
            dx = dx / dist * maxR;
            dz = dz / dist * maxR;
        }

        float screenX = (float) (cx + dx);
        float screenY = (float) (cy + dz);
        UiRender.panel(guiGraphics, screenX - 3.6f, screenY - 3.6f, 7.2f, 7.2f, 2.4f, UiPalette.panelDeep());
        UiRender.panel(guiGraphics, screenX - 3.0f, screenY - 3.0f, 6.0f, 6.0f, 2.0f, UiTheme.muted(color, 0.25f));

        if (!clamped) {
            labelAbove(guiGraphics, Minecraft.getInstance(), name, screenX, screenY, 3.1f, PLATE_TEXT);
        }
    }
}
