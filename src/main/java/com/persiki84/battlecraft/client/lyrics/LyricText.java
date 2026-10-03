package com.persiki84.battlecraft.client.lyrics;

// WHY: текст приходит из базы, которую пополняет кто угодно: знак параграфа включал бы коды
// WHY: форматирования игры, управляющие и форматные символы (нулевая ширина, переключатели
// WHY: направления) ломают раскладку, а стопка комбинируемых знаков (залго) растит строку вверх и
// WHY: множит отрисовку. Оставляются печатные символы и не больше двух знаков на букву
public final class LyricText {
    private static final int MARKS_PER_LETTER = 2;
    private static final int SECTION_SIGN = 0x00A7;

    private LyricText() {}

    public static String clean(String value, int limit) {
        if (value == null || value.isEmpty()) return "";

        StringBuilder out = new StringBuilder(Math.min(value.length(), limit * 2));
        int kept = 0;
        int marks = 0;
        boolean space = false;
        for (int index = 0; index < value.length() && kept < limit; ) {
            int point = value.codePointAt(index);
            index += Character.charCount(point);
            if (Character.isWhitespace(point) || Character.isSpaceChar(point)) {
                space = kept > 0;
                continue;
            }
            if (!printable(point)) continue;
            boolean mark = combining(point);
            if (mark && (kept == 0 || ++marks > MARKS_PER_LETTER)) continue;
            if (!mark) marks = 0;
            if (space && !mark) {
                out.append(' ');
                kept++;
            }
            space = false;
            out.appendCodePoint(point);
            kept++;
        }
        return out.toString();
    }

    private static boolean printable(int point) {
        if (point == SECTION_SIGN) return false;
        int type = Character.getType(point);
        return type != Character.CONTROL && type != Character.FORMAT && type != Character.SURROGATE
                && type != Character.PRIVATE_USE && type != Character.UNASSIGNED;
    }

    private static boolean combining(int point) {
        int type = Character.getType(point);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK;
    }
}
