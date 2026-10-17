package com.persiki84.sellmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.persiki84.shared.client.ui.UiIcon;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

// WHY: значок валюты один на весь интерфейс: HUD, шапка магазина, цены плиток и окно перевода
// WHY: показывают одни и те же деньги. Счёт без выбранного оператором значка рисуется своей
// WHY: монетой из шейдера значков, всё остальное - предметом, как раньше
public final class CurrencyIcon {
    private static final float ITEM_SIZE = 16.0f;
    private static final float COIN_SHARE = 0.86f;
    private static final float GONE = 0.01f;
    private static final float CUTOUT_ALPHA = 0.1f;

    private CurrencyIcon() {}

    public static void draw(GuiGraphics graphics, float x, float y, float size, int coinColor, float alpha) {
        if (alpha <= GONE || size <= 0.0f) return;

        if (ClientSellData.drawsCoin()) {
            coin(graphics, x, y, size, coinColor, alpha);
            return;
        }
        item(graphics, ClientSellData.icon(), x, y, size, alpha);
    }

    public static void coin(GuiGraphics graphics, float x, float y, float size, int color, float alpha) {
        UiIcon.draw(graphics, UiIcon.Kind.COIN, x + size / 2.0f, y + size / 2.0f, size * COIN_SHARE,
                UiTheme.alpha(color, alpha));
    }

    // WHY: renderItem принимает целые координаты, а значок едет вместе с карточкой субпиксельно:
    // WHY: место ставит матрица, иначе значок прыгал бы по пикселям отдельно от своей плашки.
    // WHY: Блок в интерфейсе рисуется cutout без смешивания: альфа модулятора у него только отсекает
    // WHY: фрагменты ниже CUTOUT_ALPHA, и значок стоял бы целиком, а потом пропадал кадром. Поэтому
    // WHY: на уходе значок ещё и сжимается к середине и доходит до нуля как раз к этому порогу
    public static void item(GuiGraphics graphics, ItemStack stack, float x, float y, float size, float alpha) {
        if (stack.isEmpty() || alpha <= GONE) return;
        float share = alpha >= 1.0f ? 1.0f : Math.max(0.0f, (alpha - CUTOUT_ALPHA) / (1.0f - CUTOUT_ALPHA));
        if (share <= 0.0f) return;

        float shown = size * share;
        float scale = shown / ITEM_SIZE;
        float[] tone = RenderSystem.getShaderColor();
        float red = tone[0];
        float green = tone[1];
        float blue = tone[2];
        float base = tone[3];
        graphics.flush();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x + (size - shown) / 2.0f, y + (size - shown) / 2.0f, 0.0f);
            graphics.pose().scale(scale, scale, 1.0f);
            RenderSystem.setShaderColor(red, green, blue, base * alpha);
            graphics.renderItem(stack, 0, 0);
        } finally {
            graphics.flush();
            RenderSystem.setShaderColor(red, green, blue, base);
            graphics.pose().popPose();
        }
    }
}
