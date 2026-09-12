package com.persiki84.shared.client.ui;

public final class UiTagStack {
    private static final float GAP = UiMetrics.GAP_TIGHT;
    private static final float LIFT_SPEED = 13.0f;
    private static final float MAX_LIFT = 180.0f;
    private static final int PASSES = 4;
    private static final int ROOM = 64;

    private static final Slot[] frame = new Slot[ROOM];
    private static final float[] settled = new float[ROOM];

    private static int count;
    private static long stamp = -1L;

    private UiTagStack() {}

    public static Slot slot() {
        return new Slot();
    }

    public static float lift(Slot slot, float centerX, float topY, float width, float height) {
        long now = UiFrame.frame();
        if (now != stamp) {
            solve();
            stamp = now;
            count = 0;
        }

        if (slot.shown != now - 1L) {
            slot.target = 0.0f;
            slot.lift.snap(0.0f);
        }
        slot.shown = now;
        slot.left = centerX - width / 2.0f;
        slot.top = topY;
        slot.width = width;
        slot.height = height;

        if (count < ROOM) {
            frame[count++] = slot;
        }
        return slot.lift.to(slot.target, UiFrame.delta());
    }

    private static void solve() {
        sortByDepth();
        for (int index = 0; index < count; index++) {
            float top = drop(index);
            settled[index] = top;
            frame[index].target = clamp(top - frame[index].top);
        }
    }

    private static float drop(int index) {
        Slot tag = frame[index];
        float top = tag.top;

        for (int pass = 0; pass < PASSES; pass++) {
            float pushed = pushBelow(index, top);
            if (pushed == top) return top;
            top = pushed;
        }
        return top;
    }

    private static float pushBelow(int index, float top) {
        Slot tag = frame[index];
        for (int above = 0; above < index; above++) {
            Slot placed = frame[above];
            if (!overlapX(tag, placed)) continue;

            float bottom = settled[above] + placed.height + GAP;
            if (top < bottom && top + tag.height + GAP > settled[above]) {
                top = bottom;
            }
        }
        return top;
    }

    private static boolean overlapX(Slot first, Slot second) {
        return first.left < second.left + second.width + GAP
                && first.left + first.width + GAP > second.left;
    }

    private static void sortByDepth() {
        for (int index = 1; index < count; index++) {
            Slot moving = frame[index];
            int place = index;
            while (place > 0 && frame[place - 1].depth > moving.depth) {
                frame[place] = frame[place - 1];
                place--;
            }
            frame[place] = moving;
        }
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(MAX_LIFT, value));
    }

    public static final class Slot {
        private final Smooth lift = new Smooth(0.0f, LIFT_SPEED);
        private float left;
        private float top;
        private float width;
        private float height;
        private float depth;
        private float target;
        private long shown = Long.MIN_VALUE;

        private Slot() {}

        public void depth(float away) {
            depth = away;
        }
    }
}
