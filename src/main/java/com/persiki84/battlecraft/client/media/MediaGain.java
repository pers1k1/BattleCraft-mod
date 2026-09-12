package com.persiki84.battlecraft.client.media;

// WHY: мост отдаёт сырой уровень потока, а он весь целиком едет за ползунком громкости плеера:
// WHY: тихо - полоски лежат, громко - все стоят в потолке. Поэтому кадр делится на собственный
// WHY: недавний пик, и визуализатор показывает форму спектра и динамику трека, а не громкость
public final class MediaGain {
    private static final float QUIET = 0.05f;
    private static final float SILENCE = 0.012f;
    private static final float MAX_BOOST = 12.0f;
    private static final float FALL_MS = 2500.0f;

    private static float top = QUIET;
    private static long measured;

    private MediaGain() {}

    public static void forget() {
        top = QUIET;
        measured = 0L;
    }

    public static float[] normalize(float[] raw) {
        float peak = 0.0f;
        for (float value : raw) {
            peak = Math.max(peak, value);
        }

        float ceiling = follow(peak);
        if (peak < SILENCE) return new float[raw.length];

        float scale = Math.min(1.0f / ceiling, MAX_BOOST);
        float[] shown = new float[raw.length];
        for (int index = 0; index < raw.length; index++) {
            shown[index] = Math.max(0.0f, Math.min(1.0f, raw[index] * scale));
        }
        return shown;
    }

    // WHY: вверх пик берётся сразу, вниз отпускается за пару секунд: мгновенный спад приравнял бы
    // WHY: тихий проигрыш к припеву, а медленный подъём срезал бы верхушку первого громкого удара
    private static float follow(float peak) {
        long now = System.currentTimeMillis();
        float fall = measured == 0L ? 0.0f : (float) Math.exp(-(now - measured) / FALL_MS);
        measured = now;
        top = Math.max(peak, QUIET + (top - QUIET) * fall);
        return Math.max(top, QUIET);
    }
}
