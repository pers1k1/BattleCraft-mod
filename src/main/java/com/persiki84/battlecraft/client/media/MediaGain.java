package com.persiki84.battlecraft.client.media;

// WHY: процессный захват берёт звук плеера до системного регулятора, но после ползунка самого
// WHY: плеера, а кривая айфона подогнана под трек на полной цифровой громкости. Spotify на обычной
// WHY: громкости приходит на 40 дБ тише полной, и без приведения полоски едут за ползунком.
// WHY: Опора устроена как компрессор: рост громкости догоняет за 2 с, спад отпускает за 8 с.
// WHY: Быстрая в обе стороны она вытягивала затухание и тихий брейк до обычной высоты за секунду,
// WHY: медленная в обе стороны вбивала дроп в потолок. Абсолютных потолков усиления нет: любой из
// WHY: них снова делает высоту зависимой от громкости, как только поток до него опускается
public final class MediaGain {
    // WHY: цифры подобраны tools/visualizer/level_model.py: затухание на -30 дБ к концу оставляет
    // WHY: треть высоты, райзер перед дропом растёт заметнее, на дропе в потолке 1 % полосок, а на
    // WHY: полной громкости высоты как у модели айфона. Цена: после сильного убавления ползунка
    // WHY: высота возвращается секунд за десять
    private static final float TARGET_PEAK = 0.8f;
    private static final float RISE_MS = 2000.0f;
    private static final float FALL_MS = 8000.0f;
    // WHY: кадр тише опоры на 45 дБ это пауза или хвост трека: опора его не догоняет, иначе шум
    // WHY: вытянулся бы в полный рост, но медленно сползает, чтобы убавление больше чем на 45 дБ
    // WHY: не заперло полоски в точках навсегда
    private static final float QUIET_DROP = (float) Math.log(Math.pow(10.0, -45.0 / 20.0));
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
