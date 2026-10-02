package com.persiki84.battlecraft.client.island;

import com.persiki84.shared.client.ui.Spring;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiRestFrame;
import net.minecraft.client.gui.GuiGraphics;

// WHY: ход снят покадрово с записи iOS (60 кадров в секунду). Раскрытие: таблетка за 0.2 с сжимается
// WHY: до 0.815 ширины и 0.94 высоты, затем форма идёт к карточке пружиной response 0.51 с, damping
// WHY: 0.815 с перелётом около процента. Сворачивание идёт сразу пружиной 0.47 с, damping 0.86.
// WHY: Владелец 03.10.2026: элементы не убираются, на ходу размывается весь остров. Одна кривая
// WHY: размытия окна поднимается за 0.12 с и снимается за последние 0.2 с хода, а обе раскладки
// WHY: перетекают внахлёст под её пиком, поэтому пустого стекла не бывает ни в один кадр. Щелчок
// WHY: посреди хода подхватывает пружину с её скоростью, а доли и размытие с их текущих значений
public final class IslandMorph {
    public static final float BLUR = 4.0f;
    public static final float SQUEEZE_WIDTH = 0.815f;
    public static final float SQUEEZE_HEIGHT = 0.94f;
    private static final float SQUEEZE_SECONDS = 0.2f;
    private static final float ANTICIPATE_BELOW = 0.05f;
    private static final float OPEN_RESPONSE = 0.51f;
    private static final float OPEN_DAMPING = 0.815f;
    private static final float CLOSE_RESPONSE = 0.47f;
    private static final float CLOSE_DAMPING = 0.86f;
    private static final float FOG_IN = 0.12f;
    private static final float FOG_OUT = 0.2f;
    private static final float OPEN_SECONDS = 0.32f;
    private static final float CLOSE_SECONDS = 0.36f;
    private static final float OPEN_BLEND_LEAD = 0.08f;
    private static final float OPEN_BLEND_FLOOR = 0.02f;
    private static final float OPEN_BLEND_TAIL = 0.12f;
    private static final float CLOSE_BLEND_FROM = 0.04f;
    private static final float CLOSE_BLEND_TO = 0.22f;
    private static final float PULSE_SECONDS = 0.3f;
    private static final float FOG_RISE = 0.25f;
    private static final float FOG_CLEAR = 0.55f;

    private enum Path { REST, OPEN, CLOSE }

    private final Spring opening = new Spring(OPEN_RESPONSE, OPEN_DAMPING, 0.0f);
    private final Spring closing = new Spring(CLOSE_RESPONSE, CLOSE_DAMPING, 0.0f);
    private final Spring relaxing = new Spring(CLOSE_RESPONSE, CLOSE_DAMPING, 0.0f);
    private final IslandSwell swell = new IslandSwell();
    private Spring shaper = closing;
    private Path path = Path.REST;
    private boolean open;
    private float elapsed;
    private float lead;
    private float squeeze;
    private float fromSqueeze;
    private float pill = 1.0f;
    private float card;
    private float fromPill = 1.0f;
    private float fromCard;
    private float fromFog;
    private float fog;
    private float pulse = 1.0f;

    public void advance(boolean wanted, float delta) {
        if (wanted != target()) depart(wanted);
        elapsed += delta;
        swell.advance(delta);
        pulse = Math.min(1.0f, pulse + delta / PULSE_SECONDS);
        if (path == Path.OPEN) stepOpen(delta);
        if (path == Path.CLOSE) stepClose(delta);
        sample();
        settle();
    }

    public void pulse() {
        swell.kick();
        if (path == Path.REST) pulse = 0.0f;
    }

    public void press(boolean down) {
        swell.press(down);
    }

    public void snap(boolean wanted) {
        open = wanted;
        path = Path.REST;
        shaper.snap(wanted ? 1.0f : 0.0f);
        squeeze = 0.0f;
        pulse = 1.0f;
        swell.snap();
        sample();
    }

    private boolean target() {
        return switch (path) {
            case OPEN -> true;
            case CLOSE -> false;
            default -> open;
        };
    }

    private void depart(boolean wanted) {
        fromPill = pill;
        fromCard = card;
        fromFog = fog;
        fromSqueeze = squeeze;
        elapsed = 0.0f;
        Spring next = wanted ? opening : closing;
        next.take(shaper);
        shaper = next;
        path = wanted ? Path.OPEN : Path.CLOSE;
        lead = wanted && shaper.get() < ANTICIPATE_BELOW ? SQUEEZE_SECONDS : 0.0f;
        if (lead > 0.0f) shaper.snap(shaper.get());
        if (!wanted) relaxing.snap(squeeze);
    }

    private void stepOpen(float delta) {
        if (elapsed < lead) {
            squeeze = fromSqueeze + (1.0f - fromSqueeze) * UiAnim.smoothstep(0.0f, lead, elapsed);
            return;
        }
        shaper.to(1.0f, delta);
    }

    private void stepClose(float delta) {
        shaper.to(0.0f, delta);
        squeeze = relaxing.to(0.0f, delta);
    }

    private void sample() {
        switch (path) {
            case OPEN -> blend(UiAnim.smoothstep(Math.max(OPEN_BLEND_FLOOR, lead - OPEN_BLEND_LEAD),
                    lead + OPEN_BLEND_TAIL, elapsed), true);
            case CLOSE -> blend(UiAnim.smoothstep(CLOSE_BLEND_FROM, CLOSE_BLEND_TO, elapsed), false);
            default -> sampleRest();
        }
        if (path != Path.REST) fog = fogAt(length());
    }

    private void blend(float share, boolean toCard) {
        card = toCard ? fromCard + (1.0f - fromCard) * share : fromCard * (1.0f - share);
        pill = toCard ? fromPill * (1.0f - share) : fromPill + (1.0f - fromPill) * share;
    }

    private float fogAt(float length) {
        float rise = fromFog + (1.0f - fromFog) * UiAnim.smoothstep(0.0f, FOG_IN, elapsed);
        return rise * (1.0f - UiAnim.smoothstep(length - FOG_OUT, length, elapsed));
    }

    private float length() {
        return path == Path.OPEN ? lead + OPEN_SECONDS : CLOSE_SECONDS;
    }

    private void sampleRest() {
        pill = open ? 0.0f : 1.0f;
        card = open ? 1.0f : 0.0f;
        fog = 0.0f;
    }

    private void settle() {
        if (path == Path.REST) return;

        float goal = path == Path.OPEN ? 1.0f : 0.0f;
        if (elapsed < length() || shaper.get() != goal || shaper.velocity() != 0.0f) return;
        if (path == Path.CLOSE && squeeze != 0.0f) return;

        open = path == Path.OPEN;
        path = Path.REST;
        sampleRest();
    }

    // WHY: содержимое состояния стоит в своей итоговой раскладке, а форма в кадре другого размера:
    // WHY: раскладка масштабируется вокруг своей середины и ставится в середину живой формы
    public static void fit(GuiGraphics graphics, float centerX, float layoutCenterY, float shapeCenterY,
                           float scale) {
        UiRestFrame.push(graphics, centerX, layoutCenterY, scale, scale, 0.0f, shapeCenterY - layoutCenterY);
    }

    public static float radius(float height, float pillHeight) {
        return Math.min(height / 2.0f, pillHeight / 2.0f + (height - pillHeight) * 0.166f);
    }

    public float width(float pillWidth, float cardWidth) {
        float squeezed = pillWidth * (1.0f - squeeze * (1.0f - SQUEEZE_WIDTH));
        return (squeezed + (cardWidth - squeezed) * shaper.get()) * swell.width();
    }

    public float height(float pillHeight, float cardHeight) {
        float squeezed = pillHeight * (1.0f - squeeze * (1.0f - SQUEEZE_HEIGHT));
        return (squeezed + (cardHeight - squeezed) * shaper.get()) * swell.height();
    }

    public boolean veiling() {
        return path != Path.REST || pulse < 1.0f;
    }

    public boolean resting() {
        return !veiling() && swell.resting();
    }

    public boolean opened() {
        return path == Path.REST && open;
    }

    public float pill() {
        return UiAnim.clamp01(pill);
    }

    public float card() {
        return UiAnim.clamp01(card);
    }

    public float blur() {
        float swap = Math.min(UiAnim.smoothstep(0.0f, FOG_RISE, pulse),
                1.0f - UiAnim.smoothstep(FOG_CLEAR, 1.0f, pulse));
        return UiAnim.clamp01(Math.max(swap, fog));
    }
}
