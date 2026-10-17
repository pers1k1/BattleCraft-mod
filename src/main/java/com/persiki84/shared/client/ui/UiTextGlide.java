package com.persiki84.shared.client.ui;

// WHY: начало строки ставится на целый пиксель покоя ради чёткости. Строку, чьё место едет мимо
// WHY: заявленного хода (координата из Smooth, камера, голая поза), такая привязка ведёт
// WHY: ступенями, и последний шаг хвоста приходит рывком, когда всё вокруг уже стоит: стенд
// WHY: Z:\tmp\smm_textflex2 дал лишний скачок до 1 px в 100 % въездов строки меню по координате.
// WHY: Здесь строка помнит, где была прошлым кадром: сдвинулась - рисуется в живой точке, встала -
// WHY: доезжает до сетки за треть секунды. Положение всегда непрерывно: копится не точка, а
// WHY: отступ от живой точки, и он тянется к цели экспонентой с конечной доводкой
final class UiTextGlide {
    private static final int CAPACITY = 1024;
    private static final int MASK = CAPACITY - 1;
    private static final int PROBES = 24;
    private static final int MIX = 0x9E3779B9;
    private static final long EXPIRE_FRAMES = 3L;
    // WHY: место покоя внутри заявленного хода снимается обратным ходом из float-позы и гуляет
    // WHY: до 3e-4 пикселя за кадр (стенд), такой шум не имеет права считаться движением
    private static final double STILL_PIXELS = 1.0 / 128.0;
    private static final double REACH_PIXELS = 12.0;
    private static final double QUIET_PIXELS = 1.0E-4;
    private static final double LANDED_PIXELS = 1.0 / 2048.0;
    private static final float FOLLOW_SPEED = 30.0f;
    private static final float SETTLE_SPEED = 9.0f;

    private static final int[] KEYS = new int[CAPACITY];
    private static final long[] SEEN = new long[CAPACITY];
    private static final double[] LAST_X = new double[CAPACITY];
    private static final double[] LAST_Y = new double[CAPACITY];
    private static final double[] LEAD_X = new double[CAPACITY];
    private static final double[] LEAD_Y = new double[CAPACITY];

    private static boolean fresh;
    private static double driftX;
    private static double driftY;

    private UiTextGlide() {}

    static void follow(int key, double pixelX, double pixelY, float loose) {
        double settledX = UiRestFrame.whole(pixelX);
        double settledY = UiRestFrame.whole(pixelY);
        double targetX = (1.0f - loose) * (settledX - pixelX);
        double targetY = (1.0f - loose) * (settledY - pixelY);
        int slot = claim(key, UiFrame.frame() + 1L, pixelX, pixelY);
        if (slot < 0) {
            driftX = quiet(pixelX + targetX - settledX);
            driftY = quiet(pixelY + targetY - settledY);
            return;
        }
        lead(slot, pixelX, pixelY, targetX, targetY);
        driftX = quiet(pixelX + LEAD_X[slot] - settledX);
        driftY = quiet(pixelY + LEAD_Y[slot] - settledY);
    }

    static double driftX() {
        return driftX;
    }

    static double driftY() {
        return driftY;
    }

    private static void lead(int slot, double pixelX, double pixelY, double targetX, double targetY) {
        if (fresh) {
            LEAD_X[slot] = targetX;
            LEAD_Y[slot] = targetY;
        } else {
            boolean moving = Math.abs(pixelX - LAST_X[slot]) > STILL_PIXELS
                    || Math.abs(pixelY - LAST_Y[slot]) > STILL_PIXELS;
            float speed = moving ? FOLLOW_SPEED : SETTLE_SPEED;
            float delta = UiFrame.delta();
            LEAD_X[slot] = approach(LEAD_X[slot], moving ? 0.0 : targetX, speed, delta);
            LEAD_Y[slot] = approach(LEAD_Y[slot], moving ? 0.0 : targetY, speed, delta);
        }
        LAST_X[slot] = pixelX;
        LAST_Y[slot] = pixelY;
    }

    // WHY: одна и та же строка бывает на экране не раз за кадр ("Выкл" в соседних строках меню),
    // WHY: поэтому запись ищется по ключу и месту: берётся ближайшая неиспользованная в этом кадре
    // WHY: в пределах REACH. По очереди появления нельзя: новая строка выше по экрану сдвинула бы
    // WHY: очередь, и соседи на кадр получили бы чужое место и поплыли бы. Строка, прыгнувшая дальше
    // WHY: REACH за кадр, заводит новую запись и стоит на сетке: на таком ходу ступень не видна
    private static int claim(int key, long frame, double pixelX, double pixelY) {
        int start = (key * MIX >>> 16) & MASK;
        int best = -1;
        double nearest = REACH_PIXELS;
        int free = -1;
        for (int step = 0; step < PROBES; step++) {
            int slot = (start + step) & MASK;
            long seen = SEEN[slot];
            boolean stale = seen == 0L || frame - seen > EXPIRE_FRAMES;
            if (!stale && KEYS[slot] == key && seen != frame) {
                double distance = Math.max(Math.abs(pixelX - LAST_X[slot]), Math.abs(pixelY - LAST_Y[slot]));
                if (distance < nearest) {
                    nearest = distance;
                    best = slot;
                }
            } else if (stale && free < 0) {
                free = slot;
            }
            if (seen == 0L) break;
        }
        if (best >= 0) return take(best, key, frame, false);
        return free < 0 ? -1 : take(free, key, frame, true);
    }

    private static int take(int slot, int key, long frame, boolean created) {
        KEYS[slot] = key;
        SEEN[slot] = frame;
        fresh = created;
        return slot;
    }

    private static double approach(double current, double target, float speed, float delta) {
        if (delta <= 0.0f) return current;
        double next = target + (current - target) * Math.exp(-speed * delta);
        return Math.abs(target - next) < LANDED_PIXELS ? target : next;
    }

    private static double quiet(double drift) {
        return Math.abs(drift) < QUIET_PIXELS ? 0.0 : drift;
    }
}
