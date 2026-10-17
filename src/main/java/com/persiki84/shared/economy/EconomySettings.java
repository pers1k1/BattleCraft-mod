package com.persiki84.shared.economy;

import net.minecraft.world.item.ItemStack;

public final class EconomySettings {
    public static final int NAME_LIMIT = 24;
    public static final int DEATH_LOSS_MAX = 100;
    public static final boolean RESET_ON_START_DEFAULT = true;

    private CurrencyMode mode = CurrencyMode.DEFAULT;
    private String name = "";
    private ItemStack icon = ItemStack.EMPTY;
    private boolean resetOnStart = RESET_ON_START_DEFAULT;
    private long startBalance;
    private int deathLossPercent;

    public CurrencyMode mode() {
        return mode;
    }

    public String name() {
        return name;
    }

    public ItemStack icon() {
        return icon;
    }

    public boolean iconSet() {
        return !icon.isEmpty();
    }

    public boolean resetOnStart() {
        return resetOnStart;
    }

    public long startBalance() {
        return startBalance;
    }

    public int deathLossPercent() {
        return deathLossPercent;
    }

    void mode(CurrencyMode value) {
        mode = value == null ? CurrencyMode.DEFAULT : value;
    }

    void name(String value) {
        name = cleanName(value);
    }

    // WHY: значок это только картинка: количество и повреждение не несут смысла, а лишний счётчик в углу
    // WHY: значка читался бы как цена
    void icon(ItemStack value) {
        icon = value == null || value.isEmpty() ? ItemStack.EMPTY : value.copyWithCount(1);
    }

    void resetOnStart(boolean value) {
        resetOnStart = value;
    }

    void startBalance(long value) {
        startBalance = Wallets.clamp(value);
    }

    void deathLossPercent(int value) {
        deathLossPercent = Math.max(0, Math.min(DEATH_LOSS_MAX, value));
    }

    void restoreDefaults() {
        mode = CurrencyMode.DEFAULT;
        name = "";
        icon = ItemStack.EMPTY;
        resetOnStart = RESET_ON_START_DEFAULT;
        startBalance = 0L;
        deathLossPercent = 0;
    }

    // WHY: имя валюты попадает в чат и в HUD каждого игрока: управляющие символы и знак раскраски
    // WHY: ломали бы строку у всех разом
    public static String cleanName(String raw) {
        if (raw == null) return "";

        StringBuilder clean = new StringBuilder();
        raw.strip().codePoints()
                .filter(point -> point >= 0x20 && point != 0x7F && point != 0xA7)
                .limit(NAME_LIMIT)
                .forEach(clean::appendCodePoint);
        return clean.toString().strip();
    }
}
