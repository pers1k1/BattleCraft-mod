package com.persiki84.zones.shop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// WHY: порядок разделов, отделов и товаров это порядок ключей LinkedHashMap - и в пакете,
// WHY: и в shop.json, поэтому перестановка переписывает карту целиком, а не меняет поле
public final class ShopOrder {
    public static final int UP = -1;
    public static final int DOWN = 1;

    private ShopOrder() {}

    public static <T> boolean move(Map<String, T> keyed, String id, int delta) {
        List<String> order = new ArrayList<>(keyed.keySet());
        int from = order.indexOf(id);
        if (from < 0) return false;

        int to = from + delta;
        if (to < 0 || to >= order.size()) return false;

        order.add(to, order.remove(from));
        rebuild(keyed, order);
        return true;
    }

    private static <T> void rebuild(Map<String, T> keyed, List<String> order) {
        Map<String, T> previous = new LinkedHashMap<>(keyed);
        keyed.clear();
        for (String key : order) {
            keyed.put(key, previous.get(key));
        }
    }
}
