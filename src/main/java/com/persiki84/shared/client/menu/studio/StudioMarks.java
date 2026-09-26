package com.persiki84.shared.client.menu.studio;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

// WHY: отметка держится по ключу вещи, а не по номеру плитки: снимок с сервера переставляет и
// WHY: удаляет плитки, и по номеру выделение перескакивало бы на соседей. Битовая маска для сетки
// WHY: пересчитывается только при смене отметок или данных, а не в кадре
public final class StudioMarks {
    private final Set<String> keys = new LinkedHashSet<>();
    private final Set<String> bandBase = new LinkedHashSet<>();
    private final BitSet bits = new BitSet();
    private final BitSet collected = new BitSet();

    public BitSet bits() {
        return bits;
    }

    public int size() {
        return keys.size();
    }

    public boolean group() {
        return keys.size() >= 2;
    }

    public boolean has(int index) {
        return bits.get(index);
    }

    public void clear() {
        keys.clear();
        bits.clear();
    }

    public void toggle(String key) {
        if (key == null) return;
        if (!keys.remove(key)) keys.add(key);
    }

    public void add(String key) {
        if (key != null) keys.add(key);
    }

    public void beginBand(boolean additive) {
        bandBase.clear();
        if (additive) bandBase.addAll(keys);
    }

    public BitSet collected() {
        collected.clear();
        return collected;
    }

    public void applyBand(IntFunction<String> keyOf) {
        keys.clear();
        keys.addAll(bandBase);
        for (int index = collected.nextSetBit(0); index >= 0; index = collected.nextSetBit(index + 1)) {
            add(keyOf.apply(index));
        }
    }

    public void refresh(int total, IntFunction<String> keyOf) {
        bits.clear();
        Set<String> present = new LinkedHashSet<>();
        for (int index = 0; index < total; index++) {
            String key = keyOf.apply(index);
            if (key == null) continue;
            present.add(key);
            if (keys.contains(key)) bits.set(index);
        }
        keys.retainAll(present);
    }

    public List<Integer> indices() {
        List<Integer> shown = new ArrayList<>();
        for (int index = bits.nextSetBit(0); index >= 0; index = bits.nextSetBit(index + 1)) {
            shown.add(index);
        }
        return shown;
    }

    public int first() {
        return bits.nextSetBit(0);
    }
}
