package com.persiki84.battlecraft.client.media;

// WHY: процессный захват берёт звук плеера до системного регулятора, но после ползунка самого
// WHY: плеера, а кривая айфона подогнана под трек на полной цифровой громкости. Spotify на обычной
// WHY: громкости приходит на 40 дБ тише полной, и без приведения полоски едут за ползунком.
// WHY: Опора - среднее логарифма самой громкой полосы, кадр масштабируется так, будто трек играет
// WHY: в полную силу. Никаких абсолютных порогов и потолков усиления: любой из них снова делает
// WHY: высоту зависимой от громкости, как только поток до него опускается
public final class MediaGain {
    // WHY: цифры подобраны tools/visualizer/level_model.py на pigstep, otherside, relic и живом
    // WHY: захвате Spotify: высоты как у модели айфона на полной громкости, от 100 % до 1 % не
    // WHY: меняются, после ползунка вниз впятеро полоски через секунду на 0.78 высоты, через две на 0.95
    private static final float TARGET_PEAK = 0.85f;
    private static final float RISE_MS = 200.0f;
    private static final float FALL_MS = 800.0f;
    private static final float QUIET_DROP = (float) Math.log(Math.pow(10.0, -45.0 / 20.0));
    // WHY: кадр тише опоры на 45 дБ это пауза или хвост трека: опора его не догоняет, иначе шум
    // WHY: вытянулся бы в полный рост, но медленно сползает, чтобы убавление больше чем на 45 дБ
    // WHY: не заперло полоски в точках навсегда
    private static final float QUIET_FALL_MS = 4000.0f;
    private static final float SILENCE = 1.0e-7f;

    private static float reference = Float.NaN;
    private static long measured;

    private MediaGain() {}

    public static void forget() {
        reference = Float.NaN;
        measured = 0L;
    }

    public static float scale(float loudestBand) {
        long now = System.currentTimeMillis();
        float elapsed = measured == 0L ? 0.0f : now - measured;
        measured = now;
        if (loudestBand <= SILENCE) return 0.0f;

        float loudness = (float) Math.log(loudestBand);
        if (Float.isNaN(reference)) reference = loudness;
        if (loudness < reference + QUIET_DROP) {
            approach(loudness, elapsed, QUIET_FALL_MS);
            return 0.0f;
        }
        approach(loudness, elapsed, loudness > reference ? RISE_MS : FALL_MS);
        return TARGET_PEAK / (float) Math.exp(reference);
    }

    private static void approach(float loudness, float elapsed, float settleMs) {
        reference += (loudness - reference) * (1.0f - (float) Math.exp(-elapsed / settleMs));
    }
}
