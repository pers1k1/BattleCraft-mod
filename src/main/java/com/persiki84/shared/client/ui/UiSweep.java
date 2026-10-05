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

    default float held(int index) {
        return 0.0f;
    }

    // WHY: подъём буквы, когда настаёт её черёд: по умолчанию колокол по её доле, лирика отдаёт
    // WHY: движение своего слога
    default float motion(int index) {
        float share = Math.max(0.0f, Math.min(1.0f, lit(index)));
        return 4.0f * share * (1.0f - share);
    }
}
