package com.persiki84.battlecraft.client.menu.desktop;

import net.minecraft.network.chat.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

// WHY: страница месяца собирается один раз на смену месяца или языка: название, сдвиг первого
// WHY: дня под первый день недели локали и длины соседних месяцев. В кадре только арифметика
final class CalendarPage {
    static final int COLUMNS = 7;
    static final int ROWS = 6;
    static final int CELLS = COLUMNS * ROWS;

    private final YearMonth month;
    private final Component title;
    private final int lead;
    private final int length;
    private final int previousLength;

    private CalendarPage(YearMonth month, Component title, int lead) {
        this.month = month;
        this.title = title;
        this.lead = lead;
        this.length = month.lengthOfMonth();
        this.previousLength = month.minusMonths(1).lengthOfMonth();
    }

    static CalendarPage of(YearMonth month, Locale locale, DayOfWeek firstDay) {
        int lead = Math.floorMod(month.atDay(1).getDayOfWeek().getValue() - firstDay.getValue(), COLUMNS);
        return new CalendarPage(month, Component.literal(titleOf(month, locale)), lead);
    }

    // WHY: месяц в заголовке стоит сам по себе, поэтому нужен именительный падеж («сентябрь», а не
    // WHY: «сентября»): это отдельная форма FULL_STANDALONE. Часть локалей без неё отдаёт номер
    // WHY: месяца, тогда берётся обычная полная форма
    private static String titleOf(YearMonth month, Locale locale) {
        String name = month.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale);
        if (name.isEmpty() || Character.isDigit(name.charAt(0))) {
            name = month.getMonth().getDisplayName(TextStyle.FULL, locale);
        }
        return capitalized(name, locale) + " " + month.getYear();
    }

    static Component[] weekdays(Locale locale, DayOfWeek firstDay) {
        Component[] names = new Component[COLUMNS];
        for (int column = 0; column < COLUMNS; column++) {
            DayOfWeek day = firstDay.plus(column);
            String name = day.getDisplayName(TextStyle.SHORT_STANDALONE, locale);
            if (name.isEmpty() || Character.isDigit(name.charAt(0))) name = day.getDisplayName(TextStyle.SHORT, locale);
            names[column] = Component.literal(capitalized(name, locale));
        }
        return names;
    }

    private static String capitalized(String text, Locale locale) {
        if (text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase(locale) + text.substring(1);
    }

    YearMonth month() {
        return month;
    }

    Component title() {
        return title;
    }

    int dayAt(int cell) {
        int day = cell - lead + 1;
        if (day < 1) return previousLength + day;
        return day > length ? day - length : day;
    }

    boolean inside(int cell) {
        int day = cell - lead + 1;
        return day >= 1 && day <= length;
    }

    int cellOf(LocalDate date) {
        if (!YearMonth.from(date).equals(month)) return -1;
        return lead + date.getDayOfMonth() - 1;
    }
}
