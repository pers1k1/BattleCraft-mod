package com.persiki84.battlecraft.client.menu.lobby;

import com.persiki84.shared.client.menu.TickerLine;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public final class LobbyText {
    private static final int UNSET = Integer.MIN_VALUE;

    private String key;
    private int first = UNSET;
    private int second = UNSET;
    private Component current = Component.empty();
    private float width = -1.0f;
    private TickerLine ticker;
    private float tickerAlign = -1.0f;

    // WHY: строка собирается заново только при смене ключа или числа: перевод с аргументами
    // WHY: на каждый кадр это аллокация в отрисовке
    public boolean set(String nextKey, int value) {
        if (nextKey.equals(key) && value == first && second == UNSET) return false;
        restartOn(nextKey);
        key = nextKey;
        first = value;
        second = UNSET;
        replace(Component.translatable(nextKey, value));
        return true;
    }

    public boolean set(String nextKey, int value, int other) {
        if (nextKey.equals(key) && value == first && other == second) return false;
        restartOn(nextKey);
        key = nextKey;
        first = value;
        second = other;
        replace(Component.translatable(nextKey, value, other));
        return true;
    }

    public void set(String nextKey, Component value) {
        restartOn(nextKey);
        key = nextKey;
        first = UNSET;
        second = UNSET;
        replace(value);
    }

    // WHY: по цифрам перекатывается только смена числа в той же фразе; новая фраза встаёт целиком,
    // WHY: её приход несёт переезд строки, а не перекат букв
    private void restartOn(String nextKey) {
        if (ticker != null && !nextKey.equals(key)) ticker.forget();
    }

    TickerLine ticker(float align) {
        if (ticker == null || align != tickerAlign) {
            ticker = new TickerLine(align);
            tickerAlign = align;
        }
        return ticker;
    }

    private void replace(Component value) {
        current = value;
        width = -1.0f;
    }

    public String key() {
        return key;
    }

    public Component current() {
        return current;
    }

    public float width(Font font) {
        if (width < 0.0f) width = UiRender.width(font, current);
        return width;
    }
}
