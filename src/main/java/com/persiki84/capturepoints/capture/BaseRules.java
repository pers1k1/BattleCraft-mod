package com.persiki84.capturepoints.capture;

import net.minecraft.nbt.CompoundTag;

// WHY: правила сети баз живут с точками в points.dat: точка-база и её возможности меняются вместе
// WHY: с картой, а место под новые игровые правила в маске правил кончилось
public final class BaseRules {
    public static final int CHANNEL_MIN = 1;
    public static final int CHANNEL_MAX = 30;
    public static final int COOLDOWN_MAX = 600;
    private static final int CHANNEL_DEFAULT = 5;
    private static final int COOLDOWN_DEFAULT = 60;
    private static final String KEY = "baseRules";

    private static boolean respawn = true;
    private static boolean travel = true;
    private static boolean shop = true;
    private static int channelSeconds = CHANNEL_DEFAULT;
    private static int cooldownSeconds = COOLDOWN_DEFAULT;

    private BaseRules() {}

    public static boolean respawn() { return respawn; }
    public static boolean travel() { return travel; }
    public static boolean shop() { return shop; }
    public static int channelSeconds() { return channelSeconds; }
    public static int cooldownSeconds() { return cooldownSeconds; }

    public static void respawn(boolean value) { respawn = value; }
    public static void travel(boolean value) { travel = value; }
    public static void shop(boolean value) { shop = value; }
    public static void channelSeconds(int value) { channelSeconds = Math.max(CHANNEL_MIN, Math.min(CHANNEL_MAX, value)); }
    public static void cooldownSeconds(int value) { cooldownSeconds = Math.max(0, Math.min(COOLDOWN_MAX, value)); }

    public static void reset() {
        respawn = true;
        travel = true;
        shop = true;
        channelSeconds = CHANNEL_DEFAULT;
        cooldownSeconds = COOLDOWN_DEFAULT;
    }

    public static void save(CompoundTag root) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("respawn", respawn);
        tag.putBoolean("travel", travel);
        tag.putBoolean("shop", shop);
        tag.putInt("channel", channelSeconds);
        tag.putInt("cooldown", cooldownSeconds);
        root.put(KEY, tag);
    }

    // WHY: файл со старого сервера ключа не знает, и тогда действуют значения по умолчанию, а не нули
    public static void load(CompoundTag root) {
        reset();
        if (!root.contains(KEY)) return;
        CompoundTag tag = root.getCompound(KEY);
        respawn = !tag.contains("respawn") || tag.getBoolean("respawn");
        travel = !tag.contains("travel") || tag.getBoolean("travel");
        shop = !tag.contains("shop") || tag.getBoolean("shop");
        if (tag.contains("channel")) channelSeconds(tag.getInt("channel"));
        if (tag.contains("cooldown")) cooldownSeconds(tag.getInt("cooldown"));
    }
}
