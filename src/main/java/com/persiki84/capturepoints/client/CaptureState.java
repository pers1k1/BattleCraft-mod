package com.persiki84.capturepoints.client;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// WHY: слова «ПАУЗА» и «ОТКАТ» менялись кадром и читались как дефект: тег уезжает вверх и гаснет,
// WHY: новый приходит снизу, а место под них едет шириной, иначе карточка прыгает на смене
public final class CaptureState {
    private static final float SWAP_SPEED = 13.0f;
    private static final float WIDTH_SPEED = 15.0f;
    private static final float RISE = 3.0f;
    private static final float SEPARATOR_WIDTH = 7.0f;
    private static final float SEPARATOR_ALPHA = 0.55f;
    private static final float MIN_TAG_WIDTH = 8.0f;
    private static final Component SEPARATOR = Component.literal("·");

    private final Smooth swap = new Smooth(1.0f, SWAP_SPEED);
    private final Smooth room = new Smooth(0.0f, WIDTH_SPEED);

    private Component shown = Component.empty();
    private Component leaving = Component.empty();
    private String shownKey = "";
    private String leavingKey = "";
    private int shownTint;
    private int leavingTint;
    private float shownWidth;
    private float leavingWidth;

    // WHY: тег сравнивается ключом, а не текстом: в счёте атакующих меняются числа, и по тексту
    // WHY: «3 : 2» уезжало бы горением каждый раз, когда кто-то вошёл в зону
    public void want(String key, Component tag, int tint) {
        shownTint = tint;
        if (key.equals(shownKey)) {
            shown = tag;
            return;
        }

        leaving = shown;
        leavingKey = shownKey;
        leavingTint = shownTint;
        leavingWidth = shownWidth;
        shown = tag;
        shownKey = key;
        swap.snap(0.0f);
    }

    // WHY: место держит и уходящий тег, пока он не догорел, иначе длинное слово торчит за карточку
    public float advance(GuiGraphics graphics, float scale, float delta) {
        shownWidth = measure(graphics, scale);
        float phase = UiAnim.easeOut(swap.to(1.0f, delta));
        float widest = Math.max(shownWidth, leavingWidth * (1.0f - phase));
        return room.to(widest <= 0.0f ? 0.0f : widest + SEPARATOR_WIDTH, delta);
    }

    public float room() {
        return room.get();
    }

    public void paint(GuiGraphics graphics, float left, float textY, float scale, float alpha) {
        if (room.get() <= 0.1f) return;

        float phase = UiAnim.easeOut(swap.get());
        float presence = UiAnim.clamp01(room.get() / (SEPARATOR_WIDTH + MIN_TAG_WIDTH));
        float textLeft = left + SEPARATOR_WIDTH;

        UiRender.labelCentered(graphics, font(), SEPARATOR, left + SEPARATOR_WIDTH / 2.0f, textY, scale,
                UiTheme.alpha(shownTint, alpha * presence * SEPARATOR_ALPHA));

        if (phase < 0.999f && !leavingKey.isEmpty()) {
            UiRender.labelScaled(graphics, font(), leaving, textLeft, textY - phase * RISE, scale,
                    UiTheme.alpha(leavingTint, alpha * (1.0f - phase)));
        }
        if (!shownKey.isEmpty()) {
            UiRender.labelScaled(graphics, font(), shown, textLeft, textY + (1.0f - phase) * RISE, scale,
                    UiTheme.alpha(shownTint, alpha * phase));
        }
    }

    public void settle() {
        swap.snap(1.0f);
        room.snap(0.0f);
        leaving = Component.empty();
        shown = Component.empty();
        leavingKey = "";
        shownKey = "";
    }

    private float measure(GuiGraphics graphics, float scale) {
        if (shownKey.isEmpty()) return 0.0f;
        return UiRender.measure(graphics, font(), shown, scale);
    }

    private static net.minecraft.client.gui.Font font() {
        return Minecraft.getInstance().font;
    }
}
