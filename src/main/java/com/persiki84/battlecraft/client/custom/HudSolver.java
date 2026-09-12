package com.persiki84.battlecraft.client.custom;

final class HudSolver {
    private static final int COUNT = HudSlot.values().length;

    private static final float[] left = new float[COUNT];
    private static final float[] top = new float[COUNT];
    private static final float[] width = new float[COUNT];
    private static final float[] height = new float[COUNT];
    private static final float[] pushX = new float[COUNT];
    private static final float[] pushY = new float[COUNT];
    private static final boolean[] live = new boolean[COUNT];
    private static final int[] rank = new int[COUNT];
    private static final int[] bound = new int[COUNT];
    private static final float[] keepOut = new float[COUNT];

    private static float roomWidth;
    private static float roomHeight;

    private HudSolver() {}

    static void room(float screenWidth, float screenHeight) {
        roomWidth = screenWidth;
        roomHeight = screenHeight;
    }

    static void begin() {
        for (int index = 0; index < COUNT; index++) {
            live[index] = false;
            pushX[index] = 0.0f;
            pushY[index] = 0.0f;
            bound[index] = -1;
        }
    }

    static void feed(int index, float x, float y, float shownWidth, float shownHeight,
                     boolean present, int priority, int attachedTo, float edge) {
        left[index] = x;
        top[index] = y;
        width[index] = shownWidth;
        height[index] = shownHeight;
        live[index] = present;
        rank[index] = priority;
        bound[index] = attachedTo;
        keepOut[index] = edge;
    }

    static void solve(float gap, float limit, int passes) {
        for (int pass = 0; pass < passes; pass++) {
            for (int first = 0; first < COUNT; first++) {
                if (!live[first]) continue;
                for (int second = first + 1; second < COUNT; second++) {
                    if (!live[second] || attached(first, second)) continue;
                    resolve(first, second, gap, limit);
                }
            }
        }
    }

    static float pushX(int index) {
        return pushX[index];
    }

    static float pushY(int index) {
        return pushY[index];
    }

    private static boolean attached(int first, int second) {
        return bound[first] == second || bound[second] == first;
    }

    private static void resolve(int first, int second, float gap, float limit) {
        float spanX = (width[first] + width[second]) / 2.0f + gap;
        float spanY = (height[first] + height[second]) / 2.0f + gap;
        float apartX = centerX(second) - centerX(first);
        float apartY = centerY(second) - centerY(first);
        float overlapX = spanX - Math.abs(apartX);
        float overlapY = spanY - Math.abs(apartY);
        if (overlapX <= 0.0f || overlapY <= 0.0f) return;

        int mover = rank[first] <= rank[second] ? second : first;
        float away = mover == second ? 1.0f : -1.0f;
        if (overlapX < overlapY) {
            float push = clamp(pushX[mover] + overlapX * away * direction(apartX), limit);
            pushX[mover] = inside(held(mover, push, gap, true),
                    left[mover], width[mover], roomWidth, keepOut[mover]);
            return;
        }
        float push = clamp(pushY[mover] + overlapY * away * direction(apartY), limit);
        pushY[mover] = inside(held(mover, push, gap, false),
                top[mover], height[mover], roomHeight, keepOut[mover]);
    }

    // WHY: стыкованный элемент не заезжает на свою цель: с ней расталкивание не считается вовсе,
    // WHY: и разведённая вниз строка сообщений садилась поверх ячеек хотбара, к которому стыкована
    private static float held(int mover, float push, float gap, boolean horizontal) {
        int target = bound[mover];
        if (target < 0 || !live[target]) return push;

        float origin = horizontal ? left[mover] : top[mover];
        float span = horizontal ? width[mover] : height[mover];
        float other = horizontal ? left[target] : top[target];
        float otherSpan = horizontal ? width[target] : height[target];

        if (origin + span / 2.0f <= other + otherSpan / 2.0f) {
            return Math.min(push, other - gap - span - origin);
        }
        return Math.max(push, other + otherSpan + gap - origin);
    }

    private static float inside(float push, float origin, float span, float room, float edge) {
        float low = edge - origin;
        float high = room - edge - span - origin;
        if (high < low) return push;
        return Math.max(low, Math.min(high, push));
    }

    private static float direction(float apart) {
        return apart < 0.0f ? -1.0f : 1.0f;
    }

    private static float centerX(int index) {
        return left[index] + pushX[index] + width[index] / 2.0f;
    }

    private static float centerY(int index) {
        return top[index] + pushY[index] + height[index] / 2.0f;
    }

    private static float clamp(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }
}
