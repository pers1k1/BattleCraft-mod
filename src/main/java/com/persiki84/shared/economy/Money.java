package com.persiki84.shared.economy;

import net.minecraft.network.chat.Component;

// WHY: сервер и клиент обязаны называть сумму одинаково: чат сервера, HUD и окно магазина говорят об
// WHY: одних и тех же деньгах. Здесь нет ни игрока, ни мира, поэтому класс берёт и клиентский код
public final class Money {
    public static final long LIMIT = 1_000_000_000L;
    public static final String DEFAULT_NAME_KEY = "sellmod.money.default_name";
    public static final String AMOUNT_KEY = "sellmod.money.amount";
    public static final String ITEM_AMOUNT_KEY = "sellmod.money.amount.item";
    public static final String COINS_KEY = "sellmod.money.coins.";

    private static final String ONE = "one";
    private static final String FEW = "few";
    private static final String MANY = "many";

    private Money() {}

    public static long clamp(long value) {
        return Math.max(0L, Math.min(LIMIT, value));
    }

    public static Component unit(CurrencyMode mode, String customName, Component itemName) {
        if (mode != CurrencyMode.ACCOUNT) return itemName;
        return customName == null || customName.isEmpty()
                ? Component.translatable(DEFAULT_NAME_KEY) : Component.literal(customName);
    }

    public static Component amount(long amount, CurrencyMode mode, String customName, Component itemName) {
        if (mode != CurrencyMode.ACCOUNT) return Component.translatable(ITEM_AMOUNT_KEY, amount, itemName);
        if (customName == null || customName.isEmpty()) {
            return Component.translatable(COINS_KEY + plural(amount), amount);
        }
        return Component.translatable(AMOUNT_KEY, amount, Component.literal(customName));
    }

    // WHY: правило русского языка покрывает и английское: у английского «few» и «many» переведены
    // WHY: одинаково, поэтому выбор формы на сервере верен для любого языка клиента
    public static String plural(long amount) {
        long value = Math.abs(amount);
        long tens = value % 100L;
        long units = value % 10L;
        if (units == 1L && tens != 11L) return ONE;
        if (units >= 2L && units <= 4L && (tens < 12L || tens > 14L)) return FEW;
        return MANY;
    }
}
