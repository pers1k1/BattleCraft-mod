package com.persiki84.battlecraft.client.hud;

import com.persiki84.shared.client.ui.UiScale;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

public final class ForeignHud {
    private static final float BOTTOM_SHARE = 0.45f;
    private static final float COLUMN_WIDTH = 140.0f;
    private static final float DOCK_GAP = 12.0f;
    private static final float MARGIN = 4.0f;
    private static final float NOTHING = Float.MAX_VALUE;
    private static final int MAX_MARKS = 128;

    private static final Column leftColumn = new Column();
    private static final Column rightColumn = new Column();

    private static boolean watching;

    private ForeignHud() {}

    public static void beginFrame() {
        leftColumn.begin();
        rightColumn.begin();
    }

    public static void watch(boolean value) {
        watching = value;
    }

    public static boolean watching() {
        return watching;
    }

    public static void commit() {
        watching = false;
        leftColumn.commit();
        rightColumn.commit();
    }

    public static void record(Matrix4f pose, float left, float top, float right, float bottom) {
        if (!watching || bottom <= top || right <= left) return;

        float height = screenHeight();
        float placedTop = top * pose.m11() + pose.m31();
        if (height <= 0.0f || placedTop < height * BOTTOM_SHARE) return;

        float placedLeft = left * pose.m00() + pose.m30();
        float placedRight = right * pose.m00() + pose.m30();
        float column = COLUMN_WIDTH * UiScale.factor();
        float placedBottom = bottom * pose.m11() + pose.m31();

        if (placedLeft < column) leftColumn.add(placedTop, placedBottom);
        if (placedRight > screenWidth() - column) rightColumn.add(placedTop, placedBottom);
    }

    public static float clearance(boolean rightSide) {
        float top = rightSide ? rightColumn.top() : leftColumn.top();
        float height = screenHeight();
        if (top >= NOTHING || height <= 0.0f) return 0.0f;

        return Math.max(0.0f, (height - top) / UiScale.factor() + MARGIN);
    }

    private static float screenHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    private static float screenWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private static final class Column {
        private final float[] tops = new float[MAX_MARKS];
        private final float[] bottoms = new float[MAX_MARKS];
        private int marks;
        private float shownTop = NOTHING;

        private void begin() {
            marks = 0;
        }

        private void add(float top, float bottom) {
            if (marks >= MAX_MARKS) return;
            tops[marks] = top;
            bottoms[marks] = bottom;
            marks++;
        }

        private void commit() {
            shownTop = marks == 0 ? NOTHING : climb(deepest());
        }

        private float top() {
            return shownTop;
        }

        private float deepest() {
            float edge = bottoms[0];
            for (int i = 1; i < marks; i++) {
                edge = Math.max(edge, bottoms[i]);
            }
            return edge;
        }

        private float climb(float edge) {
            boolean grown = true;
            while (grown) {
                grown = false;
                for (int i = 0; i < marks; i++) {
                    if (tops[i] >= edge || bottoms[i] < edge - DOCK_GAP) continue;
                    edge = tops[i];
                    grown = true;
                }
            }
            return edge;
        }
    }
}
