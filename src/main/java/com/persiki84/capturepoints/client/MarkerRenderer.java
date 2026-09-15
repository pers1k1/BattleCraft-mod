package com.persiki84.capturepoints.client;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.client.ClientGameData;
import com.persiki84.battlecraft.client.ClientMarkerRanges;
import com.persiki84.battlecraft.rules.MarkerRange;
import com.persiki84.capturepoints.CapturePointsMod;
import com.persiki84.minimap.client.ClientMapData;
import com.persiki84.minimap.network.MapMarkerSyncPacket;
import com.persiki84.shared.client.ui.UiAccent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
public final class MarkerRenderer {
    private static final String PLAYER_PREFIX = "player:";
    private static final double POINT_LIFT = 1.5;

    private static final List<ProjectedMarker> visible = new ArrayList<>();
    private static final Vector4f scratch = new Vector4f();

    private static float screenWidth;
    private static float screenHeight;

    private MarkerRenderer() {}

    public static List<ProjectedMarker> visible() {
        return visible;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        visible.clear();
        if (!ClientCaptureData.isLocalMarkersEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        screenWidth = mc.getWindow().getGuiScaledWidth();
        screenHeight = mc.getWindow().getGuiScaledHeight();

        Vec3 camera = event.getCamera().getPosition();
        Matrix4f view = event.getPoseStack().last().pose();
        Matrix4f projection = event.getProjectionMatrix();

        projectPoints(camera, view, projection);
        projectPlayerMarkers(mc, camera, view, projection);
    }

    private static void projectPoints(Vec3 camera, Matrix4f view, Matrix4f projection) {
        boolean running = !ClientGameData.isSoftDisabled()
                && ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.ACTIVE;
        if (!running) return;

        if (ClientCaptureData.isServerCaptureMarkers()) {
            projectPointRow(camera, view, projection, ClientCaptureData.getAllPointOwners(), false);
        }
        if (ClientCaptureData.isServerFinalMarkers() && ClientCaptureData.areAllPointsCapturedBySameTeam()) {
            projectPointRow(camera, view, projection, ClientCaptureData.getAllFinalPointOwners(), true);
        }
    }

    private static void projectPointRow(Vec3 camera, Matrix4f view, Matrix4f projection,
                                        Map<String, String> points, boolean isFinal) {
        double range = ClientMarkerRanges.blocks(MarkerRange.POINTS);

        for (Map.Entry<String, String> entry : points.entrySet()) {
            String name = entry.getKey();
            if (!ClientCaptureData.isPointShownInHud(name)) continue;

            BlockPos pos = isFinal
                    ? ClientCaptureData.getFinalPointPosition(name)
                    : ClientCaptureData.getPointPosition(name);
            if (pos == null) continue;

            double distance = project(pos.getX() + 0.5, pos.getY() + POINT_LIFT, pos.getZ() + 0.5,
                    camera, view, projection);
            if (distance < 0.0) continue;

            visible.add(new ProjectedMarker(name, name, entry.getValue(), isFinal, distance <= range,
                    distance, screenX(), screenY(), null));
        }
    }

    private static void projectPlayerMarkers(Minecraft mc, Vec3 camera, Matrix4f view, Matrix4f projection) {
        String self = mc.player.getScoreboardName();
        double range = ClientMarkerRanges.blocks(MarkerRange.PLAYERS);

        ResourceLocation here = mc.level == null ? null : mc.level.dimension().location();
        for (MapMarkerSyncPacket.MarkerData marker : ClientMapData.getMarkers()) {
            boolean own = marker.playerName.equals(self);
            if (!ClientMapData.showOtherMarkers && !own) continue;
            if (!marker.dimension.equals(here)) continue;

            double distance = project(marker.x, marker.y, marker.z, camera, view, projection);
            if (distance < 0.0) continue;

            String label = !marker.isTeam && own ? null : marker.playerName;
            visible.add(new ProjectedMarker(key(marker), label, null, false, distance <= range,
                    distance, screenX(), screenY(), UiAccent.color()));
        }
    }

    // WHY: у своей личной метки нет подписи, а присутствие держится по ключу: без своего ключа
    // WHY: она делила бы состояние с командной меткой того же игрока
    private static String key(MapMarkerSyncPacket.MarkerData marker) {
        return PLAYER_PREFIX + marker.playerId + (marker.isTeam ? ":team" : ":private");
    }

    // WHY: дальность больше не отсеивает метку здесь: снятая с кадра уходила рывком, а плавное
    // WHY: угасание считает худ по признаку inRange
    private static double project(double x, double y, double z, Vec3 camera, Matrix4f view, Matrix4f projection) {
        double dx = x - camera.x;
        double dy = y - camera.y;
        double dz = z - camera.z;

        scratch.set((float) dx, (float) dy, (float) dz, 1.0f);
        scratch.mul(view);
        scratch.mul(projection);
        if (scratch.w() <= 0.0f) return -1.0;

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float screenX() {
        return (scratch.x() / scratch.w() + 1.0f) * 0.5f * screenWidth;
    }

    private static float screenY() {
        return (1.0f - scratch.y() / scratch.w()) * 0.5f * screenHeight;
    }
}
