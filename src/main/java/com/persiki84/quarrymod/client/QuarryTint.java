package com.persiki84.quarrymod.client;

import com.persiki84.shared.client.ui.UiAccent;

public final class QuarryTint {
    private static final int DIAMOND = 0xFF4FE6E0;
    private static final int EMERALD = 0xFF3CE07C;
    private static final int GOLD = 0xFFF5C63C;
    private static final int IRON = 0xFFE3C9A8;
    private static final int COPPER = 0xFFE87A45;
    private static final int COAL = 0xFF7C88A0;
    private static final int REDSTONE = 0xFFFF3D4E;
    private static final int LAPIS = 0xFF4670E8;
    private static final int QUARTZ = 0xFFEDE6D8;
    private static final int DEBRIS = 0xFFB98351;

    private QuarryTint() {}

    public static int color(String ore) {
        if (ore == null || ore.isEmpty()) return UiAccent.color();
        if (ore.contains("diamond")) return DIAMOND;
        if (ore.contains("emerald")) return EMERALD;
        if (ore.contains("gold")) return GOLD;
        if (ore.contains("iron")) return IRON;
        if (ore.contains("copper")) return COPPER;
        if (ore.contains("coal")) return COAL;
        if (ore.contains("redstone")) return REDSTONE;
        if (ore.contains("lapis")) return LAPIS;
        if (ore.contains("quartz")) return QUARTZ;
        if (ore.contains("debris")) return DEBRIS;
        return UiAccent.color();
    }

    public static float red(int argb) {
        return (argb >> 16 & 0xFF) / 255.0f;
    }

    public static float green(int argb) {
        return (argb >> 8 & 0xFF) / 255.0f;
    }

    public static float blue(int argb) {
        return (argb & 0xFF) / 255.0f;
    }
}
