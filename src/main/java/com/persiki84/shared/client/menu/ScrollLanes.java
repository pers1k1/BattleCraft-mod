package com.persiki84.shared.client.menu;

public final class ScrollLanes {
    private float rowsOffset;
    private float rowsTop;
    private float rowsBottom;
    private float listOffset;
    private float listTop;
    private float listBottom;

    public void rows(float offset, float top, float bottom) {
        rowsOffset = offset;
        rowsTop = top;
        rowsBottom = bottom;
    }

    public void list(float offset, float top, float bottom) {
        listOffset = offset;
        listTop = top;
        listBottom = bottom;
    }

    public float offset(GlidingRow.Lane lane) {
        return lane == GlidingRow.Lane.LIST ? listOffset : rowsOffset;
    }

    public float top(GlidingRow.Lane lane) {
        return lane == GlidingRow.Lane.LIST ? listTop : rowsTop;
    }

    public float bottom(GlidingRow.Lane lane) {
        return lane == GlidingRow.Lane.LIST ? listBottom : rowsBottom;
    }
}
