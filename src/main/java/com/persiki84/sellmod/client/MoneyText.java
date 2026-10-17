package com.persiki84.sellmod.client;

// WHY: на счёте бывает до миллиарда, и девять цифр подряд не читаются глазом: разряды делятся
// WHY: пробелом, как в русской записи чисел. Строка собирается только на смене суммы, не в кадре
public final class MoneyText {
    private static final int GROUP = 3;
    private static final char SEPARATOR = ' ';

    private MoneyText() {}

    public static String digits(long amount) {
        String plain = Long.toString(Math.abs(amount));
        StringBuilder grouped = new StringBuilder(plain.length() + plain.length() / GROUP + 1);
        if (amount < 0L) grouped.append('-');
        int lead = plain.length() % GROUP;
        for (int index = 0; index < plain.length(); index++) {
            if (index > 0 && (index - lead) % GROUP == 0) grouped.append(SEPARATOR);
            grouped.append(plain.charAt(index));
        }
        return grouped.toString();
    }

    public static String signed(long amount) {
        return (amount >= 0L ? "+" : "-") + digits(Math.abs(amount));
    }

    // WHY: в поле суммы пишут как угодно: с пробелами разрядов, со знаком плюс. Всё, что не цифра,
    // WHY: отбрасывается, а слишком длинное число отдаётся пределом, а не переполняется
    public static long parse(String text) {
        long value = 0L;
        boolean any = false;
        for (int index = 0; index < text.length(); index++) {
            char symbol = text.charAt(index);
            if (symbol < '0' || symbol > '9') continue;
            any = true;
            value = value * 10L + (symbol - '0');
            if (value > Long.MAX_VALUE / 20L) return Long.MAX_VALUE / 20L;
        }
        return any ? value : -1L;
    }
}
