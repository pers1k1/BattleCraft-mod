package com.persiki84.shared.client.ui;

// WHY: ореолы одного кадра знают друг о друге, чтобы соседние всплывающие не давали двойной тени
// WHY: и не темнили друг другу тела. Запись живёт кадр и привязана к цели отрисовки: офскрин-стадия
// WHY: горения переносит кадр по мировой позе, и прямоугольники с главного кадра там лежат не на месте
final class UiHushLedger {
    static final int PRIORS = 4;
    static final int NONE = 0;

    private static final int CAPACITY = 24;
    private static final UiHushSpot[] laid = UiHushSpot.pool(CAPACITY);

    private static int count;
    private static long frame = -1L;

    private UiHushLedger() {}

    static void record(UiHushSpot spot) {
        refresh();
        if (count >= CAPACITY) return;

        laid[count].copy(spot);
        count++;
    }

    // WHY: массивов юниформов у core-шейдеров нет, поэтому мест под соседей ровно четыре, и берутся
    // WHY: последние нарисованные из тех, чья зона задевает новую: соседи в стопке уведомлений
    static void gather(UiHushSpot spot, UiHushSpot[] into) {
        refresh();
        int found = 0;
        for (int index = count - 1; index >= 0 && found < into.length; index--) {
            UiHushSpot prior = laid[index];
            if (prior.target != spot.target || !prior.touches(spot)) continue;

            into[found].copy(prior);
            found++;
        }
        for (int index = found; index < into.length; index++) {
            into[index].clear();
        }
    }

    // WHY: стадию каждый её хозяин заполняет заново, и ореолы прошлого хозяина в ней стёрты. Простая
    // WHY: копия кадра лежит в тех же пикселях, поэтому ореолы кадра переходят в стадию как есть, а
    // WHY: кадр, перенесённый плоскостью горения или полем осколков, лежит не на месте и не переходит
    static void refilled(int stage, int copiedFrom) {
        refresh();
        forget(stage);
        if (copiedFrom != NONE) inherit(copiedFrom, stage);
    }

    private static void forget(int target) {
        int kept = 0;
        for (int index = 0; index < count; index++) {
            if (laid[index].target == target) continue;
            if (kept != index) laid[kept].copy(laid[index]);
            kept++;
        }
        count = kept;
    }

    private static void inherit(int source, int stage) {
        int known = count;
        for (int index = 0; index < known && count < CAPACITY; index++) {
            if (laid[index].target != source) continue;
            laid[count].copy(laid[index]);
            laid[count].target = stage;
            count++;
        }
    }

    private static void refresh() {
        long now = UiFrame.frame();
        if (now == frame) return;

        frame = now;
        count = 0;
    }
}
