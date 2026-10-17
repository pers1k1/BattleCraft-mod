package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiFrame;

import java.util.HashMap;
import java.util.Map;

// WHY: у списка подпись с числом своя на каждую вещь и держится ключом вещи, а не номером строки:
// WHY: состав меняется, и по номеру ход перескакивал бы между вещами. Подписи вещей, которых давно
// WHY: нет на экране, выбрасываются обходом раз в секунду, без итератора и без сборки в кадре
public final class TickerLines {
    private static final long KEPT_FRAMES = 600L;
    private static final long SWEEP_FRAMES = 60L;

    private final Map<Object, TickerLine> byKey = new HashMap<>();
    private Object[] keys = new Object[16];
    private TickerLine[] lines = new TickerLine[16];
    private int count;
    private long swept;

    private final float align;
    private final float tracking;

    public TickerLines(float align) {
        this(align, 0.0f);
    }

    public TickerLines(float align, float tracking) {
        this.align = align;
        this.tracking = tracking;
    }

    public TickerLine of(Object key) {
        sweep();
        TickerLine line = byKey.get(key);
        if (line == null) line = admit(key);
        line.seen = UiFrame.frame();
        return line;
    }

    public void clear() {
        byKey.clear();
        for (int index = 0; index < count; index++) {
            keys[index] = null;
            lines[index] = null;
        }
        count = 0;
    }

    private TickerLine admit(Object key) {
        TickerLine line = new TickerLine(align, tracking);
        byKey.put(key, line);
        if (count == keys.length) grow();
        keys[count] = key;
        lines[count] = line;
        count++;
        return line;
    }

    private void grow() {
        Object[] wideKeys = new Object[keys.length * 2];
        TickerLine[] wideLines = new TickerLine[lines.length * 2];
        System.arraycopy(keys, 0, wideKeys, 0, count);
        System.arraycopy(lines, 0, wideLines, 0, count);
        keys = wideKeys;
        lines = wideLines;
    }

    private void sweep() {
        long now = UiFrame.frame();
        if (now - swept < SWEEP_FRAMES) return;

        swept = now;
        for (int index = count - 1; index >= 0; index--) {
            if (now - lines[index].seen <= KEPT_FRAMES) continue;
            byKey.remove(keys[index]);
            int last = --count;
            keys[index] = keys[last];
            lines[index] = lines[last];
            keys[last] = null;
            lines[last] = null;
        }
    }
}
