package com.persiki84.battlecraft.client.menu.panel.loot;

import java.util.Locale;

// WHY: полоска шанса на плитке логарифмическая: на линейной весь редкий лут, который и настраивают
// WHY: точнее всего, лежал бы в первом проценте её длины и выглядел бы одинаково пустым
public final class LootChance {
    public static final float LOWEST = 0.1f;
    public static final float HIGHEST = 100.0f;
    private static final double DECADES = Math.log10(HIGHEST / LOWEST);

    private LootChance() {}

    public static double toSlider(float percent) {
        float clamped = Math.max(LOWEST, Math.min(HIGHEST, percent));
        return Math.log10(clamped / LOWEST) / DECADES;
    }

    public static String shown(float percent) {
        if (percent >= 10.0f || percent == Math.round(percent)) return String.valueOf(Math.round(percent));
        String text = String.format(Locale.ROOT, "%.2f", percent);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public static String argument(float percent) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.01f, Math.min(HIGHEST, percent)));
    }
}
