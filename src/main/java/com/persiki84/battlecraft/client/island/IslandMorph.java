package com.persiki84.battlecraft.client.island;

import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiRestFrame;
import net.minecraft.client.gui.GuiGraphics;

// WHY: морф таблетки в карточку и обратно по просьбе владельца идёт не перетеканием одних и тех
// WHY: же элементов, а через размытие, как у Apple: старое содержимое размывается и гаснет, форма
// WHY: меняется пустой, новое приходит из размытия, и размытие снимается только к самому концу.
// WHY: На столе закрытие сперва сжимает карточку в маленькую пустую таблетку и только потом
// WHY: раздвигает её под обложку, текст и полоски; остров HUD по слову владельца не сжимается
// WHY: вовсе, поэтому глубина сжатия задаётся экземпляру, а не зашита в ход
public final class IslandMorph {
    public static final float BLUR = 4.0f;
    private static final float SHRINK = 0.06f;
    private static final float OPEN_SECONDS = 0.42f;
    private static final float CLOSE_SECONDS = 0.5f;
    private static final float SWAP_SECONDS = 0.38f;
    private static final float HOLLOW = 0.02f;
    // WHY: владелец просил сжатие при закрытии мельче: пустая таблетка проходит лишь треть пути
    // WHY: к узкой, то есть на 15-25 % уже итоговой, и читается как вдох, а не провал
    private static final float DESK_SQUEEZE = 0.3f;
    private static final float OPEN_LEAVE = 0.32f;
    private static final float CLOSE_LEAVE = 0.28f;
    private static final float SQUEEZE_RISE = 0.3f;
    private static final float SQUEEZE_FALL = 0.4f;
    private static final float SWAP_HOLLOW = 0.4f;

    private enum Path { REST, OPEN, CLOSE, SWAP }

    private final float depth;
    private Path path = Path.REST;
    private boolean open;
    private float phase;
    private float shape;
    private float squeeze;
    private float pill = 1.0f;
    private float card;
    private float blur;
    private float fromShape;
    private float fromSqueeze;
    private float fromPill = 1.0f;
    private float fromCard;
    private float fromBlur;

    private IslandMorph(float depth) {
        this.depth = depth;
    }

    public static IslandMorph squeezing() {
        return new IslandMorph(DESK_SQUEEZE);
    }

    public static IslandMorph flat() {
        return new IslandMorph(0.0f);
    }

    public void advance(boolean wanted, float delta) {
        if (wanted != target()) depart(wanted ? Path.OPEN : Path.CLOSE);
        if (path != Path.REST) run(delta);
        sample();
    }

    public void swap() {
        if (path != Path.REST) return;

        depart(Path.SWAP);
    }

    public void snap(boolean wanted) {
        open = wanted;
        path = Path.REST;
        phase = 0.0f;
        sample();
        hold();
    }

    // WHY: щелчок посреди любого хода (раскрытия, закрытия, смены трека) не ведёт ту же дорожку
    // WHY: назад и не начинает новую с нуля: новый ход подхватывает каждый канал с его текущего
    // WHY: значения, поэтому закрытие посреди раскрытия идёт полным сценарием закрытия со сжатием,
    // WHY: а первый кадр совпадает с последним кадром прерванного хода
    private void depart(Path next) {
        hold();
        path = next;
        phase = 0.0f;
    }

    private void hold() {
        fromShape = shape;
        fromSqueeze = squeeze;
        fromPill = pill;
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

        if (path != Path.SWAP) open = path == Path.OPEN;
        path = Path.REST;
        phase = 0.0f;
    }

    private float seconds() {
        return switch (path) {
            case OPEN -> OPEN_SECONDS;
            case CLOSE -> CLOSE_SECONDS;
            default -> SWAP_SECONDS;
        };
    }

    private void sample() {
        switch (path) {
            case OPEN -> opening(phase);
            case CLOSE -> closing(phase);
            case SWAP -> swapping(phase);
            default -> still();
        }
    }

    private void still() {
        shape = open ? 1.0f : 0.0f;
        squeeze = 0.0f;
        pill = open ? 0.0f : 1.0f;
        card = open ? 1.0f : 0.0f;
        blur = 0.0f;
    }

    private void opening(float t) {
        pill = fromPill * (1.0f - UiAnim.smoothstep(0.0f, OPEN_LEAVE, t));
        card = arrive(fromCard, UiAnim.smoothstep(0.42f, 0.82f, t));
        shape = arrive(fromShape, UiAnim.smoothstep(0.06f, 0.74f, t));
        squeeze = fromSqueeze * (1.0f - UiAnim.smoothstep(0.0f, SQUEEZE_FALL, t));
        float fog = arrive(fromBlur, UiAnim.smoothstep(0.0f, 0.34f, t));
        blur = Math.min(fog, 1.0f - UiAnim.smoothstep(0.42f, 1.0f, t));
    }

    private void closing(float t) {
        card = fromCard * (1.0f - UiAnim.smoothstep(0.0f, CLOSE_LEAVE, t));
        shape = fromShape * (1.0f - UiAnim.smoothstep(0.06f, 0.5f, t));
        float inhale = fromSqueeze + (depth - fromSqueeze) * UiAnim.smoothstep(0.0f, SQUEEZE_RISE, t);
        squeeze = inhale * (1.0f - UiAnim.smoothstep(0.5f, 0.8f, t));
        float lingering = fromPill * (1.0f - UiAnim.smoothstep(0.0f, CLOSE_LEAVE, t));
        pill = lingering + UiAnim.smoothstep(0.56f, 0.86f, t);
        float fog = arrive(fromBlur, UiAnim.smoothstep(0.0f, 0.3f, t));
        blur = Math.min(fog, 1.0f - UiAnim.smoothstep(0.56f, 1.0f, t));
    }

    private static float arrive(float from, float share) {
        return from + (1.0f - from) * share;
    }

    private void swapping(float t) {
        float presence = 1.0f - UiAnim.smoothstep(0.0f, SWAP_HOLLOW, t) + UiAnim.smoothstep(0.5f, 0.9f, t);
        shape = open ? 1.0f : 0.0f;
        squeeze = 0.0f;
        pill = open ? 0.0f : presence;
        card = open ? presence : 0.0f;
        blur = Math.min(UiAnim.smoothstep(0.0f, SWAP_HOLLOW, t), 1.0f - UiAnim.smoothstep(0.5f, 1.0f, t));
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

    public boolean hollow() {
        return pill <= HOLLOW && card <= HOLLOW;
    }

    public boolean opened() {
        return path == Path.REST && open;
    }

    public float shape() {
        return shape;
    }

    public float squeeze() {
        return squeeze;
    }

    public float pill() {
        return pill;
    }

    public float card() {
        return card;
    }

    public float blur() {
        return blur;
    }
}
