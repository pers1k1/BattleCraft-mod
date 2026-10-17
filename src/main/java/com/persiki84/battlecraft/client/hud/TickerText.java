package com.persiki84.battlecraft.client.hud;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Objects;
import java.util.Optional;

// WHY: тикер несёт строку одним цветом. Чужой текст (заголовок, полоса босса, уведомление из чата)
// WHY: идёт тикером, только если все его части одного цвета с корнем и в строке нет кодов «§»:
// WHY: такие коды разбираются при отрисовке как цвет, а тикер двигал бы их по буквам как цифры.
// WHY: Остальное рисуется прежним пером со своей раскраской. Проверка идёт на смене компонента, не в кадре
public final class TickerText {
    public static final int NO_INK = 0;

    private TickerText() {}

    public static boolean plain(Component text) {
        TextColor root = text.getStyle().getColor();
        boolean mixed = text.visit((style, part) -> Objects.equals(style.getColor(), root)
                ? Optional.<Boolean>empty() : Optional.of(Boolean.TRUE), Style.EMPTY).isPresent();
        return !mixed && text.getString().indexOf(ChatFormatting.PREFIX_CODE) < 0;
    }

    public static int ink(Component text) {
        TextColor color = text.getStyle().getColor();
        return color == null ? NO_INK : 0xFF000000 | color.getValue();
    }

    public static int inkOr(int ink, int fallback) {
        return ink == NO_INK ? fallback : ink;
    }
}
