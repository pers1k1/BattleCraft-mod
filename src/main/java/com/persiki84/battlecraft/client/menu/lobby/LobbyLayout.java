package com.persiki84.battlecraft.client.menu.lobby;

import com.persiki84.shared.client.ui.UiMetrics;

public final class LobbyLayout {
    public static final float TILE_HEIGHT = 21.0f;
    public static final float TILE_GAP = 4.0f;
    public static final float TILE_INSET = 5.0f;
    public static final float HEADER = 17.0f;
    public static final float FOOTER = 17.0f;
    public static final float RADIUS = 14.0f;
    private static final float ROW = TILE_HEIGHT + TILE_GAP;
    private static final float CHROME = UiMetrics.PAD * 2.0f + HEADER + FOOTER + UiMetrics.GAP - TILE_GAP;
    private static final float SIDE_MARGIN = 18.0f;
    private static final float GAP = 12.0f;
    private static final float MAX_WIDTH = 176.0f;
    private static final float MIN_WIDTH = 40.0f;

    private float left;
    private float top;
    private float width = MAX_WIDTH;
    private float height;
    private int count;
    private int rows = 1;
    private int stamp;
    private int frame;

    public void measure(int screenWidth, float columnsTop, float room, int teams, int wantedRows) {
        int columns = Math.max(1, teams);
        float available = screenWidth - SIDE_MARGIN * 2.0f - GAP * (columns - 1);
        float fitted = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, available / columns));
        int fit = Math.max(1, (int) ((room - CHROME) / ROW));
        int fittedRows = Math.max(1, Math.min(fit, wantedRows));
        float total = columns * fitted + (columns - 1) * GAP;
        float fittedLeft = Math.max(SIDE_MARGIN, (screenWidth - total) / 2.0f);

        boolean reframed = fitted != width || columns != count || fittedLeft != left || columnsTop != top;
        if (reframed) frame++;
        if (reframed || fittedRows != rows) stamp++;
        width = fitted;
        rows = fittedRows;
        count = columns;
        left = fittedLeft;
        top = columnsTop;
        height = CHROME + rows * ROW;
    }

    public int stamp() {
        return stamp;
    }

    public int frame() {
        return frame;
    }

    public int rows() {
        return rows;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float top() {
        return top;
    }

    public float bottom() {
        return top + height;
    }

    public float columnX(int index) {
        return left + index * (width + GAP);
    }

    public float tileX(int index) {
        return columnX(index) + TILE_INSET;
    }

    public float tileWidth() {
        return width - TILE_INSET * 2.0f;
    }

    public float rowY(int row) {
        return top + UiMetrics.PAD + HEADER + row * ROW;
    }

    public float footerTop() {
        return top + height - UiMetrics.PAD - FOOTER;
    }

    public int columnAt(double x, double y) {
        if (y < top || y > top + height) return -1;
        for (int index = 0; index < count; index++) {
            float columnLeft = columnX(index);
            if (x >= columnLeft && x <= columnLeft + width) return index;
        }
        return -1;
    }
}
