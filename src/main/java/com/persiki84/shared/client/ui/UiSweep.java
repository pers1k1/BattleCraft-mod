package com.persiki84.shared.client.ui;

public interface UiSweep {
    UiSweep NONE = index -> 1.0f;

    float lit(int index);

    default float head() {
        return -1.0f;
    }

    default int accent(int base) {
        return base;
    }
}
