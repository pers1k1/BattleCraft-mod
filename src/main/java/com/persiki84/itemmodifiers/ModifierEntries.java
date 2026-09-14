package com.persiki84.itemmodifiers;

import java.util.List;

// WHY: конфиг мода общий, но на выделенном сервере он лежит только там. Клиент читал свой пустой
// WHY: файл, и в подсказке предмета не было ни одного настроенного атрибута, хотя сервер их выдавал
public final class ModifierEntries {
    private static volatile List<String> potions = List.of();
    private static volatile List<String> attributes = List.of();
    private static volatile boolean remote;

    private ModifierEntries() {}

    public static void adopt(List<String> potionEntries, List<String> attributeEntries) {
        potions = List.copyOf(potionEntries);
        attributes = List.copyOf(attributeEntries);
        remote = true;
        markStale();
    }

    public static void forget() {
        potions = List.of();
        attributes = List.of();
        remote = false;
        markStale();
    }

    public static List<String> potions() {
        return remote ? potions : ModifierConfig.getPotionEffects();
    }

    public static List<String> attributes() {
        return remote ? attributes : ModifierConfig.getAttributes();
    }

    private static void markStale() {
        AttributeHandler.markDirty();
        EffectHandler.markDirty();
    }
}
