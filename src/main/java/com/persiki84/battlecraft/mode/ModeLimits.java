package com.persiki84.battlecraft.mode;

import com.persiki84.shared.economy.Wallets;
import net.minecraft.SharedConstants;

// WHY: пределы одни на файл, команду и строку студии: два места с границами разъезжаются на первой же правке
public final class ModeLimits {
    public static final int SCORE_MAX = 100;
    public static final int TARGET_MAX = 10000;
    public static final int CAP_MINUTES_MAX = 600;
    public static final int SUDDEN_MARGIN_MAX = 1000;
    public static final int SUDDEN_MINUTES_MAX = 60;
    public static final long MONEY_MAX = Wallets.LIMIT;
    public static final int KIT_ENTRIES_MAX = 36;
    public static final int KIT_COUNT_MAX = 640;
    public static final int TEXT_MAX = 4096;
    // WHY: описание уходит командой, а строка команды не длиннее 256 знаков вместе с префиксом
    public static final int DESCRIPTION_MAX = 200;

    private ModeLimits() {}

    public static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(maximum, value));
    }

    public static long money(long value) {
        return Math.max(0L, Math.min(MONEY_MAX, value));
    }

    // WHY: символ форматирования или управляющий в команде сервер считает недопустимым и отключает
    // WHY: игрока, поэтому описание чистится до записи; обрезка не оставляет половину суррогатной пары,
    // WHY: иначе запись файла режимов в UTF-8 падает целиком
    public static String description(String value) {
        if (value == null) return "";
        String clean = SharedConstants.filterText(value).strip();
        if (clean.length() <= DESCRIPTION_MAX) return clean;
        int end = Character.isHighSurrogate(clean.charAt(DESCRIPTION_MAX - 1)) ? DESCRIPTION_MAX - 1 : DESCRIPTION_MAX;
        return clean.substring(0, end).strip();
    }

    public static int kitCount(int value) {
        return Math.max(1, Math.min(KIT_COUNT_MAX, value));
    }
}
