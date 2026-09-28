package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.shared.client.ui.UiAnim;

// WHY: сон стола ведёт одна кривая: доля хода идёт по времени, а не пружиной, потому что перелёт
// WHY: пружины толкнул бы размытие фона за предел и вернул бы док на миг над строкой. Всё
// WHY: остальное выводится из этой доли по своим отрезкам: строка меню и док уходят в первой
// WHY: половине, подсказка проявляется последней, и обратный ход разворачивает тот же порядок
final class DesktopSleep {
    private static final long IDLE_NANOS = 120_000_000_000L;
    private static final float FALL_SECONDS = 0.9f;
    private static final float WAKE_SECONDS = 0.5f;
    private static final float CHROME_FROM = 0.05f;
    private static final float CHROME_UNTIL = 0.55f;
    private static final float HINT_FROM = 0.7f;
    // WHY: пока строка меню и док видны меньше чем наполовину, щелчок по ним был бы щелчком вслепую
    private static final float BLIND_ABOVE = 0.3f;
    private static final double MOVE_PIXELS = 2.0;
    private static final int NOTHING = -1;

    private long lastActivity = System.nanoTime();
    private boolean asleep;
    private float progress;
    private boolean anchored;
    private double anchorX;
    private double anchorY;
    private int heldKey = NOTHING;
    private int heldButton = NOTHING;

    boolean advance(boolean held, float delta) {
        long now = System.nanoTime();
        if (held) lastActivity = now;
        boolean falling = !asleep && now - lastActivity >= IDLE_NANOS;
        if (falling) asleep = true;
        progress = asleep
                ? Math.min(1.0f, progress + delta / FALL_SECONDS)
                : Math.max(0.0f, progress - delta / WAKE_SECONDS);
        return falling;
    }

    float amount() {
        return UiAnim.smoothstep(0.0f, 1.0f, progress);
    }

    float chromeGone() {
        return UiAnim.smoothstep(CHROME_FROM, CHROME_UNTIL, progress);
    }

    float hint() {
        return UiAnim.smoothstep(HINT_FROM, 1.0f, progress);
    }

    boolean resting() {
        return asleep || progress > BLIND_ABOVE;
    }

    boolean stir() {
        boolean blind = resting();
        lastActivity = System.nanoTime();
        asleep = false;
        return blind;
    }

    boolean moved(double mouseX, double mouseY, double guiScale) {
        if (!anchored) {
            anchor(mouseX, mouseY);
            return false;
        }
        double travelX = (mouseX - anchorX) * guiScale;
        double travelY = (mouseY - anchorY) * guiScale;
        if (travelX * travelX + travelY * travelY < MOVE_PIXELS * MOVE_PIXELS) return false;

        anchor(mouseX, mouseY);
        return true;
    }

    private void anchor(double mouseX, double mouseY) {
        anchored = true;
        anchorX = mouseX;
        anchorY = mouseY;
    }

    boolean wakeByButton(int button) {
        if (!stir()) return false;

        heldButton = button;
        return true;
    }

    boolean holdsButton(int button) {
        return heldButton != NOTHING && heldButton == button;
    }

    boolean releaseButton(int button) {
        if (!holdsButton(button)) return false;

        heldButton = NOTHING;
        return true;
    }

    // WHY: клавиша, разбудившая стол, следом присылает символ и повторы, пока её держат: без
    // WHY: удержания буква пробуждения тут же открыла бы поиск. Другая клавиша перебивает автоповтор
    // WHY: удержанной, и её символ уже не от пробуждения: при быстром наборе пробел Ctrl+Space ещё
    // WHY: не отпущен, когда нажата первая буква запроса
    boolean wakeByKey(int key) {
        if (heldKey != NOTHING && heldKey == key) return true;
        heldKey = NOTHING;
        if (!stir()) return false;

        holdKey(key);
        return true;
    }

    // WHY: так же держится клавиша, пропустившая вход, и пробел сочетания поиска: их символ приходит
    // WHY: следом за нажатием и без удержания стал бы первой буквой поиска
    void holdKey(int key) {
        heldKey = key;
    }

    boolean holdsTyping() {
        return heldKey != NOTHING;
    }

    boolean releaseKey(int key) {
        if (heldKey == NOTHING || heldKey != key) return false;

        heldKey = NOTHING;
        return true;
    }

    void reset() {
        lastActivity = System.nanoTime();
        asleep = false;
        progress = 0.0f;
        anchored = false;
        heldKey = NOTHING;
        heldButton = NOTHING;
    }
}
