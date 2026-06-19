package com.persiki84.minimap.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.persiki84.capturepoints.client.ClientCaptureData;
import com.persiki84.minimap.network.MapMarkerSyncPacket;
import com.persiki84.minimap.network.PlayerPositionSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.Map;

public class MinimapOverlay {
    public static final IGuiOverlay HUD_MINIMAP = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        if (!ClientMapData.enableMinimap) return;
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.options.renderDebug) return;
        
        if (!com.persiki84.battlecraft.client.ClientGameData.isSoftDisabled() && com.persiki84.battlecraft.client.ClientGameData.getCurrentPhase() == com.persiki84.battlecraft.BattleCraftManager.GamePhase.LOBBY) {
            return;
        }

        int size = ClientMapData.minimapSize;
        float zoom = ClientMapData.minimapZoom;
        int padding = 5;
        int x = screenWidth - size - padding;
        int y = padding;
        float radius = size * 0.25f;
        float thickness = 2.0f;

        MapRenderUtil.fillRoundedRect(guiGraphics, x, y, size, size, radius, 0xFF1E1E1E);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.colorMask(false, false, false, false);

        MapRenderUtil.fillRoundedRect(guiGraphics, x + thickness, y + thickness, size - thickness * 2, size - thickness * 2, radius - thickness, 0xFFFFFFFF);

        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthFunc(GL11.GL_EQUAL);

        int cx = x + size / 2;
        int cy = y + size / 2;

        double mapX = mc.player.getX();
        double mapZ = mc.player.getZ();

        MapTextureManager.renderMap(guiGraphics, mapX, mapZ, zoom, cx, cy, size, size);

        double minWorldX = mapX - (cx - x) / zoom;
        double maxWorldX = mapX + ((x + size) - cx) / zoom;
        long firstGridX = (long) Math.ceil(minWorldX / 100.0) * 100;
        for (double wx = firstGridX; wx <= maxWorldX; wx += 100.0) {
            double sx = cx + (wx - mapX) * zoom;
            guiGraphics.fill((int) sx, y, (int) sx + 1, y + size, 0x44000000);
        }

        double minWorldZ = mapZ - (cy - y) / zoom;
        double maxWorldZ = mapZ + ((y + size) - cy) / zoom;
        long firstGridZ = (long) Math.ceil(minWorldZ / 100.0) * 100;
        for (double wz = firstGridZ; wz <= maxWorldZ; wz += 100.0) {
            double sy = cy + (wz - mapZ) * zoom;
            guiGraphics.fill(x, (int) sy, x + size, (int) sy + 1, 0x44000000);
        }

        boolean isBattlecraftActive = !com.persiki84.battlecraft.client.ClientGameData.isSoftDisabled() &&
                com.persiki84.battlecraft.client.ClientGameData.getCurrentPhase() == com.persiki84.battlecraft.BattleCraftManager.GamePhase.ACTIVE;

        if (isBattlecraftActive) {
            for (Map.Entry<String, String> entry : ClientCaptureData.getAllPointOwners().entrySet()) {
                BlockPos pos = ClientCaptureData.getPointPosition(entry.getKey());
                if (pos != null) {
                    renderPointMarker(guiGraphics, pos.getX(), pos.getZ(), mapX, mapZ, zoom, cx, cy, 0xFF000000 | (com.persiki84.capturepoints.client.CaptureHudOverlay.getTeamColor(entry.getValue()).getColor() != null ? com.persiki84.capturepoints.client.CaptureHudOverlay.getTeamColor(entry.getValue()).getColor() : 0xFFFFFF), entry.getKey());
                }
            }

            if (ClientCaptureData.areAllPointsCapturedBySameTeam()) {
                for (Map.Entry<String, String> entry : ClientCaptureData.getAllFinalPointOwners().entrySet()) {
                    BlockPos pos = ClientCaptureData.getFinalPointPosition(entry.getKey());
                    if (pos != null) {
                        renderPointMarker(guiGraphics, pos.getX(), pos.getZ(), mapX, mapZ, zoom, cx, cy, 0xFFFFD700, entry.getKey());
                    }
                }
            }
        }

        renderPlayerDot(guiGraphics, mapX, mapZ, mc.player.getYRot(), mapX, mapZ, zoom, cx, cy, MapRenderUtil.getPlayerTeamColor(mc.player.getScoreboardName()));

        for (PlayerPositionSyncPacket.PlayerPos p : ClientMapData.getPlayers()) {
            if (p.playerId.equals(mc.player.getUUID())) continue;
            renderPlayerDot(guiGraphics, p.x, p.z, p.yRot, mapX, mapZ, zoom, cx, cy, MapRenderUtil.getPlayerTeamColor(p.playerName));
        }

        for (MapMarkerSyncPacket.MarkerData marker : ClientMapData.getMarkers()) {
            if (!ClientMapData.showOtherMarkers && !marker.playerName.equals(mc.player.getScoreboardName())) continue;
            int color;
            String name;
            if (!marker.isTeam) {
                color = 0xFF55FFFF;
                name = Component.translatable("minimap.label.personal").getString();
            } else {
                color = MapRenderUtil.getPlayerTeamColor(marker.playerName);
                name = marker.playerName;
            }
            renderMarker(guiGraphics, marker.x, marker.z, mapX, mapZ, zoom, cx, cy, color, name);
        }

        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    };

    private static void renderPlayerDot(GuiGraphics guiGraphics, double px, double pz, float yRot, double mapX, double mapZ, float zoom, int cx, int cy, int color) {
        double dx = (px - mapX) * zoom;
        double dz = (pz - mapZ) * zoom;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float maxR = (ClientMapData.minimapSize - 4.0f) / 2.0f - 3.0f;
        if (dist > maxR) {
            return;
        }
        double screenX = cx + dx;
        double screenY = cy + dz;
        MapRenderUtil.fillRoundedRect(guiGraphics, (float) screenX - 2, (float) screenY - 2, 4, 4, 2, color);
    }

    private static void renderMarker(GuiGraphics guiGraphics, double mx, double mz, double mapX, double mapZ, float zoom, int cx, int cy, int color, String name) {
        double dx = (mx - mapX) * zoom;
        double dz = (mz - mapZ) * zoom;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float maxR = (ClientMapData.minimapSize - 4.0f) / 2.0f - 4.0f;
        boolean clamped = false;
        if (dist > maxR) {
            dx = (dx / dist) * maxR;
            dz = (dz / dist) * maxR;
            clamped = true;
        }
        double screenX = cx + dx;
        double screenY = cy + dz;
        guiGraphics.fill((int) screenX - 1, (int) screenY - 1, (int) screenX + 2, (int) screenY + 2, color);
        if (!clamped) {
            guiGraphics.drawString(Minecraft.getInstance().font, name, (int) screenX + 3, (int) screenY - 4, color, false);
        }
    }

    private static void renderPointMarker(GuiGraphics guiGraphics, double px, double pz, double mapX, double mapZ, float zoom, int cx, int cy, int color, String name) {
        double dx = (px - mapX) * zoom;
        double dz = (pz - mapZ) * zoom;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float maxR = (ClientMapData.minimapSize - 4.0f) / 2.0f - 4.0f;
        boolean clamped = false;
        if (dist > maxR) {
            dx = (dx / dist) * maxR;
            dz = (dz / dist) * maxR;
            clamped = true;
        }
        double screenX = cx + dx;
        double screenY = cy + dz;
        MapRenderUtil.fillRoundedRect(guiGraphics, (float) screenX - 3, (float) screenY - 3, 6, 6, 2, color);
        if (!clamped) {
            guiGraphics.drawString(Minecraft.getInstance().font, name, (int) screenX + 4, (int) screenY - 4, color, false);
        }
    }
}
