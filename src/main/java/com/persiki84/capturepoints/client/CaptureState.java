package com.persiki84.capturepoints.client;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiTicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// WHY: слова «ПАУЗА» и «ОТКАТ» менялись кадром и читались как дефект: тег уезжает вверх и гаснет,
// WHY: новый приходит снизу, а место под них едет шириной, иначе карточка прыгает на смене. Числа
// WHY: внутри одного тега (секунды до старта, счёт атакующих) меняются тикером: уходят только
// WHY: сменившиеся цифры, а не весь тег
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

    private UiTicker shown = new UiTicker();
    private UiTicker leaving = new UiTicker();
    private String shownKey = "";
    private String leavingKey = "";
    private int shownTint;
    private int leavingTint;
    private float shownWidth;
    private float leavingWidth;

    // WHY: тег сравнивается ключом, а не текстом: в счёте атакующих меняются числа, и по тексту
    // WHY: «3 : 2» уезжало бы горением каждый раз, когда кто-то вошёл в зону. Строка собирается
    // WHY: форматом только на смене числа
    public void want(String key, long value, UiTicker.Format format, int tint) {
        shownTint = tint;
        if (key.equals(shownKey)) {
            if (!key.isEmpty()) shown.set(value, format);
            return;
        }

        UiTicker spare = leaving;
        leaving = shown;
        shown = spare;
        leavingKey = shownKey;
        leavingTint = shownTint;
        leavingWidth = shownWidth;
        shownKey = key;
        if (!key.isEmpty()) shown.snap(value, format);
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
            leaving.draw(graphics, font(), textLeft, textY - phase * RISE, scale, leavingTint,
                    alpha * (1.0f - phase));
        }
        if (!shownKey.isEmpty()) {
            shown.draw(graphics, font(), textLeft, textY + (1.0f - phase) * RISE, scale, shownTint, alpha * phase);
        }
    }

    public void settle() {
        swap.snap(1.0f);
        room.snap(0.0f);
        shown.reset();
        leaving.reset();
        leavingKey = "";
        shownKey = "";
    }

    private float measure(GuiGraphics graphics, float scale) {
        if (shownKey.isEmpty()) return 0.0f;
        return shown.steadyWidth(graphics, font(), scale);
    }

    private static net.minecraft.client.gui.Font font() {
        return Minecraft.getInstance().font;
    }
}
