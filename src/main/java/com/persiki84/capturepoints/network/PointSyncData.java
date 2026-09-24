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
    public final boolean required;
    public final boolean shownInHud;
    public final boolean hiddenInside;
    public final int markerRange;

    public PointSyncData(String owner, ZoneArea area, ResourceLocation dimension, CaptureMode mode,
                         boolean required, boolean shownInHud, boolean hiddenInside, int markerRange) {
        this.owner = owner;
        this.area = area;
        this.dimension = dimension;
        this.mode = mode;
        this.required = required;
        this.shownInHud = shownInHud;
        this.hiddenInside = hiddenInside;
        this.markerRange = markerRange;
    }

    public static PointSyncData of(CapturePoint point) {
        return new PointSyncData(point.getOwnerTeam(), point.getArea(),
                point.getDimension().location(), point.getMode(),
                point.isRequired(), point.isShownInHud(), point.isHiddenInside(), point.getMarkerRange());
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
            buf.writeBoolean(point.required);
            buf.writeBoolean(point.shownInHud);
            buf.writeBoolean(point.hiddenInside);
            buf.writeVarInt(point.markerRange);
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
            boolean required = buf.readBoolean();
            boolean shownInHud = buf.readBoolean();
            boolean hiddenInside = buf.readBoolean();
            int markerRange = buf.readVarInt();
            points.put(name, new PointSyncData(owner.isEmpty() ? null : owner, area, dimension, mode,
                    required, shownInHud, hiddenInside, markerRange));
        }
        return points;
    }
}
