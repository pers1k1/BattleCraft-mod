package com.persiki84.quarrymod.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

// WHY: вся геометрия карьера неподвижна и печётся один раз: анимацию ведут юниформы, поэтому
// WHY: в кадре нет ни одной построенной вершины, сколько бы выработок ни было в поле зрения
public final class QuarryMeshes {
    private static final int RING_SEGMENTS = 96;
    private static final float RING_INNER = 0.335f;
    private static final float RING_OUTER = 0.365f;
    private static final int SHELL_RINGS = 12;
    private static final int SHELL_SEGMENTS = 24;
    private static final int BUFFER_CAPACITY = 8192;

    private static VertexBuffer cage;
    private static VertexBuffer core;
    private static VertexBuffer ring;
    private static VertexBuffer shell;

    private QuarryMeshes() {}

    public static VertexBuffer cage() {
        if (cage == null) cage = buildCage();
        return cage;
    }

    public static VertexBuffer core() {
        if (core == null) core = buildCore();
        return core;
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
        float half = 0.5f;

        face(builder, -half, -half, -half, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        face(builder, -half, -half, half, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        face(builder, -half, -half, -half, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f);
        face(builder, half, -half, -half, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f);
        face(builder, -half, -half, -half, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        face(builder, -half, half, -half, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        return upload(builder);
    }

    private static void face(BufferBuilder builder, float originX, float originY, float originZ,
                             float spanX, float spanY, float spanZ,
                             float riseX, float riseY, float riseZ) {
        vertex(builder, originX, originY, originZ, 0.0f, 0.0f);
        vertex(builder, originX + spanX, originY + spanY, originZ + spanZ, 1.0f, 0.0f);
        vertex(builder, originX + spanX + riseX, originY + spanY + riseY, originZ + spanZ + riseZ, 1.0f, 1.0f);
        vertex(builder, originX + riseX, originY + riseY, originZ + riseZ, 0.0f, 1.0f);
    }

    private static VertexBuffer buildCore() {
        BufferBuilder builder = begin(VertexFormat.Mode.TRIANGLES);
        float[][] points = {
                {1.0f, 0.0f, 0.0f}, {-1.0f, 0.0f, 0.0f},
                {0.0f, 1.0f, 0.0f}, {0.0f, -1.0f, 0.0f},
                {0.0f, 0.0f, 1.0f}, {0.0f, 0.0f, -1.0f}
        };
        int[][] faces = {
                {0, 2, 4}, {2, 1, 4}, {1, 3, 4}, {3, 0, 4},
                {2, 0, 5}, {1, 2, 5}, {3, 1, 5}, {0, 3, 5}
        };

        for (int[] face : faces) {
            divide(builder, points[face[0]], points[face[1]], points[face[2]]);
        }
        return upload(builder);
    }

    private static void divide(BufferBuilder builder, float[] first, float[] second, float[] third) {
        float[] firstMid = middle(first, second);
        float[] secondMid = middle(second, third);
        float[] thirdMid = middle(third, first);

        triangle(builder, first, firstMid, thirdMid);
        triangle(builder, firstMid, second, secondMid);
        triangle(builder, thirdMid, secondMid, third);
        triangle(builder, firstMid, secondMid, thirdMid);
    }

    private static float[] middle(float[] first, float[] second) {
        return normalize(new float[] {
                (first[0] + second[0]) * 0.5f,
                (first[1] + second[1]) * 0.5f,
                (first[2] + second[2]) * 0.5f
        });
    }

    private static float[] normalize(float[] point) {
        float length = (float) Math.sqrt(point[0] * point[0] + point[1] * point[1] + point[2] * point[2]);
        if (length < 0.0001f) return point;

        point[0] /= length;
        point[1] /= length;
        point[2] /= length;
        return point;
    }

    private static void triangle(BufferBuilder builder, float[] first, float[] second, float[] third) {
        float radius = 0.5f;
        vertex(builder, first[0] * radius, first[1] * radius, first[2] * radius, 0.0f, 0.0f);
        vertex(builder, second[0] * radius, second[1] * radius, second[2] * radius, 1.0f, 0.0f);
        vertex(builder, third[0] * radius, third[1] * radius, third[2] * radius, 0.5f, 1.0f);
    }

    private static VertexBuffer buildRing() {
        BufferBuilder builder = begin(VertexFormat.Mode.QUADS);
        for (int segment = 0; segment < RING_SEGMENTS; segment++) {
            float startAlong = segment / (float) RING_SEGMENTS;
            float endAlong = (segment + 1) / (float) RING_SEGMENTS;
            ringQuad(builder, startAlong, endAlong);
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
            float topAngle = (float) (Math.PI * band / SHELL_RINGS);
            float bottomAngle = (float) (Math.PI * (band + 1) / SHELL_RINGS);
            for (int segment = 0; segment < SHELL_SEGMENTS; segment++) {
                float leftAngle = (float) (Math.PI * 2.0 * segment / SHELL_SEGMENTS);
                float rightAngle = (float) (Math.PI * 2.0 * (segment + 1) / SHELL_SEGMENTS);
                shellQuad(builder, topAngle, bottomAngle, leftAngle, rightAngle);
            }
        }
        return upload(builder);
    }

    private static void shellQuad(BufferBuilder builder, float topAngle, float bottomAngle,
                                  float leftAngle, float rightAngle) {
        shellVertex(builder, topAngle, leftAngle);
        shellVertex(builder, bottomAngle, leftAngle);
        shellVertex(builder, bottomAngle, rightAngle);
        shellVertex(builder, topAngle, rightAngle);
    }

    private static void shellVertex(BufferBuilder builder, float polar, float around) {
        float radius = 0.5f;
        float ring = (float) Math.sin(polar);
        float x = (float) (ring * Math.cos(around)) * radius;
        float y = (float) Math.cos(polar) * radius;
        float z = (float) (ring * Math.sin(around)) * radius;
        vertex(builder, x, y, z, (float) (around / (Math.PI * 2.0)), (float) (polar / Math.PI));
    }

    private static void vertex(BufferBuilder builder, float x, float y, float z, float u, float v) {
        builder.vertex(x, y, z).uv(u, v).endVertex();
    }

    private static BufferBuilder begin(VertexFormat.Mode mode) {
        BufferBuilder builder = new BufferBuilder(BUFFER_CAPACITY);
        builder.begin(mode, QuarryShaders.FIELD_FORMAT);
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
