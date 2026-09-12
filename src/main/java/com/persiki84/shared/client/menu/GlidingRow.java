package com.persiki84.shared.client.menu;

public interface GlidingRow {

    enum Lane {
        ROWS,
        LIST
    }

    void anchor(int y, Lane lane);

    void glide(ScrollLanes lanes);
}
