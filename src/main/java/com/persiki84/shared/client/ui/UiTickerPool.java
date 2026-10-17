package com.persiki84.shared.client.ui;

import java.util.HashMap;
import java.util.Map;

// WHY: у списка (плитки, строки, карточки) тикер свой на каждую вещь, и держится он ключом вещи, а не
// WHY: номером строки: состав списка меняется, и по номеру ход перескакивал бы между вещами. Поиск по
// WHY: ключу в кадре не аллоцирует (ключ - String, UUID, enum, record, но не упакованное число), а
// WHY: тикеры вещей, которых давно нет на экране, возвращаются в запас и выдаются заново сброшенными
public final class UiTickerPool {
    private static final long KEPT_FRAMES = 600L;
    private static final long SWEEP_FRAMES = 60L;

    private final Map<Object, UiTicker> byKey = new HashMap<>();
    private Object[] keys = new Object[16];
    private UiTicker[] live = new UiTicker[16];
    private UiTicker[] spare = new UiTicker[16];
    private int liveCount;
    private int spareCount;
    private long swept;

    private final float align;
    private final float tracking;
    private final boolean rising;
    private final float throttle;

    public UiTickerPool(float align) {
        this(align, 0.0f, false, 0.0f);
    }

    public UiTickerPool(float align, float tracking, boolean rising, float throttle) {
        this.align = align;
        this.tracking = tracking;
        this.rising = rising;
        this.throttle = throttle;
    }

    public UiTicker of(Object key) {
        sweep();
        UiTicker ticker = byKey.get(key);
        if (ticker == null) ticker = admit(key);
        ticker.seen = UiFrame.frame();
        return ticker;
    }

    private UiTicker admit(Object key) {
        UiTicker ticker = acquire();
        byKey.put(key, ticker);
        keys = grown(keys, liveCount + 1);
        live = grown(live, liveCount + 1);
        keys[liveCount] = key;
        live[liveCount] = ticker;
        liveCount++;
        return ticker;
    }

    public void forget(Object key) {
        UiTicker ticker = byKey.remove(key);
        if (ticker == null) return;

        for (int index = 0; index < liveCount; index++) {
            if (live[index] == ticker) {
                drop(index);
                return;
            }
        }
    }

    public void clear() {
        while (liveCount > 0) {
            byKey.remove(keys[liveCount - 1]);
            drop(liveCount - 1);
        }
    }

    private UiTicker acquire() {
        if (spareCount == 0) return configured(new UiTicker());

        UiTicker ticker = spare[--spareCount];
        spare[spareCount] = null;
        ticker.reset();
        return ticker;
    }

    private UiTicker configured(UiTicker ticker) {
        ticker.align(align).tracking(tracking).throttle(throttle);
        return rising ? ticker.rising() : ticker;
    }

    private void sweep() {
        long now = UiFrame.frame();
        if (now - swept < SWEEP_FRAMES) return;

        swept = now;
        for (int index = liveCount - 1; index >= 0; index--) {
            if (now - live[index].seen <= KEPT_FRAMES) continue;
            byKey.remove(keys[index]);
            drop(index);
        }
    }

    private void drop(int index) {
        spare = grown(spare, spareCount + 1);
        spare[spareCount++] = live[index];
        int last = liveCount - 1;
        keys[index] = keys[last];
        live[index] = live[last];
        keys[last] = null;
        live[last] = null;
        liveCount = last;
    }

    private static Object[] grown(Object[] array, int count) {
        if (array.length >= count) return array;
        Object[] wider = new Object[array.length * 2];
        System.arraycopy(array, 0, wider, 0, array.length);
        return wider;
    }

    private static UiTicker[] grown(UiTicker[] array, int count) {
        if (array.length >= count) return array;
        UiTicker[] wider = new UiTicker[array.length * 2];
        System.arraycopy(array, 0, wider, 0, array.length);
        return wider;
    }
}
