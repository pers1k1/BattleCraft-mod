package com.persiki84.battlecraft.client.island;

import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiRestFrame;
import net.minecraft.client.gui.GuiGraphics;

// WHY: владелец просил, чтобы содержимое острова не пропадало ни в каком случае: таблетка и
// WHY: карточка перетекают друг в друга одной долей (сумма их видимости всегда 1), форма растёт
// WHY: вместе с ними. Размытие при этом идёт весь ход: набирается в начале и снимается ровно к
// WHY: концу. Смена трека содержимое не убирает: обложка, визуализатор и строки остаются на месте
// WHY: и только проходят через такое же размытие. Щелчок посреди хода подхватывает каждый канал
// WHY: с его текущего значения, без скачка
public final class IslandMorph {
    public static final float BLUR = 4.0f;
    private static final float SHRINK = 0.04f;
    private static final float OPEN_SECONDS = 0.34f;
    private static final float CLOSE_SECONDS = 0.34f;
    private static final float BLEND_FROM = 0.12f;
    private static final float BLEND_TO = 0.78f;
    private static final float SHAPE_TO = 0.8f;
    private static final float PULSE_SECONDS = 0.3f;
    private static final float FOG_RISE = 0.25f;
    private static final float FOG_CLEAR = 0.55f;

    private enum Path { REST, OPEN, CLOSE, PULSE }

    private Path path = Path.REST;
    private boolean open;
    private float phase;
    private float shape;
    private float card;
    private float blur;
    private float fromShape;
    private float fromCard;
    private float fromBlur;

    public void advance(boolean wanted, float delta) {
        if (wanted != target()) depart(wanted ? Path.OPEN : Path.CLOSE);
        if (path != Path.REST) run(delta);
        sample();
    }

    public void pulse() {
        if (path == Path.REST) depart(Path.PULSE);
    }

    public void snap(boolean wanted) {
        open = wanted;
        path = Path.REST;
        phase = 0.0f;
        sample();
        hold();
    }

    private void depart(Path next) {
        hold();
        path = next;
        phase = 0.0f;
    }

    private void hold() {
        fromShape = shape;
        fromCard = card;
        fromBlur = blur;
    }

    private boolean target() {
        return switch (path) {
            case OPEN -> true;
            case CLOSE -> false;
            default -> open;
        };
    }

    private void run(float delta) {
        phase += delta / seconds();
        if (phase < 1.0f) return;

        if (path != Path.PULSE) open = path == Path.OPEN;
        path = Path.REST;
        phase = 0.0f;
    }

    private float seconds() {
        return switch (path) {
            case OPEN -> OPEN_SECONDS;
            case CLOSE -> CLOSE_SECONDS;
            default -> PULSE_SECONDS;
        };
    }

    private void sample() {
        if (path == Path.REST) {
            shape = open ? 1.0f : 0.0f;
            card = open ? 1.0f : 0.0f;
            blur = 0.0f;
            return;
        }
        float fog = fromBlur + (1.0f - fromBlur) * UiAnim.smoothstep(0.0f, FOG_RISE, phase);
        blur = Math.min(fog, 1.0f - UiAnim.smoothstep(FOG_CLEAR, 1.0f, phase));
        if (path == Path.PULSE) return;

        float goal = path == Path.OPEN ? 1.0f : 0.0f;
        card = fromCard + (goal - fromCard) * UiAnim.smoothstep(BLEND_FROM, BLEND_TO, phase);
        shape = fromShape + (goal - fromShape) * UiAnim.smoothstep(0.0f, SHAPE_TO, phase);
    }

    // WHY: уходящая и приходящая группа чуть сжимается и сдвигается вокруг своей середины: ход
    // WHY: заявляется через позу покоя, иначе строки внутри шли бы ступенями по пикселю
    public static void pose(GuiGraphics graphics, float pivotX, float pivotY, float presence, float drift) {
        float absent = 1.0f - UiAnim.clamp01(presence);
        float scale = 1.0f - SHRINK * absent;
        UiRestFrame.push(graphics, pivotX, pivotY, scale, scale, 0.0f, drift * absent);
    }

    public boolean resting() {
        return path == Path.REST;
    }

    public boolean opened() {
        return path == Path.REST && open;
    }

    public float shape() {
        return shape;
    }

    public float pill() {
        return 1.0f - card;
    }

    public float card() {
        return card;
    }

    public float blur() {
        return blur;
    }
}
