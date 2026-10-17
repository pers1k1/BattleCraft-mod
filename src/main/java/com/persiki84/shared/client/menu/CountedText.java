package com.persiki84.shared.client.menu;

import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

// WHY: подпись с числами, которую спрашивают каждый кадр, собирается заново только при смене ключа
// WHY: или чисел: перевод с аргументами в кадре это аллокация, а TickerLine узнаёт неизменную
// WHY: подпись по ссылке и тогда не разворачивает её в строку. Перевод на смене языка менять не
// WHY: нужно: TranslatableContents сам пересобирается под новый язык при следующем чтении
public final class CountedText {
    private static final String BLANK = "";
    private static final String RATIO = "/";
    private static final long NONE = Long.MIN_VALUE;

    private String key;
    private int arity = -1;
    private long first = NONE;
    private long second = NONE;
    private Component current = CommonComponents.EMPTY;

    public Component blank() {
        if (holds(BLANK, 0, NONE, NONE)) return current;
        return keep(BLANK, 0, NONE, NONE, CommonComponents.EMPTY);
    }

    public Component of(String nextKey) {
        if (holds(nextKey, 0, NONE, NONE)) return current;
        return keep(nextKey, 0, NONE, NONE, Component.translatable(nextKey));
    }

    public Component of(String nextKey, long value) {
        if (holds(nextKey, 1, value, NONE)) return current;
        return keep(nextKey, 1, value, NONE, Component.translatable(nextKey, value));
    }

    public Component of(String nextKey, long value, long other) {
        if (holds(nextKey, 2, value, other)) return current;
        return keep(nextKey, 2, value, other, Component.translatable(nextKey, value, other));
    }

    public Component ratio(long value, long other) {
        if (holds(RATIO, 2, value, other)) return current;
        return keep(RATIO, 2, value, other, Component.literal(value + RATIO + other));
    }

    private boolean holds(String nextKey, int count, long value, long other) {
        return count == arity && value == first && other == second && nextKey.equals(key);
    }

    private Component keep(String nextKey, int count, long value, long other, Component made) {
        key = nextKey;
        arity = count;
        first = value;
        second = other;
        current = made;
        return made;
    }
}
