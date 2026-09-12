package com.persiki84.shared.client.ui;

public final class UiWorldPalette {
    public static final int FRIENDLY = 0xFF3FDB6A;
    public static final int HOSTILE = 0xFFE04B4B;
    public static final int SHOP = 0xFFF2C94C;
    public static final int CONTESTED = 0xFFF2994A;
    public static final int MARK = 0xFFE7E9F4;

    private static int friendly = FRIENDLY;
    private static int hostile = HOSTILE;
    private static int shop = SHOP;
    private static int contested = CONTESTED;
    private static int mark = MARK;

    private UiWorldPalette() {}

    public static void reset() {
        friendly = FRIENDLY;
        hostile = HOSTILE;
        shop = SHOP;
        contested = CONTESTED;
        mark = MARK;
    }

    public static void friendly(int argb) {
        friendly = argb;
    }

    public static void hostile(int argb) {
        hostile = argb;
    }

    public static void shop(int argb) {
        shop = argb;
    }

    public static void contested(int argb) {
        contested = argb;
    }

    public static void mark(int argb) {
        mark = argb;
    }

    public static int friendly() {
        return friendly;
    }

    public static int hostile() {
        return hostile;
    }

    public static int shop() {
        return shop;
    }

    public static int contested() {
        return contested;
    }

    public static int mark() {
        return mark;
    }
}
