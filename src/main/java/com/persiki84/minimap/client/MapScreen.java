package com.persiki84.minimap.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.persiki84.capturepoints.client.ClientCaptureData;
import com.persiki84.minimap.network.MapMarkerSyncPacket;
import com.persiki84.minimap.network.MapMarkerUpdatePacket;
import com.persiki84.minimap.network.PacketHandler;
import com.persiki84.minimap.network.PlayerPositionSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

public class MapScreen extends Screen {
    private double mapX = 0;
    private double mapZ = 0;
    private float zoom = 1.0f;
    private float targetZoom = 1.0f;
    private boolean isDragging = false;
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private boolean clickedWidget = false;
    private boolean hasDragged = false;
    
    public MapScreen() {
        super(Component.literal("Map"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mapX == 0 && mapZ == 0) {
            mapX = mc.player.getX();
            mapZ = mc.player.getZ();
        }
        this.targetZoom = this.zoom;

        this.addRenderableWidget(Button.builder(Component.translatable("minimap.button.settings"), button -> {
            Minecraft.getInstance().setScreen(new MinimapSettingsScreen(this));
        }).bounds(this.width - 110, 10, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (Math.abs(this.targetZoom - this.zoom) > 0.001f) {
            double anchorMouseX = (mouseX >= 0 && mouseX <= this.width) ? mouseX : centerX;
            double anchorMouseY = (mouseY >= 0 && mouseY <= this.height) ? mouseY : centerY;

            double anchorX = mapX + (anchorMouseX - centerX) / zoom;
            double anchorZ = mapZ + (anchorMouseY - centerY) / zoom;

            this.zoom += (this.targetZoom - this.zoom) * 0.15f;

            mapX = anchorX - (anchorMouseX - centerX) / this.zoom;
            mapZ = anchorZ - (anchorMouseY - centerY) / this.zoom;
        } else {
            this.zoom = this.targetZoom;
        }

        this.renderBackground(guiGraphics);

        guiGraphics.enableScissor(0, 0, this.width, this.height);

        MapTextureManager.renderMap(guiGraphics, mapX, mapZ, zoom, centerX, centerY, this.width, this.height);

        double minWorldX = mapX - centerX / zoom;
        double maxWorldX = mapX + (this.width - centerX) / zoom;
        long firstGridX = (long) Math.ceil(minWorldX / 100.0) * 100;
        for (double wx = firstGridX; wx <= maxWorldX; wx += 100.0) {
            double sx = centerX + (wx - mapX) * zoom;
            guiGraphics.fill((int) sx, 0, (int) sx + 1, this.height, 0x44000000);
        }

        double minWorldZ = mapZ - centerY / zoom;
        double maxWorldZ = mapZ + (this.height - centerY) / zoom;
        long firstGridZ = (long) Math.ceil(minWorldZ / 100.0) * 100;
        for (double wz = firstGridZ; wz <= maxWorldZ; wz += 100.0) {
            double sy = centerY + (wz - mapZ) * zoom;
            guiGraphics.fill(0, (int) sy, this.width, (int) sy + 1, 0x44000000);
        }

        renderPoints(guiGraphics, centerX, centerY);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            renderPlayerDot(guiGraphics, mc.player.getX(), mc.player.getZ(), mc.player.getYRot(), centerX, centerY, MapRenderUtil.getPlayerTeamColor(mc.player.getScoreboardName()), Component.translatable("minimap.label.you").getString());
        }

        for (PlayerPositionSyncPacket.PlayerPos p : ClientMapData.getPlayers()) {
            if (mc.player != null && p.playerId.equals(mc.player.getUUID())) continue;
            renderPlayerDot(guiGraphics, p.x, p.z, p.yRot, centerX, centerY, MapRenderUtil.getPlayerTeamColor(p.playerName), p.playerName);
        }

        for (MapMarkerSyncPacket.MarkerData marker : ClientMapData.getMarkers()) {
            if (!ClientMapData.showOtherMarkers && !marker.playerName.equals(mc.player.getScoreboardName())) continue;
            int color;
            String name;
            if (!marker.isTeam) {
                color = 0xFF55FFFF;
                name = Component.translatable("minimap.label.personal_marker").getString();
            } else {
                color = MapRenderUtil.getPlayerTeamColor(marker.playerName);
                name = marker.playerName;
            }
            renderMarker(guiGraphics, marker.x, marker.z, centerX, centerY, color, name);
        }

        for (com.persiki84.minimap.network.MapWorldMarkerSyncPacket.WorldMarker wm : ClientMapData.getWorldMarkers()) {
            renderWorldMarker(guiGraphics, wm.x, wm.z, centerX, centerY, 0xFFFF9A1F, Component.translatable(wm.key).getString());
        }

        guiGraphics.disableScissor();

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderPoints(GuiGraphics guiGraphics, int centerX, int centerY) {
        for (Map.Entry<String, String> entry : ClientCaptureData.getAllPointOwners().entrySet()) {
            BlockPos pos = ClientCaptureData.getPointPosition(entry.getKey());
            if (pos != null) {
                renderPointMarker(guiGraphics, pos.getX(), pos.getZ(), centerX, centerY, 0xFF000000 | (com.persiki84.capturepoints.client.CaptureHudOverlay.getTeamColor(entry.getValue()).getColor() != null ? com.persiki84.capturepoints.client.CaptureHudOverlay.getTeamColor(entry.getValue()).getColor() : 0xFFFFFF), entry.getKey());
            }
        }

        if (ClientCaptureData.areAllPointsCapturedBySameTeam()) {
            for (Map.Entry<String, String> entry : ClientCaptureData.getAllFinalPointOwners().entrySet()) {
                BlockPos pos = ClientCaptureData.getFinalPointPosition(entry.getKey());
                if (pos != null) {
                    renderPointMarker(guiGraphics, pos.getX(), pos.getZ(), centerX, centerY, 0xFFFFD700, entry.getKey() + " (Final)");
                }
            }
        }
    }

    private void renderPlayerDot(GuiGraphics guiGraphics, double px, double pz, float yRot, int cx, int cy, int color, String name) {
        double screenX = cx + (px - mapX) * zoom;
        double screenY = cy + (pz - mapZ) * zoom;

        MapRenderUtil.fillRoundedRect(guiGraphics, (float) screenX - 3, (float) screenY - 3, 6, 6, 3, color);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, name, (int) screenX, (int) screenY - 12, 0xFFFFFFFF);
    }

    private void renderMarker(GuiGraphics guiGraphics, double mx, double mz, int cx, int cy, int color, String name) {
        double screenX = cx + (mx - mapX) * zoom;
        double screenY = cy + (mz - mapZ) * zoom;

        guiGraphics.fill((int) screenX - 2, (int) screenY - 2, (int) screenX + 2, (int) screenY + 2, color);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, name, (int) screenX, (int) screenY - 10, color);
    }

    private void renderWorldMarker(GuiGraphics guiGraphics, double mx, double mz, int cx, int cy, int color, String name) {
        double screenX = cx + (mx - mapX) * zoom;
        double screenY = cy + (mz - mapZ) * zoom;

        MapRenderUtil.fillRoundedRect(guiGraphics, (float) screenX - 4, (float) screenY - 4, 8, 8, 2, color);
        guiGraphics.fill((int) screenX - 1, (int) screenY - 7, (int) screenX + 1, (int) screenY - 4, color);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, name, (int) screenX, (int) screenY - 18, color);
    }

    private void renderPointMarker(GuiGraphics guiGraphics, double px, double pz, int cx, int cy, int color, String name) {
        double screenX = cx + (px - mapX) * zoom;
        double screenY = cy + (pz - mapZ) * zoom;

        MapRenderUtil.fillRoundedRect(guiGraphics, (float) screenX - 4, (float) screenY - 4, 8, 8, 2, color);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, name, (int) screenX, (int) screenY - 12, color);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            hasDragged = true;
            mapX -= dragX / zoom;
            mapZ -= dragY / zoom;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        targetZoom = (float) Math.max(0.1, Math.min(10.0, targetZoom + delta * 0.15 * targetZoom));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        hasDragged = false;
        clickedWidget = super.mouseClicked(mouseX, mouseY, button);
        if (clickedWidget) {
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (clickedWidget) {
            clickedWidget = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        if (!hasDragged) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                boolean isTeam = (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                int cx = this.width / 2;
                int cy = this.height / 2;
                double clickWorldX = mapX + (mouseX - cx) / zoom;
                double clickWorldZ = mapZ + (mouseY - cy) / zoom;

                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    boolean removing = false;
                    for (MapMarkerSyncPacket.MarkerData md : ClientMapData.getMarkers()) {
                        if (md.playerId.equals(mc.player.getUUID()) && md.isTeam == isTeam) {
                            double dx = md.x - clickWorldX;
                            double dz = md.z - clickWorldZ;
                            if (dx * dx + dz * dz < (20.0 / zoom) * (20.0 / zoom)) {
                                removing = true;
                            }
                            break;
                        }
                    }

                    if (removing) {
                        PacketHandler.INSTANCE.sendToServer(new MapMarkerUpdatePacket(0, 0, true, isTeam));
                    } else {
                        PacketHandler.INSTANCE.sendToServer(new MapMarkerUpdatePacket(clickWorldX, clickWorldZ, false, isTeam));
                    }
                }
                return true;
            }
        }
        hasDragged = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
