package com.persiki84.battlecraft.client.voice;

import java.util.Arrays;

// WHY: лента диктофона, а не полоски спектра: у речи энергия сидит в одной полосе, и столбики
// WHY: на ней стоят кучей, а бегущая запись читается как «человек говорит вот столько и вот так»
public final class VoiceTape {
    public static final int MARKS = 44;

    private static final long STEP_MS = 42L;
    private static final long SILENCE_MS = 320L;
    private static final float QUIET = 0.02f;
    private static final float FALL_PER_SECOND = 0.45f;
    private static final float MAX_BOOST = 14.0f;
    private static final float SHAPE = 0.62f;

    private final float[] marks = new float[MARKS];

    private volatile float pending;
    private volatile long heardAt;

    private float loudest = QUIET;
    private int head;
    private int filled;
    private long steppedAt;
    private long startedAt;
    private long spokeUntil;

    // WHY: зовут из потока голосового чата, а читают из кадра: пик копится без блокировки,
    // WHY: потерянная в гонке выборка стоит одного штриха, а замок стоил бы щелчка в звуке
    public void feed(short[] audio) {
        if (audio == null || audio.length == 0) return;

        double sum = 0.0;
        for (short sample : audio) {
            double value = sample / 32768.0;
            sum += value * value;
        }
        feed((float) Math.sqrt(sum / audio.length));
    }

    public void feed(float level) {
        pending = Math.max(pending, Math.max(0.0f, level));
        heardAt = System.currentTimeMillis();
    }

    public void advance(long now) {
        if (steppedAt == 0L) steppedAt = now;
        if (talking(now)) {
            if (now - spokeUntil > SILENCE_MS) restart(now);
            spokeUntil = now;
        }

        long overdue = now - steppedAt;
        if (overdue < STEP_MS) return;

        int steps = (int) Math.min(MARKS, overdue / STEP_MS);
        steppedAt += (long) steps * STEP_MS;
        float level = take();
        for (int step = 0; step < steps; step++) {
            marks[head] = step == 0 ? level : 0.0f;
            head = (head + 1) % MARKS;
            if (filled < MARKS) filled++;
        }
    }

    // WHY: новая передача начинает ленту с чистого места: оставленные штрихи читаются как запись,
    // WHY: которая шла всё это время, хотя человек только нажал клавишу
    private void restart(long now) {
        Arrays.fill(marks, 0.0f);
        head = 0;
        filled = 0;
        steppedAt = now;
        startedAt = now;
        loudest = QUIET;
    }

    // WHY: тихий голос обязан выглядеть голосом, а не ровной чертой, поэтому лента делится на
    // WHY: собственный недавний пик: подъём мгновенный, спад за пару секунд
    private float take() {
        float level = pending;
        pending = 0.0f;

        float fall = (float) Math.pow(FALL_PER_SECOND, STEP_MS / 1000.0f);
        loudest = Math.max(level, QUIET + (loudest - QUIET) * fall);
        if (level <= QUIET * 0.5f) return 0.0f;

        float scale = Math.min(1.0f / Math.max(loudest, QUIET), MAX_BOOST);
        return (float) Math.pow(Math.min(1.0f, level * scale), SHAPE);
    }

    public float mark(int index) {
        return marks[(head + index) % MARKS];
    }

    // WHY: сколько штрихов уже записано: до них лента идёт пунктиром, иначе первая же секунда
    // WHY: разговора выглядит как готовая запись во всю ширину
    public int written() {
        return filled;
    }

    public float step(long now) {
        return Math.min(1.0f, Math.max(0.0f, (now - steppedAt) / (float) STEP_MS));
    }

    public boolean talking(long now) {
        long last = heardAt;
        return last != 0L && now - last < SILENCE_MS;
    }

    public long spokenFor(long now) {
        if (startedAt == 0L) return 0L;
        return Math.max(0L, (talking(now) ? now : spokeUntil) - startedAt);
    }

    public boolean idle(long now) {
        long last = heardAt;
        return last == 0L || now - last > SILENCE_MS * 4L;
    }

    public void forget() {
        Arrays.fill(marks, 0.0f);
        pending = 0.0f;
        heardAt = 0L;
        loudest = QUIET;
        head = 0;
        filled = 0;
        steppedAt = 0L;
        startedAt = 0L;
        spokeUntil = 0L;
    }
}
