package com.persiki84.zones.client.menu;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTicker;
import com.persiki84.zones.shop.ShopEntry;
import com.persiki84.zones.shop.ShopSchedule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// WHY: подпись цены у плитки спрашивается каждый кадр, а меняется раз в секунду: состояние закрытого
// WHY: товара держится примитивами, и строка собирается только при смене вида или секунды. Ширина
// WHY: подписи в полном кегле меряется один раз на смену текста: тикер помнит замер одного кегля, и
// WHY: замер полного рядом с отрисовкой в сжатом сбрасывал бы его места букв каждый кадр
final class ShopPriceFace {
    private static final int OPENS_MATCH = 0;
    private static final int OPENS_IN = 1;
    private static final int CLOSED = 2;
    private static final int SOLD_OUT = 3;
    private static final int RESTOCK = 4;
    private static final int SECONDS_PER_MINUTE = 60;

    final Smooth icon;
    private int kind = -1;
    private int seconds = -1;
    private String language;
    private String shut = "";
    private String fitted;
    private float fittedPixels = -1.0f;
    private float natural;

    ShopPriceFace(boolean priced, float iconSpeed) {
        icon = new Smooth(priced ? 1.0f : 0.0f, iconSpeed);
    }

    String shut(ShopEntry entry) {
        int nextKind = kindOf(entry);
        int nextSeconds = secondsOf(entry, nextKind);
        String nextLanguage = Minecraft.getInstance().getLanguageManager().getSelected();
        if (nextKind == kind && nextSeconds == seconds && nextLanguage == language) return shut;

        kind = nextKind;
        seconds = nextSeconds;
        language = nextLanguage;
        shut = labelOf(entry).getString();
        return shut;
    }

    float natural(GuiGraphics graphics, Font font, UiTicker footer, float scale) {
        String text = footer.text();
        float pixels = UiRender.restPixels(graphics);
        if (text == fitted && pixels == fittedPixels) return natural;

        fitted = text;
        fittedPixels = pixels;
        natural = footer.width(graphics, font, scale);
        return natural;
    }

    private static int kindOf(ShopEntry entry) {
        ShopSchedule.State state = ShopClock.state(entry);
        if (state == ShopSchedule.State.CLOSED) return CLOSED;
        if (state == ShopSchedule.State.WAITING) return ShopClock.elapsed() < 0L ? OPENS_MATCH : OPENS_IN;
        return entry.remainingSeconds(System.currentTimeMillis()) <= 0 ? SOLD_OUT : RESTOCK;
    }

    private static int secondsOf(ShopEntry entry, int kind) {
        return switch (kind) {
            case OPENS_MATCH -> entry.opensAfter();
            case OPENS_IN -> ShopSchedule.secondsToOpen(entry, ShopClock.elapsed());
            case RESTOCK -> entry.remainingSeconds(System.currentTimeMillis());
            default -> 0;
        };
    }

    private Component labelOf(ShopEntry entry) {
        return switch (kind) {
            case SOLD_OUT -> Component.translatable("zones.shop.sold_out");
            case RESTOCK -> Component.translatable("zones.shop.restock.short", clock(seconds));
            default -> ShopClock.label(entry);
        };
    }

    private static String clock(int total) {
        int rest = total % SECONDS_PER_MINUTE;
        return total / SECONDS_PER_MINUTE + ":" + (rest < 10 ? "0" : "") + rest;
    }
}
