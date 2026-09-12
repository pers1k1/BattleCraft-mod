package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.capture.CaptureMode;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.shared.zone.ZoneArea;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public class PointSyncData {
    private static final int MAX_POINTS = 512;
    private static final int NAME_LIMIT = 128;

    public final String owner;
    public final ZoneArea area;
    public final ResourceLocation dimension;
    public final CaptureMode mode;

    public PointSyncData(String owner, ZoneArea area, ResourceLocation dimension, CaptureMode mode) {
        this.owner = owner;
        this.area = area;
        this.dimension = dimension;
        this.mode = mode;
    }

    public static PointSyncData of(CapturePoint point) {
        return new PointSyncData(point.getOwnerTeam(), point.getArea(),
                point.getDimension().location(), point.getMode());
    }

    public BlockPos pos() {
        return area.center();
    }

    // WHY: пакеты обычных и финальных точек различались только именем класса и вызовом на клиенте,
    // WHY: поэтому сам формат живёт здесь и правится в одном месте
    public static void writeMap(net.minecraft.network.FriendlyByteBuf buf,
                                java.util.Map<String, PointSyncData> points) {
        buf.writeVarInt(points.size());
        for (java.util.Map.Entry<String, PointSyncData> entry : points.entrySet()) {
            PointSyncData point = entry.getValue();
            buf.writeUtf(entry.getKey());
            buf.writeUtf(point.owner == null ? "" : point.owner);
            point.area.write(buf);
            buf.writeResourceLocation(point.dimension);
            buf.writeUtf(point.mode.id());
        }
    }

    public static java.util.Map<String, PointSyncData> readMap(net.minecraft.network.FriendlyByteBuf buf) {
        int size = Math.min(Math.max(buf.readVarInt(), 0), MAX_POINTS);
        java.util.Map<String, PointSyncData> points = new java.util.HashMap<>();
        for (int index = 0; index < size; index++) {
            String name = buf.readUtf(NAME_LIMIT);
            String owner = buf.readUtf(NAME_LIMIT);
            ZoneArea area = ZoneArea.read(buf);
            ResourceLocation dimension = buf.readResourceLocation();
            CaptureMode mode = CaptureMode.byId(buf.readUtf(NAME_LIMIT));
            points.put(name, new PointSyncData(owner.isEmpty() ? null : owner, area, dimension, mode));
        }
        return points;
    }
}
