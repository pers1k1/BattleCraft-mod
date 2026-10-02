package com.persiki84.battlecraft.client.island;

import com.persiki84.shared.client.ui.Spring;

// WHY: замеры с записи iOS: нажатие вздувает остров на 9 % по ширине и 6 % по высоте пружиной без
// WHY: отскока, отпускание возвращает его с лёгким отскоком. Смена трека в покое даёт толчок: пик
// WHY: около 6 % и 5 % через 0.15 с и возврат с недолётом около десятой доли пика
public final class IslandSwell {
    private static final float PRESS_WIDTH = 0.09f;
    private static final float PRESS_HEIGHT = 0.06f;
    private static final float KICK_WIDTH = 0.06f;
    private static final float KICK_HEIGHT = 0.05f;
    private static final float PRESS_RESPONSE = 0.35f;
    private static final float PRESS_DAMPING = 1.0f;
    private static final float RELEASE_DAMPING = 0.7f;
    // WHY: пик у затухающей пружины из покоя с начальной скоростью v0 приходит в момент
    // WHY: atan(sqrt(1 - z^2) / z) / wd; при z = 0.6 и пике на 0.15 с это response 0.81 с,
    // WHY: а единичный пик даёт v0 = 15.5
    private static final float KICK_RESPONSE = 0.81f;
    private static final float KICK_DAMPING = 0.6f;
    private static final float KICK_SPEED = 15.5f;

    private final Spring pressing = new Spring(PRESS_RESPONSE, PRESS_DAMPING, 0.0f);
    private final Spring releasing = new Spring(PRESS_RESPONSE, RELEASE_DAMPING, 0.0f);
    private final Spring kick = new Spring(KICK_RESPONSE, KICK_DAMPING, 0.0f);
    private Spring press = releasing;
    private boolean held;

    public void press(boolean down) {
        if (held == down) return;

        held = down;
        Spring next = down ? pressing : releasing;
        next.take(press);
        press = next;
    }

    public void kick() {
        kick.shove(KICK_SPEED);
    }

    public void advance(float delta) {
        press.to(held ? 1.0f : 0.0f, delta);
        kick.to(0.0f, delta);
    }

    public void snap() {
        held = false;
        press = releasing;
        releasing.snap(0.0f);
        pressing.snap(0.0f);
        kick.snap(0.0f);
    }

    public float width() {
        return 1.0f + PRESS_WIDTH * press.get() + KICK_WIDTH * kick.get();
    }

    public float height() {
        return 1.0f + PRESS_HEIGHT * press.get() + KICK_HEIGHT * kick.get();
    }

    public boolean resting() {
        return !held && press.get() == 0.0f && press.velocity() == 0.0f
                && kick.get() == 0.0f && kick.velocity() == 0.0f;
    }
}
