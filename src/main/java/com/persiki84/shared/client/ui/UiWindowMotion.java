package com.persiki84.shared.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

// WHY: малое окно приходит и уходит выбранным у игрока набором движения, как окна экранов: сборка
// WHY: из света, иней или раскол. Жизнь окна вынесена из окна палитры, чтобы любое малое окно жило
// WHY: теми же кривыми, а не своей копией. Набор защёлкивается на каждой стадии отдельно: смена его
// WHY: в кастомизации не ломает уже идущее появление или уход
public final class UiWindowMotion {
    private static final float MAX_STEP = 1.0f / 15.0f;
    private static final float MIN_FRAMES = 6.0f;
    private static final float BURN_MARGIN = 28.0f;
    private static final float WINDOW_CHARGE = 0.55f;

    private final float enterSeconds;
    private final float burnSeconds;
    private UiMotionSet set;
    private float entered;
    private float burned = -1.0f;
    private long stamp = -1L;
    private boolean fresh = true;
    private float leavingArrival = 1.0f;
    private UiShards shards;
    private long grain;

    public interface Painter {
        void paint(GuiGraphics graphics, float left, float top);
    }

    public UiWindowMotion(float enterSeconds, float burnSeconds) {
        this.enterSeconds = enterSeconds;
        this.burnSeconds = burnSeconds;
        this.set = chosen();
    }

    private static UiMotionSet chosen() {
        return UiMotion.of(Minecraft.getInstance().screen);
    }

    public boolean closing() {
        return burned >= 0.0f;
    }

    public boolean finished() {
        return burned >= leaveSpan();
    }

    // WHY: уход, начатый посреди входа, остаётся в наборе входа: доля прихода относится к его кривой,
    // WHY: и чужой набор начал бы уход скачком
    public void beginClose() {
        if (closing()) return;
        leavingArrival = animating() ? phase() : 1.0f;
        fresh = true;
        burned = 0.0f;
        if (leavingArrival >= 1.0f) set = chosen();
        shards = null;
        grain = System.nanoTime();
    }

    public void finish() {
        burned = leaveSpan();
    }

    private float enterSpan() {
        return set.enterSeconds(enterSeconds);
    }

    private float leaveSpan() {
        return set == UiMotionSet.IGNITE ? burnSeconds : set.leaveSeconds(Minecraft.getInstance().level != null);
    }

    // WHY: шаг кадра один на окно, даже если рисуется дважды; возвращается, чтобы окно вело по нему
    // WHY: свои мелкие анимации тем же временем
    public float advance() {
        long frame = UiFrame.frame();
        if (frame == stamp) return 0.0f;
        stamp = frame;
        float step = fresh ? 0.0f : Math.min(MAX_STEP, UiFrame.delta());
        fresh = false;
        if (closing()) {
            burned += Math.min(step, leaveSpan() / MIN_FRAMES);
        } else {
            entered += Math.min(step, enterSpan() / MIN_FRAMES);
        }
        return step;
    }

    public boolean animating() {
        if (!UiReveal.enabled()) return false;
        return closing() || entered < enterSpan();
    }

    public float phase() {
        return closing() ? UiAnim.clamp01(burned / leaveSpan()) : UiAnim.clamp01(entered / enterSpan());
    }

    // WHY: время горения продолжает время входа: шейдер восстанавливает недоигранный фронт входа
    // WHY: по тому же шуму, и с нуля он встал бы на другой рисунок
    private float seconds() {
        return closing() ? entered + burned : entered;
    }

    // WHY: сила стекла растёт с фазой входа и гаснет с фазой ухода; уход, начатый посреди входа,
    // WHY: гасит ту силу, что успела набраться, а не полную, иначе на первом кадре был бы скачок
    public float presence() {
        if (!animating()) return 1.0f;
        if (closing()) return UiReveal.glassPresence(leavingArrival) * UiReveal.glassPresence(1.0f - phase());
        return UiReveal.glassPresence(phase());
    }

    // WHY: окно приходит сборкой и уходит горением или расколом, а не альфой, поэтому ореол гаснет по
    // WHY: фазе анимации; уход посреди входа гасит ту долю, что успела набраться. Иней гасит тело окна
    // WHY: своей кривой (frostCloseOpacity в ui_assemble.fsh), и ореол гаснет по ней же
    public float hushShown() {
        if (!closing()) return UiAnim.easeOut(phase());
        float left = 1.0f - phase();
        float body = set == UiMotionSet.FROST ? UiReveal.frostLeaveOpacity(1.0f - left) : UiAnim.easeOut(left);
        return UiAnim.easeOut(leavingArrival) * body;
    }

    public boolean splitting() {
        return closing() && set == UiMotionSet.GLASS;
    }

    // WHY: замощение нужно раньше композита: через него переносятся и копия кадра в стадии окна, и
    // WHY: снимок подложки, иначе мир за улетевшим осколком остаётся с места, где ячейка была
    public UiShards field(float left, float top, float width, float height) {
        if (shards == null) shards = UiShards.of(left, top, width, height, grain);
        return shards;
    }

    // WHY: у раскола осколки уходят за габарит окна, поэтому он рисуется треугольниками по своим
    // WHY: местам, а не композитным квадом по прямоугольнику окна, как вход из света и горение
    public void resolve(GuiGraphics graphics, float screenWidth, float screenHeight, float left, float top,
                        float width, float height) {
        if (splitting()) {
            UiShatter.draw(graphics, screenWidth, screenHeight, UiStage.windowTexture(),
                    field(left, top, width, height), phase(), leavingArrival);
            return;
        }
        float mode = closing() ? leaveMode() : set.enterMode();
        UiAssemble.window(graphics, screenWidth, screenHeight, UiStage.windowTexture(), left, top, width, height,
                BURN_MARGIN, phase(), seconds(), mode, WINDOW_CHARGE, closing() ? leavingArrival : 1.0f);
    }

    private float leaveMode() {
        return set == UiMotionSet.FROST ? UiReveal.FROST_LEAVE : UiReveal.BURN;
    }

    // WHY: целый проход малого окна без своих эффектов переноса: подложка, ореол, стадия, композит.
    // WHY: Окно, которому шейдеры недоступны, рисуется плоско и уходит сразу
    public void render(GuiGraphics graphics, float screenWidth, float screenHeight, float left, float top,
                       float width, float height, float radius, Painter painter) {
        advance();
        graphics.flush();
        UiShards split = splitting() ? field(left, top, width, height) : null;
        UiBackdrop.scatter(null, split, phase(), screenWidth, screenHeight);
        if (!closing() || UiReveal.enabled()) UiGlass.hush(graphics, left, top, width, height, radius, hushShown());
        boolean staged = animating() && UiStage.beginWindow(split, phase(), screenWidth, screenHeight);
        if (closing() && !staged) {
            finish();
            UiBackdrop.plain();
            return;
        }
        float outer = UiGlassStyle.scalePresence(staged ? presence() : 1.0f);
        try {
            painter.paint(graphics, left, top);
        } finally {
            UiGlassStyle.restorePresence(outer);
            UiBackdrop.plain();
            if (staged) {
                graphics.flush();
                UiStage.endWindow();
            }
        }
        if (staged) resolve(graphics, screenWidth, screenHeight, left, top, width, height);
    }
}
