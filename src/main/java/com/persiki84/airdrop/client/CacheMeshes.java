package com.persiki84.airdrop.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

// WHY: геометрия тайника неподвижна и печётся один раз единичной: размер и место задают юниформы,
// WHY: поэтому двойной сундук и бочка рисуются одним и тем же буфером без вершин в кадре
public final class CacheMeshes {
    private static final int RING_SEGMENTS = 96;
    private static final float RING_INNER = 0.47f;
    private static final float RING_OUTER = 0.5f;
    private static final int SHELL_RINGS = 12;
    private static final int SHELL_SEGMENTS = 24;
    private static final int BUFFER_CAPACITY = 8192;
    private static final float HALF = 0.5f;

    private static VertexBuffer cage;
    private static VertexBuffer floor;
    private static VertexBuffer ring;
    private static VertexBuffer shell;

    private CacheMeshes() {}

    public static VertexBuffer cage() {
        if (cage == null) cage = buildCage();
        return cage;
    }

    public static VertexBuffer floor() {
        if (floor == null) floor = buildFloor();
        return floor;
    }

    public static VertexBuffer ring() {
        if (ring == null) ring = buildRing();
        return ring;
    }

    public static VertexBuffer shell() {
        if (shell == null) shell = buildShell();
        return shell;
    }

    private static VertexBuffer buildCage() {
        BufferBuilder builder = begin(VertexFormat.Mode.QUADS);
        face(builder, -HALF, -HALF, -HALF, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        face(builder, -HALF, -HALF, HALF, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        face(builder, -HALF, -HALF, -HALF, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f);
        face(builder, HALF, -HALF, -HALF, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f);
        face(builder, -HALF, -HALF, -HALF, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        face(builder, -HALF, HALF, -HALF, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        return upload(builder);
    }

    private static VertexBuffer buildFloor() {
        BufferBuilder builder = begin(VertexFormat.Mode.QUADS);
        face(builder, -HALF, 0.0f, -HALF, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        return upload(builder);
    }

    private static void face(BufferBuilder builder, float originX, float originY, float originZ,
                             float spanX, float spanY, float spanZ, float riseX, float riseY, float riseZ) {
        vertex(builder, originX, originY, originZ, 0.0f, 0.0f);
        vertex(builder, originX + spanX, originY + spanY, originZ + spanZ, 1.0f, 0.0f);
        vertex(builder, originX + spanX + riseX, originY + spanY + riseY, originZ + spanZ + riseZ, 1.0f, 1.0f);
        vertex(builder, originX + riseX, originY + riseY, originZ + riseZ, 0.0f, 1.0f);
    }

    private static VertexBuffer buildRing() {
        BufferBuilder builder = begin(VertexFormat.Mode.QUADS);
        for (int segment = 0; segment < RING_SEGMENTS; segment++) {
            ringQuad(builder, segment / (float) RING_SEGMENTS, (segment + 1) / (float) RING_SEGMENTS);
        }
        return upload(builder);
    }

    private static void ringQuad(BufferBuilder builder, float startAlong, float endAlong) {
        float startAngle = (float) (startAlong * Math.PI * 2.0);
        float endAngle = (float) (endAlong * Math.PI * 2.0);
        float startCos = (float) Math.cos(startAngle);
        float startSin = (float) Math.sin(startAngle);
        float endCos = (float) Math.cos(endAngle);
        float endSin = (float) Math.sin(endAngle);

        vertex(builder, startCos * RING_INNER, 0.0f, startSin * RING_INNER, 0.0f, startAlong);
        vertex(builder, endCos * RING_INNER, 0.0f, endSin * RING_INNER, 0.0f, endAlong);
        vertex(builder, endCos * RING_OUTER, 0.0f, endSin * RING_OUTER, 1.0f, endAlong);
        vertex(builder, startCos * RING_OUTER, 0.0f, startSin * RING_OUTER, 1.0f, startAlong);
    }

    private static VertexBuffer buildShell() {
        BufferBuilder builder = begin(VertexFormat.Mode.QUADS);
        for (int band = 0; band < SHELL_RINGS; band++) {
            float top = (float) (Math.PI * band / SHELL_RINGS);
            float bottom = (float) (Math.PI * (band + 1) / SHELL_RINGS);
            for (int segment = 0; segment < SHELL_SEGMENTS; segment++) {
                float left = (float) (Math.PI * 2.0 * segment / SHELL_SEGMENTS);
                float right = (float) (Math.PI * 2.0 * (segment + 1) / SHELL_SEGMENTS);
                shellVertex(builder, top, left);
                shellVertex(builder, bottom, left);
                shellVertex(builder, bottom, right);
                shellVertex(builder, top, right);
            }
        }
        return upload(builder);
    }

    private static void shellVertex(BufferBuilder builder, float polar, float around) {
        float band = (float) Math.sin(polar);
        float x = (float) (band * Math.cos(around)) * HALF;
        float y = (float) Math.cos(polar) * HALF;
        float z = (float) (band * Math.sin(around)) * HALF;
        vertex(builder, x, y, z, (float) (around / (Math.PI * 2.0)), (float) (polar / Math.PI));
    }

    private static void vertex(BufferBuilder builder, float x, float y, float z, float u, float v) {
        builder.vertex(x, y, z).uv(u, v).endVertex();
    }

    private static BufferBuilder begin(VertexFormat.Mode mode) {
        BufferBuilder builder = new BufferBuilder(BUFFER_CAPACITY);
        builder.begin(mode, CacheShaders.FORMAT);
        return builder;
    }

    private static VertexBuffer upload(BufferBuilder builder) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(builder.end());
        VertexBuffer.unbind();
        return buffer;
    }
}
