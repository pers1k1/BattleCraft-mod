package com.persiki84.zones.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.persiki84.shared.zone.ZoneArea;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class ZoneMeshes {
    private static final int MIN_SEGMENTS = 48;
    private static final int MAX_SEGMENTS = 512;
    private static final float RING_WIDTH = 2.4f;
    private static final float RING_LIFT = 0.06f;
    private static final float WALL_FOOT = 0.05f;
    private static final int GROUND_PROBE_DEPTH = 24;
    private static final int BUFFER_CAPACITY = 8192;
    private static final long RESHAPE_MS = 5000L;
    private static final int PROBE_POINTS = 8;

    private static final Map<String, Terrain> rings = new HashMap<>();
    private static final Map<String, Terrain> walls = new HashMap<>();

    private ZoneMeshes() {}

    public static VertexBuffer wall(Level level, String zoneId, ZoneArea area) {
        return shaped(walls, level, zoneId, area, false);
    }

    public static VertexBuffer ring(Level level, String zoneId, ZoneArea area) {
        return shaped(rings, level, zoneId, area, true);
    }

    public static void invalidate(String zoneId) {
        close(rings.remove(zoneId));
        close(walls.remove(zoneId));
    }

    public static void clear() {
        drop(rings);
        drop(walls);
    }

    private static void drop(Map<String, Terrain> cache) {
        Iterator<Terrain> iterator = cache.values().iterator();
        while (iterator.hasNext()) {
            iterator.next().buffer.close();
            iterator.remove();
        }
    }

    private static void close(Terrain owned) {
        if (owned != null) owned.buffer.close();
    }

    private static VertexBuffer shaped(Map<String, Terrain> cache, Level level, String zoneId,
                                       ZoneArea area, boolean ribbon) {
        Terrain cached = cache.get(zoneId);
        long signature = signatureOf(area);
        boolean kept = cached != null && cached.signature == signature;
        if (kept && cached.fresh()) return cached.buffer;

        int probe = probeOf(level, area);
        if (kept && cached.probe == probe) {
            cached.touch();
            return cached.buffer;
        }

        close(cached);
        VertexBuffer built = ribbon ? buildRing(level, area) : buildWall(level, area);
        cache.put(zoneId, new Terrain(built, signature, probe));
        return built;
    }

    private static long signatureOf(ZoneArea area) {
        long bits = Double.doubleToLongBits(area.size());
        bits = bits * 31 + area.center().asLong();
        bits = bits * 31 + area.shape().ordinal();
        bits = bits * 31 + Double.doubleToLongBits(area.heightUp());
        bits = bits * 31 + Double.doubleToLongBits(area.heightDown());
        return bits;
    }

    private static int probeOf(Level level, ZoneArea area) {
        int hash = 17;
        for (int point = 0; point < PROBE_POINTS; point++) {
            float along = point / (float) PROBE_POINTS;
            double offsetX = area.perimeterX(along) - area.centerX();
            double offsetZ = area.perimeterZ(along) - area.centerZ();
            hash = hash * 31 + Math.round(surfaceHeight(level, area, offsetX, offsetZ));
        }
        return hash;
    }

    private static int segmentsFor(ZoneArea area) {
        int wanted = (int) Math.round(area.perimeter());
        return Mth.clamp(wanted + 3, MIN_SEGMENTS, MAX_SEGMENTS) / 4 * 4;
    }

    private static VertexBuffer buildWall(Level level, ZoneArea area) {
        BufferBuilder builder = new BufferBuilder(BUFFER_CAPACITY);
        builder.begin(VertexFormat.Mode.QUADS, ZoneShaders.ZONE_FORMAT);

        int segments = segmentsFor(area);
        for (int segment = 0; segment < segments; segment++) {
            wallQuad(builder, level, area, segment / (float) segments, (segment + 1) / (float) segments);
        }
        return upload(builder);
    }

    private static void wallQuad(BufferBuilder builder, Level level, ZoneArea area,
                                 float startAlong, float endAlong) {
        double startX = area.perimeterX(startAlong) - area.centerX();
        double startZ = area.perimeterZ(startAlong) - area.centerZ();
        double endX = area.perimeterX(endAlong) - area.centerX();
        double endZ = area.perimeterZ(endAlong) - area.centerZ();

        float top = (float) area.heightUp();
        float startFoot = foot(level, area, startX, startZ, top);
        float endFoot = foot(level, area, endX, endZ, top);

        vertex(builder, (float) startX, startFoot, (float) startZ, 0.0f, startAlong);
        vertex(builder, (float) endX, endFoot, (float) endZ, 0.0f, endAlong);
        vertex(builder, (float) endX, top, (float) endZ, 1.0f, endAlong);
        vertex(builder, (float) startX, top, (float) startZ, 1.0f, startAlong);
    }

    private static float foot(Level level, ZoneArea area, double offsetX, double offsetZ, float top) {
        float ground = surfaceHeight(level, area, offsetX, offsetZ);
        float lowest = (float) -area.heightDown();
        return Mth.clamp(ground, lowest, top - WALL_FOOT);
    }

    private static VertexBuffer buildRing(Level level, ZoneArea area) {
        BufferBuilder builder = new BufferBuilder(BUFFER_CAPACITY);
        builder.begin(VertexFormat.Mode.QUADS, ZoneShaders.ZONE_FORMAT);

        int segments = segmentsFor(area);
        float half = RING_WIDTH * 0.5f;
        for (int segment = 0; segment < segments; segment++) {
            ringQuad(builder, level, area, segment / (float) segments, (segment + 1) / (float) segments, half);
        }
        return upload(builder);
    }

    private static void ringQuad(BufferBuilder builder, Level level, ZoneArea area,
                                 float startAlong, float endAlong, float half) {
        double startX = area.perimeterX(startAlong) - area.centerX();
        double startZ = area.perimeterZ(startAlong) - area.centerZ();
        double endX = area.perimeterX(endAlong) - area.centerX();
        double endZ = area.perimeterZ(endAlong) - area.centerZ();

        double startOutward = Math.max(0.0001, Math.hypot(startX, startZ));
        double endOutward = Math.max(0.0001, Math.hypot(endX, endZ));
        double startScaleIn = (startOutward - half) / startOutward;
        double startScaleOut = (startOutward + half) / startOutward;
        double endScaleIn = (endOutward - half) / endOutward;
        double endScaleOut = (endOutward + half) / endOutward;

        float startY = surfaceHeight(level, area, startX, startZ) + RING_LIFT;
        float endY = surfaceHeight(level, area, endX, endZ) + RING_LIFT;

        vertex(builder, (float) (startX * startScaleIn), startY, (float) (startZ * startScaleIn), 0.0f, startAlong);
        vertex(builder, (float) (endX * endScaleIn), endY, (float) (endZ * endScaleIn), 0.0f, endAlong);
        vertex(builder, (float) (endX * endScaleOut), endY, (float) (endZ * endScaleOut), 1.0f, endAlong);
        vertex(builder, (float) (startX * startScaleOut), startY, (float) (startZ * startScaleOut), 1.0f, startAlong);
    }

    private static float surfaceHeight(Level level, ZoneArea area, double offsetX, double offsetZ) {
        BlockPos probe = BlockPos.containing(area.centerX() + offsetX, area.center().getY(), area.centerZ() + offsetZ);
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, probe);
        return groundBelow(level, surface).getY() - area.center().getY();
    }

    private static BlockPos groundBelow(Level level, BlockPos from) {
        BlockPos.MutableBlockPos cursor = from.mutable();
        for (int step = 0; step < GROUND_PROBE_DEPTH; step++) {
            cursor.move(0, -1, 0);
            if (level.getBlockState(cursor).isSolidRender(level, cursor)) return cursor.above().immutable();
        }
        return from;
    }

    private static void vertex(BufferBuilder builder, float x, float y, float z,
                               float heightFactor, float angularCoord) {
        builder.vertex(x, y, z).uv(heightFactor, angularCoord).endVertex();
    }

    private static VertexBuffer upload(BufferBuilder builder) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(builder.end());
        VertexBuffer.unbind();
        return buffer;
    }

    private static final class Terrain {
        private final VertexBuffer buffer;
        private final long signature;
        private final int probe;
        private long checkedAt;

        private Terrain(VertexBuffer buffer, long signature, int probe) {
            this.buffer = buffer;
            this.signature = signature;
            this.probe = probe;
            this.checkedAt = System.currentTimeMillis();
        }

        private boolean fresh() {
            return System.currentTimeMillis() - checkedAt < RESHAPE_MS;
        }

        private void touch() {
            checkedAt = System.currentTimeMillis();
        }
    }
}
