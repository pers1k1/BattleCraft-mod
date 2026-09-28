package com.persiki84.shared.client.ui;

import org.joml.Matrix4f;

public final class UiCover {
    private static final int MAX_AREAS = 24;
    private static final int LEFT = 0;
    private static final int TOP = 1;
    private static final int RIGHT = 2;
    private static final int BOTTOM = 3;
    private static final float REACH = 16.0f;
    private static final float FLAT_EPSILON = 1.0e-6f;

    private static final float[] AREAS = new float[MAX_AREAS * 4];
    private static final float[] PROBE = new float[4];

    private static int count;
    private static boolean everywhere;

    private UiCover() {}

    public static void clear() {
        count = 0;
        everywhere = false;
    }

    public static void flood() {
        everywhere = true;
    }

    public static void add(Matrix4f pose, float x, float y, float width, float height) {
        if (everywhere || width <= 0.0f || height <= 0.0f) return;
        if (!project(pose, x, y, width, height)) {
            everywhere = true;
            return;
        }
        if (count == MAX_AREAS) {
            widen((count - 1) * 4);
            return;
        }
        System.arraycopy(PROBE, 0, AREAS, count * 4, 4);
        count++;
    }

    // WHY: стекло сэмплит подложку и за своим краем: линза тянет её на ширину пояса, а блюр пирамиды
    // WHY: растаскивает соседние пиксели, поэтому слой под ним считается задетым с запасом REACH
    public static boolean meets(Matrix4f pose, float x, float y, float width, float height) {
        if (everywhere) return true;
        if (count == 0) return false;
        if (!project(pose, x - REACH, y - REACH, width + REACH * 2.0f, height + REACH * 2.0f)) return true;

        for (int index = 0; index < count; index++) {
            if (overlaps(index * 4)) return true;
        }
        return false;
    }

    private static boolean overlaps(int base) {
        return PROBE[LEFT] < AREAS[base + RIGHT] && AREAS[base + LEFT] < PROBE[RIGHT]
                && PROBE[TOP] < AREAS[base + BOTTOM] && AREAS[base + TOP] < PROBE[BOTTOM];
    }

    private static void widen(int base) {
        AREAS[base + LEFT] = Math.min(AREAS[base + LEFT], PROBE[LEFT]);
        AREAS[base + TOP] = Math.min(AREAS[base + TOP], PROBE[TOP]);
        AREAS[base + RIGHT] = Math.max(AREAS[base + RIGHT], PROBE[RIGHT]);
        AREAS[base + BOTTOM] = Math.max(AREAS[base + BOTTOM], PROBE[BOTTOM]);
    }

    // WHY: перспективная поза (панель на плоскости в мире) в прямоугольник экрана не сводится, такой
    // WHY: слой отвечает, что не проецируется, и считается покрывающим всё
    private static boolean project(Matrix4f pose, float x, float y, float width, float height) {
        if (Math.abs(pose.m03()) > FLAT_EPSILON || Math.abs(pose.m13()) > FLAT_EPSILON
                || Math.abs(pose.m33() - 1.0f) > FLAT_EPSILON) return false;

        float acrossX = pose.m00() * width;
        float downX = pose.m10() * height;
        float acrossY = pose.m01() * width;
        float downY = pose.m11() * height;
        float originX = pose.m00() * x + pose.m10() * y + pose.m30();
        float originY = pose.m01() * x + pose.m11() * y + pose.m31();
        PROBE[LEFT] = originX + Math.min(0.0f, acrossX) + Math.min(0.0f, downX);
        PROBE[RIGHT] = originX + Math.max(0.0f, acrossX) + Math.max(0.0f, downX);
        PROBE[TOP] = originY + Math.min(0.0f, acrossY) + Math.min(0.0f, downY);
        PROBE[BOTTOM] = originY + Math.max(0.0f, acrossY) + Math.max(0.0f, downY);
        return true;
    }
}
