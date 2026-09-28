package com.persiki84.battlecraft.client.island;

import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiRestFrame;
import net.minecraft.client.gui.GuiGraphics;

// WHY: морф таблетки в карточку и обратно по просьбе владельца идёт не перетеканием одних и тех
// WHY: же элементов, а через размытие, как у Apple: старое содержимое размывается и гаснет, форма
// WHY: меняется пустой, новое приходит из размытия, и размытие снимается только к самому концу.
// WHY: Закрытие сперва сжимает карточку в маленькую пустую таблетку и только потом раздвигает её
// WHY: под обложку, текст и полоски. Ход обратимый: повторный щелчок посреди морфа ведёт ту же
// WHY: дорожку назад, а не начинает другую, поэтому ни один элемент не прыгает
public final class IslandMorph {
    public static final float BLUR = 4.0f;
    private static final float SHRINK = 0.06f;
    private static final float OPEN_SECONDS = 0.42f;
    private static final float CLOSE_SECONDS = 0.5f;
    private static final float SWAP_SECONDS = 0.38f;
    private static final float HOLLOW = 0.02f;
    // WHY: владелец просил сжатие при закрытии мельче: пустая таблетка проходит лишь треть пути
    // WHY: к узкой, то есть на 15-25 % уже итоговой, и читается как вдох, а не провал
    private static final float SQUEEZE_DEPTH = 0.3f;
    private static final float OPEN_LEAVE = 0.32f;
    private static final float CLOSE_LEAVE = 0.28f;
    private static final float SWAP_HOLLOW = 0.4f;

    private enum Path { REST, OPEN, CLOSE, SWAP }

    private Path path = Path.REST;
    private boolean open;
    private float phase;
    private int heading = 1;
    private float lag;
    private float shape;
    private float squeeze;
    private float pill = 1.0f;
    private float card;
    private float blur;

    public void advance(boolean wanted, float delta) {
        steer(wanted);
        if (path != Path.REST) run(delta);
        sample();
    }

    public void swap() {
        if (path != Path.REST) return;

        path = Path.SWAP;
        phase = 0.0f;
        heading = 1;
        lag = 0.0f;
    }

    public void snap(boolean wanted) {
        open = wanted;
        path = Path.REST;
        phase = 0.0f;
        lag = 0.0f;
        sample();
    }

    private void steer(boolean wanted) {
        if (wanted == target()) return;

        if (path == Path.SWAP) {
            divert(wanted);
            return;
        }
        if (path == Path.REST) {
            path = wanted ? Path.OPEN : Path.CLOSE;
            phase = 0.0f;
            heading = 1;
            lag = 0.0f;
            return;
        }
        heading = -heading;
    }

    // WHY: щелчок посреди смены трека не ждёт её конца: морф подхватывает содержимое с той же
    // WHY: видимости (обратная smoothstep даёт долю ухода), а форма начинает свой ход с места
    // WHY: подхвата (lag), иначе она прыгнула бы на долю, которую успела бы пройти с начала морфа
    private void divert(boolean wanted) {
        float leaving = unsmooth(1.0f - (open ? card : pill));
        path = wanted ? Path.OPEN : Path.CLOSE;
        phase = leaving * (wanted ? OPEN_LEAVE : CLOSE_LEAVE);
        lag = phase;
        heading = 1;
    }

    private static float unsmooth(float share) {
        double turn = Math.asin(1.0 - 2.0 * UiAnim.clamp01(share)) / 3.0;
        return (float) (0.5 - Math.sin(turn));
    }

    private float lagged(float t) {
        if (lag <= 0.0f) return t;
        return UiAnim.clamp01((t - lag) / (1.0f - lag));
    }

    private boolean target() {
        if (path == Path.REST || path == Path.SWAP) return open;
        boolean forward = heading > 0;
        return path == Path.OPEN == forward;
    }

    private void run(float delta) {
        phase += heading * delta / seconds();
        if (phase >= 1.0f) {
            if (path != Path.SWAP) open = path == Path.OPEN;
            path = Path.REST;
            phase = 0.0f;
            lag = 0.0f;
            return;
        }
        if (phase > 0.0f) return;

        open = path == Path.CLOSE;
        path = Path.REST;
        phase = 0.0f;
        lag = 0.0f;
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
        pill = 1.0f - UiAnim.smoothstep(0.0f, OPEN_LEAVE, t);
        card = UiAnim.smoothstep(0.42f, 0.82f, t);
        shape = UiAnim.smoothstep(0.06f, 0.74f, lagged(t));
        squeeze = 0.0f;
        blur = Math.min(UiAnim.smoothstep(0.0f, 0.34f, t), 1.0f - UiAnim.smoothstep(0.42f, 1.0f, t));
    }

    private void closing(float t) {
        card = 1.0f - UiAnim.smoothstep(0.0f, CLOSE_LEAVE, t);
        shape = 1.0f - UiAnim.smoothstep(0.06f, 0.5f, lagged(t));
        squeeze = SQUEEZE_DEPTH * (1.0f - UiAnim.smoothstep(0.5f, 0.8f, lagged(t)));
        pill = UiAnim.smoothstep(0.56f, 0.86f, t);
        blur = Math.min(UiAnim.smoothstep(0.0f, 0.3f, t), 1.0f - UiAnim.smoothstep(0.56f, 1.0f, t));
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
