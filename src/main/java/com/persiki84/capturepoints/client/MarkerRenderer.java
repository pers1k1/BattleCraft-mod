package com.persiki84.capturepoints.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.persiki84.capturepoints.CapturePointsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = CapturePointsMod.MOD_ID, value = Dist.CLIENT)
public class MarkerRenderer {
    public static final List<ProjectedMarker> markersToRender = new ArrayList<>();

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        markersToRender.clear();

        if (!ClientCaptureData.isLocalMarkersEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 cameraPos = event.getCamera().getPosition();
        Matrix4f viewMatrix = new Matrix4f(event.getPoseStack().last().pose());
        Matrix4f projMatrix = new Matrix4f(event.getProjectionMatrix());

        boolean isBattlecraftActive = !com.persiki84.battlecraft.client.ClientGameData.isSoftDisabled() &&
                com.persiki84.battlecraft.client.ClientGameData.getCurrentPhase() == com.persiki84.battlecraft.BattleCraftManager.GamePhase.ACTIVE;

        if (isBattlecraftActive && ClientCaptureData.isServerCaptureMarkers()) {
            projectMarkers(mc, cameraPos, viewMatrix, projMatrix, ClientCaptureData.getAllPointOwners(), false);
        }

        if (isBattlecraftActive && ClientCaptureData.isServerFinalMarkers() && ClientCaptureData.areAllPointsCapturedBySameTeam()) {
            projectMarkers(mc, cameraPos, viewMatrix, projMatrix, ClientCaptureData.getAllFinalPointOwners(), true);
        }

        projectPlayerMarkers(mc, cameraPos, viewMatrix, projMatrix);
    }

    private static void projectPlayerMarkers(Minecraft mc, Vec3 cameraPos, Matrix4f viewMatrix, Matrix4f projMatrix) {
        for (com.persiki84.minimap.network.MapMarkerSyncPacket.MarkerData marker : com.persiki84.minimap.client.ClientMapData.getMarkers()) {
            if (!com.persiki84.minimap.client.ClientMapData.showOtherMarkers && !marker.playerName.equals(mc.player.getScoreboardName())) continue;
            double dx = marker.x - cameraPos.x;
            double dy = marker.y - cameraPos.y;
            double dz = marker.z - cameraPos.z;

            double distSq = dx * dx + dy * dy + dz * dz;
            double dist = Math.sqrt(distSq);
            if (dist > 1500) continue;

            Vector4f vec = new Vector4f((float) dx, (float) dy, (float) dz, 0.0f);
            vec.mul(viewMatrix);
            vec.w = 1.0f;
            vec.mul(projMatrix);

            if (vec.w() > 0) {
                float screenX = (vec.x() / vec.w() + 1.0f) * 0.5f * mc.getWindow().getGuiScaledWidth();
                float screenY = (1.0f - vec.y() / vec.w()) * 0.5f * mc.getWindow().getGuiScaledHeight();
                
                boolean isSelf = !marker.isTeam && marker.playerName.equals(mc.player.getScoreboardName());
                String displayName = isSelf ? null : marker.playerName;
                int color = marker.isTeam ? com.persiki84.minimap.client.MapRenderUtil.getPlayerTeamColor(marker.playerName) : 0xFF55FFFF;
                markersToRender.add(new ProjectedMarker(displayName, null, false, dist, screenX, screenY, color));
            }
        }
    }

    private static void projectMarkers(Minecraft mc, Vec3 cameraPos, Matrix4f viewMatrix, Matrix4f projMatrix, Map<String, String> points, boolean isFinal) {
        for (Map.Entry<String, String> entry : points.entrySet()) {
            String pointName = entry.getKey();
            String owner = entry.getValue();

            BlockPos pos = isFinal ? ClientCaptureData.getFinalPointPosition(pointName) : ClientCaptureData.getPointPosition(pointName);
            if (pos == null) continue;

            double dx = pos.getX() + 0.5 - cameraPos.x;
            double dy = pos.getY() + 1.5 - cameraPos.y;
            double dz = pos.getZ() + 0.5 - cameraPos.z;

            double distSq = dx * dx + dy * dy + dz * dz;
            double dist = Math.sqrt(distSq);
            if (dist > 1500) continue;

            Vector4f vec = new Vector4f((float) dx, (float) dy, (float) dz, 1.0f);
            vec.mul(viewMatrix);
            vec.mul(projMatrix);

            if (vec.w() > 0) {
                float screenX = (vec.x() / vec.w() + 1.0f) * 0.5f * mc.getWindow().getGuiScaledWidth();
                float screenY = (1.0f - vec.y() / vec.w()) * 0.5f * mc.getWindow().getGuiScaledHeight();
                
                markersToRender.add(new ProjectedMarker(pointName, owner, isFinal, dist, screenX, screenY));
            }
        }
    }
}
