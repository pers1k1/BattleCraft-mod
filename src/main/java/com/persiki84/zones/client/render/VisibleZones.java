package com.persiki84.zones.client.render;

import com.persiki84.capturepoints.client.ClientCaptureData;
import com.persiki84.shared.zone.ZoneArea;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneSource;
import com.persiki84.zones.ZoneType;
import com.persiki84.zones.client.ClientMarkData;
import com.persiki84.zones.client.ClientZoneData;
import com.persiki84.zones.mark.MapMark;
import com.persiki84.zones.mark.MarkHideZone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class VisibleZones {
    private static final String POINT_KEY = "point:";
    private static final String FINAL_KEY = "final:";
    private static final String MARK_KEY = "mark:";

    private static final List<Zone> visible = new ArrayList<>();
    private static final List<Zone> markVolumes = new ArrayList<>();
    private static final Map<String, Zone> derivedPoints = new HashMap<>();
    private static final Map<String, Zone> derivedFinals = new HashMap<>();
    private static final Map<String, MarkVolume> derivedMarks = new HashMap<>();

    private VisibleZones() {}

    public static List<Zone> current() {
        return visible;
    }

    // WHY: объём метки только рисуется: в current() он попал бы в ZoneOccupancy и выдал бы
    // WHY: игроку внутри него подсказку захвата, как будто это точка
    public static List<Zone> markVolumes() {
        return markVolumes;
    }

    public static void refresh() {
        visible.clear();
        markVolumes.clear();

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Entity camera = minecraft.getCameraEntity();
        if (level == null || camera == null) return;

        double limit = minecraft.options.getEffectiveRenderDistance() * 16.0;
        double limitSquared = limit * limit;
        ResourceLocation here = level.dimension().location();
        collectMarks(level, camera, here, limitSquared);
        if (!matchRunning()) return;

        boolean sheltered = sheltered(camera);
        for (Zone zone : ClientZoneData.all()) {
            if (!zone.inDimension(here)) continue;
            if (zone.type() == ZoneType.SHOP && (!sheltered || !ZoneColors.visibleToOwnTeam(zone))) continue;
            collect(visible, zone, level, camera, limitSquared);
        }
        collectPoints(ClientCaptureData.getAllPointOwners(), false, level, camera, limitSquared);
        if (ClientCaptureData.areAllPointsCapturedBySameTeam()) {
            collectPoints(ClientCaptureData.getAllFinalPointOwners(), true, level, camera, limitSquared);
        }
    }

    private static void collect(List<Zone> target, Zone zone, ClientLevel level, Entity camera, double limitSquared) {
        ZoneArea area = zone.area();
        double dx = area.centerX() - camera.getX();
        double dz = area.centerZ() - camera.getZ();
        if (dx * dx + dz * dz > limitSquared) return;
        if (!chunksLoaded(level, area)) return;

        target.add(zone);
    }

    public static boolean matchRunning() {
        return com.persiki84.battlecraft.client.ClientGameData.isSoftDisabled()
                || com.persiki84.battlecraft.client.ClientGameData.getCurrentPhase()
                        == com.persiki84.battlecraft.BattleCraftManager.GamePhase.ACTIVE;
    }

    public static boolean sheltered(Entity camera) {
        if (camera == null) return false;
        ResourceLocation here = camera.level().dimension().location();
        for (Zone zone : ClientZoneData.all()) {
            if (zone.type() != ZoneType.BASE || !zone.inDimension(here)) continue;
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
        int chunkX = SectionPos.blockToSectionCoord(Math.floor(x));
        int chunkZ = SectionPos.blockToSectionCoord(Math.floor(z));
        return level.getChunkSource().hasChunk(chunkX, chunkZ);
    }

    private static void collectPoints(Map<String, String> owners, boolean finalPoints,
                                      ClientLevel level, Entity camera, double limitSquared) {
        for (Map.Entry<String, String> entry : owners.entrySet()) {
            String name = entry.getKey();
            ZoneArea area = finalPoints ? ClientCaptureData.getFinalPointArea(name) : ClientCaptureData.getPointArea(name);
            if (area == null) continue;

            Zone zone = derivedPoint(name, area, entry.getValue(), finalPoints);
            collect(visible, zone, level, camera, limitSquared);
        }
    }

    private static Zone derivedPoint(String name, ZoneArea area, String owner, boolean finalPoint) {
        Map<String, Zone> derived = finalPoint ? derivedFinals : derivedPoints;
        Zone zone = derived.get(name);
        if (zone == null) {
            zone = new Zone(name, area, ZoneType.CAPTURE_POINT, owner, Zone.TEAM_COLOR, ZoneSource.CAPTURE_POINT);
            zone.setCacheKey((finalPoint ? FINAL_KEY : POINT_KEY) + name);
            derived.put(name, zone);
        }
        zone.setArea(area);
        zone.setOwnerTeam(owner);
        return zone;
    }

    private static void collectMarks(ClientLevel level, Entity camera, ResourceLocation here, double limitSquared) {
        pruneMarks();
        for (MapMark mark : ClientMarkData.all()) {
            if (!mark.hideZone().drawn() || !here.equals(mark.dimension())) continue;
            collect(markVolumes, derivedMark(mark), level, camera, limitSquared);
        }
    }

    // WHY: синхронизация меток подменяет объекты целиком, поэтому зона пересобирается по смене
    // WHY: ссылки на метку, а не каждый тик
    private static Zone derivedMark(MapMark mark) {
        MarkVolume volume = derivedMarks.get(mark.id());
        if (volume != null && volume.source == mark) return volume.zone;

        MarkHideZone hide = mark.hideZone();
        Zone zone = new Zone(mark.id(), hide.area(mark.position()), ZoneType.BASE, null,
                hide.colorFor(mark) | 0xFF000000, ZoneSource.MARK);
        zone.setCacheKey(MARK_KEY + mark.id());
        zone.setDimension(mark.dimension());
        derivedMarks.put(mark.id(), new MarkVolume(mark, zone));
        return zone;
    }

    private static void pruneMarks() {
        Iterator<Map.Entry<String, MarkVolume>> iterator = derivedMarks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, MarkVolume> entry = iterator.next();
            MapMark mark = ClientMarkData.byId(entry.getKey());
            if (mark != null && mark.hideZone().drawn()) continue;

            String key = entry.getValue().zone.cacheKey();
            ZoneMeshes.invalidate(key);
            ZoneColors.forget(key);
            iterator.remove();
        }
    }

    public static void forget(String zoneId) {
        visible.removeIf(zone -> zone.cacheKey().equals(zoneId));
        ZoneMeshes.invalidate(zoneId);
        ZoneMarkers.forget(zoneId);
        ZoneColors.forget(zoneId);
    }

    public static void reset() {
        visible.clear();
        markVolumes.clear();
        derivedPoints.clear();
        derivedFinals.clear();
        derivedMarks.clear();
        ZonePresence.reset();
        ZoneMeshes.clear();
        ZoneMarkers.reset();
    }

    private record MarkVolume(MapMark source, Zone zone) {}
}
