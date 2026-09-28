package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.shared.client.ui.Spring;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiReveal;
import com.persiki84.shared.client.ui.UiSound;
import net.minecraft.client.gui.GuiGraphics;

// WHY: всплывающие панели строки меню ведут себя как в macOS: панель вырастает из-под своего
// WHY: пункта с 0.9 масштаба и проявляется, а плитки внутри догоняют её каскадом по рядам.
// WHY: Уход короче и без упругости: у открытия и закрытия свои пружины, и та, что берёт ход,
// WHY: подхватывает значение и скорость прежней, поэтому смена направления посреди хода не рвётся
final class DesktopPopover {
    private static final float BORN_SCALE = 0.9f;
    private static final float DEPTH = 300.0f;
    private static final float FADE_GAIN = 1.4f;
    private static final float ROW_LEAD = 0.05f;
    private static final float ROW_STEP = 0.016f;
    private static final float ROW_FADE = 0.18f;
    private static final float ROW_DRIFT = 2.5f;
    private static final float CASCADE_DONE = 1.0f;
    private static final float SETTLED_SHARE = 0.5f;
    private static final float GONE = 0.004f;

    private final Spring opening = new Spring(0.34f, 0.86f, 0.0f);
    private final Spring closing = new Spring(0.2f, 1.0f, 0.0f);
    private boolean open;
    private float presence;
    private float scale = BORN_SCALE;
    private float seconds = CASCADE_DONE;
    private float outerPresence = 1.0f;

    boolean open() {
        return open;
    }

    boolean visible() {
        return open || presence > GONE;
    }

    void toggle() {
        if (open) {
            hide();
        } else {
            show();
        }
        UiSound.press();
    }

    void show() {
        if (open) return;
        open = true;
        opening.take(closing);
        seconds = presence > SETTLED_SHARE ? CASCADE_DONE : 0.0f;
    }

    void hide() {
        if (!open) return;
        open = false;
        closing.take(opening);
    }

    // WHY: экран, ушедший под другой, не двигает свои пружины, и начатое закрытие доигрывалось бы
    // WHY: призраком панели уже после возврата на стол
    void dismiss() {
        open = false;
        opening.snap(0.0f);
        closing.snap(0.0f);
        presence = 0.0f;
        scale = BORN_SCALE;
    }

    private Spring driving() {
        return open ? opening : closing;
    }

    void advance(float delta) {
        float value = driving().to(open ? 1.0f : 0.0f, delta);
        presence = UiAnim.clamp01(value);
        scale = BORN_SCALE + (1.0f - BORN_SCALE) * Math.min(1.0f, value);
        if (open) seconds = Math.min(CASCADE_DONE, seconds + delta);
    }

    float alpha() {
        return UiAnim.clamp01(presence * FADE_GAIN);
    }

    float row(int index) {
        if (!open) return 1.0f;
        float start = ROW_LEAD + index * ROW_STEP;
        return UiAnim.smoothstep(start, start + ROW_FADE, seconds);
    }

    float drift(int index) {
        return (1.0f - row(index)) * ROW_DRIFT;
    }

    // WHY: линза стекла панели набирает и теряет силу вместе с её появлением: пара begin/end уже
    // WHY: стоит у вызывающих в try/finally, поэтому доля стекла заявляется и снимается в ней же
    void begin(GuiGraphics graphics, float pivotX, float pivotY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, DEPTH);
        UiRestFrame.push(graphics, pivotX, pivotY, scale, scale, 0.0f, 0.0f);
        outerPresence = UiGlassStyle.scalePresence(UiReveal.glassPresence(presence));
    }

    void end(GuiGraphics graphics) {
        UiGlassStyle.restorePresence(outerPresence);
        UiRestFrame.pop(graphics);
        graphics.pose().popPose();
    }
}
