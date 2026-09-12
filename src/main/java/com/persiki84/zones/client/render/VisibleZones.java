package com.persiki84.zones.client.render;

import com.persiki84.capturepoints.client.ClientCaptureData;
import com.persiki84.shared.zone.ZoneArea;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneSource;
import com.persiki84.zones.ZoneType;
import com.persiki84.zones.client.ClientZoneData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VisibleZones {
    private static final List<Zone> visible = new ArrayList<>();
    private static final Map<String, Zone> derivedPoints = new HashMap<>();

    private VisibleZones() {}

    public static List<Zone> current() {
        return visible;
    }

    public static void refresh() {
        visible.clear();

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Entity camera = minecraft.getCameraEntity();
        if (level == null || camera == null || !matchRunning()) return;

        double limit = minecraft.options.getEffectiveRenderDistance() * 16.0;
        double limitSquared = limit * limit;

        boolean sheltered = sheltered(camera);
        for (Zone zone : ClientZoneData.all()) {
            if (zone.type() == ZoneType.SHOP && (!sheltered || !ZoneColors.visibleToOwnTeam(zone))) continue;
            collect(zone, level, camera, limitSquared);
        }
        for (Zone zone : capturePointZones()) {
            collect(zone, level, camera, limitSquared);
        }
    }

    private static void collect(Zone zone, ClientLevel level, Entity camera, double limitSquared) {
        ZoneArea area = zone.area();
        double dx = area.centerX() - camera.getX();
        double dz = area.centerZ() - camera.getZ();
        if (dx * dx + dz * dz > limitSquared) return;
        if (!chunksLoaded(level, area)) return;

        visible.add(zone);
    }

    public static boolean matchRunning() {
        return com.persiki84.battlecraft.client.ClientGameData.isSoftDisabled()
                || com.persiki84.battlecraft.client.ClientGameData.getCurrentPhase()
                        == com.persiki84.battlecraft.BattleCraftManager.GamePhase.ACTIVE;
    }

    public static boolean sheltered(Entity camera) {
        if (camera == null) return false;
        for (Zone zone : ClientZoneData.all()) {
            if (zone.type() != ZoneType.BASE) continue;
            if (zone.area().contains(camera.getX(), camera.getY(), camera.getZ())) return true;
        }
        return false;
    }

    private static boolean chunksLoaded(ClientLevel level, ZoneArea area) {
        AABB bounds = area.bounds();
        if (!chunkLoaded(level, area.centerX(), area.centerZ())) return false;
        if (!chunkLoaded(level, bounds.minX, bounds.minZ)) return false;
        if (!chunkLoaded(level, bounds.minX, bounds.maxZ)) return false;
        if (!chunkLoaded(level, bounds.maxX, bounds.minZ)) return false;
        return chunkLoaded(level, bounds.maxX, bounds.maxZ);
    }

    private static boolean chunkLoaded(ClientLevel level, double x, double z) {
        int chunkX = SectionPos.blockToSectionCoord(BlockPos.containing(x, 0.0, z).getX());
        int chunkZ = SectionPos.blockToSectionCoord(BlockPos.containing(x, 0.0, z).getZ());
        return level.getChunkSource().hasChunk(chunkX, chunkZ);
    }

    private static List<Zone> capturePointZones() {
        List<Zone> result = new ArrayList<>();
        appendPoints(result, ClientCaptureData.getAllPointOwners(), false);
        if (ClientCaptureData.areAllPointsCapturedBySameTeam()) {
            appendPoints(result, ClientCaptureData.getAllFinalPointOwners(), true);
        }
        return result;
    }

    private static void appendPoints(List<Zone> target, Map<String, String> owners, boolean finalPoints) {
        for (Map.Entry<String, String> entry : owners.entrySet()) {
            String name = entry.getKey();
            ZoneArea area = finalPoints ? ClientCaptureData.getFinalPointArea(name) : ClientCaptureData.getPointArea(name);
            if (area == null) continue;

            Zone zone = derivedPoints.computeIfAbsent(name, id ->
                    new Zone(id, area, ZoneType.CAPTURE_POINT, entry.getValue(), Zone.TEAM_COLOR, ZoneSource.CAPTURE_POINT));
            zone.setArea(area);
            zone.setOwnerTeam(entry.getValue());
            target.add(zone);
        }
    }

    public static void forget(String zoneId) {
        derivedPoints.remove(zoneId);
        visible.removeIf(zone -> zone.id().equals(zoneId));
        ZoneMeshes.invalidate(zoneId);
        ZoneMarkers.forget(zoneId);
        ZoneColors.forget(zoneId);
    }

    public static void reset() {
        visible.clear();
        derivedPoints.clear();
        ZoneMeshes.clear();
        ZoneMarkers.reset();
    }
}
