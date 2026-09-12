package com.persiki84.battlecraft.client.island;

import com.mojang.blaze3d.platform.NativeImage;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.media.MediaWatch;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiOklab;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiWash;
import net.minecraft.util.FastColor;

// WHY: визуализатор это перенос обложки на ряд полосок: цвет берётся с того самого места картинки,
// WHY: где полоска стоит - слева сверху полоски лежит цвет левого верхнего угла обложки. Выдуманных
// WHY: цветов тут нет вовсе, поэтому чёрно-белая обложка честно даёт серебряную лесенку
public final class IslandTone {
    private static final int COLUMNS = MediaWatch.BANDS;
    private static final int ROWS = 2;
    private static final float WASH_SECONDS = 0.7f;
    private static final int SAMPLE_EDGE = 96;
    private static final int CLEAR_ALPHA = 32;
    private static final float SHARPEN = 2.0f;
    private static final float VIVID_BASE = 0.04f;
    private static final float CHROMA_CAP = 2.4f;
    private static final float DARK_FLOOR = 0.26f;
    private static final float TOP_TARGET = 0.66f;
    private static final float TOP_GAIN = 2.2f;

    // WHY: снимок обложки собирается в фоне и кладётся сюда целиком одной ссылкой: рендер-поток
    // WHY: читает уже готовое и никогда не видит наполовину посчитанную лесенку
    private record Cells(int[] mean, float[] light, float[] chroma, float gain) {}

    private static final int[] ramp = new int[COLUMNS * ROWS];
    private static final UiWash[] washes = new UiWash[COLUMNS * ROWS];

    private static volatile Cells cells;

    private static Cells shownCells;
    private static float shownLight = -1.0f;
    private static float shownColor = -1.0f;

    static {
        for (int cell = 0; cell < washes.length; cell++) {
            washes[cell] = new UiWash(WASH_SECONDS);
        }
    }

    private IslandTone() {}

    public static void read(NativeImage image) {
        int size = COLUMNS * ROWS;
        float[] weights = new float[size];
        float[] chromas = new float[size];
        float[] reds = new float[size];
        float[] greens = new float[size];
        float[] blues = new float[size];
        float[] plainWeights = new float[size];
        float[] plainLights = new float[size];

        int stride = Math.max(1, Math.min(image.getWidth(), image.getHeight()) / SAMPLE_EDGE);
        for (int y = 0; y < image.getHeight(); y += stride) {
            float row = (y + 0.5f) / image.getHeight() * ROWS - 0.5f;
            for (int x = 0; x < image.getWidth(); x += stride) {
                splat(image.getPixelRGBA(x, y), (x + 0.5f) / image.getWidth() * COLUMNS - 0.5f, row,
                        weights, chromas, reds, greens, blues, plainWeights, plainLights);
            }
        }
        cells = gathered(weights, chromas, reds, greens, blues, plainWeights, plainLights);
    }

    public static void advance(float delta) {
        restamp();
        boolean tinted = cells != null && IslandArt.ready() && HudConfig.islandCoverTint();
        for (int cell = 0; cell < washes.length; cell++) {
            washes[cell].aim(tinted ? ramp[cell] : UiAccent.color());
            washes[cell].advance(delta);
        }
    }

    // WHY: цвет привязан к ряду, а не к полоске: иначе тихая полоска сжимала бы весь переход
    // WHY: в свои несколько пикселей и цвета ездили бы вверх-вниз вместе со звуком
    public static int barAt(int column, float share) {
        int at = Math.max(0, Math.min(COLUMNS - 1, column));
        return UiTheme.mix(washes[at].get(), washes[at + COLUMNS].get(), clamp(share));
    }

    public static void forget() {
        cells = null;
    }

    // WHY: ручки правят уже снятые цвета, а не выборку, поэтому обложку заново читать не надо -
    // WHY: лесенка пересобирается только на новой обложке или когда ползунок действительно сдвинули
    private static void restamp() {
        Cells fresh = cells;
        if (fresh == shownCells && HudConfig.visualizerLight() == shownLight
                && HudConfig.visualizerColor() == shownColor) {
            return;
        }

        shownCells = fresh;
        shownLight = HudConfig.visualizerLight();
        shownColor = HudConfig.visualizerColor();
        for (int cell = 0; cell < ramp.length; cell++) {
            ramp[cell] = fresh == null ? UiAccent.color() : shown(fresh, cell);
        }
    }

    // WHY: пиксель раскладывается по четырём ближайшим ячейкам, а не падает в одну: так соседние
    // WHY: полоски сходятся переходом, а не ступенью на границе блока. Билинейный вес возводится
    // WHY: в степень, потому что он размазан на две ячейки в каждую сторону, то есть на треть
    // WHY: картинки, и предмет на обложке расплывался по всему ряду вместо своего места
    private static void splat(int pixel, float column, float row, float[] weights, float[] chromas,
                              float[] reds, float[] greens, float[] blues,
                              float[] plainWeights, float[] plainLights) {
        if (FastColor.ABGR32.alpha(pixel) <= CLEAR_ALPHA) return;

        int left = (int) Math.floor(column);
        int top = (int) Math.floor(row);
        float alongX = column - left;
        float alongY = row - top;
        add(left, top, pixel, near(1.0f - alongX) * near(1.0f - alongY),
                weights, chromas, reds, greens, blues, plainWeights, plainLights);
        add(left + 1, top, pixel, near(alongX) * near(1.0f - alongY),
                weights, chromas, reds, greens, blues, plainWeights, plainLights);
        add(left, top + 1, pixel, near(1.0f - alongX) * near(alongY),
                weights, chromas, reds, greens, blues, plainWeights, plainLights);
        add(left + 1, top + 1, pixel, near(alongX) * near(alongY),
                weights, chromas, reds, greens, blues, plainWeights, plainLights);
    }

    private static float near(float share) {
        return (float) Math.pow(share, SHARPEN);
    }

    // WHY: светлота ячейки берётся с обычного среднего, а цвет с взвешенного по насыщенности.
    // WHY: одним весом их брать нельзя: по насыщенности чёрное весит почти ноль и на визуализатор
    // WHY: не попадает вовсе, а без веса яркое поле с тёмной фигурой усредняется в муть
    private static void add(int column, int row, int pixel, float weight, float[] weights, float[] chromas,
                            float[] reds, float[] greens, float[] blues,
                            float[] plainWeights, float[] plainLights) {
        if (weight <= 0.0f) return;

        int cell = Math.max(0, Math.min(COLUMNS - 1, column))
                + Math.max(0, Math.min(ROWS - 1, row)) * COLUMNS;
        int argb = argbOf(pixel);
        float chroma = UiOklab.chroma(argb);
        plainWeights[cell] += weight;
        plainLights[cell] += UiOklab.lightness(argb) * weight;

        float pull = weight * (VIVID_BASE + chroma);
        weights[cell] += pull;
        chromas[cell] += chroma * pull;
        reds[cell] += FastColor.ABGR32.red(pixel) * pull;
        greens[cell] += FastColor.ABGR32.green(pixel) * pull;
        blues[cell] += FastColor.ABGR32.blue(pixel) * pull;
    }

    private static Cells gathered(float[] weights, float[] chromas, float[] reds, float[] greens,
                                  float[] blues, float[] plainWeights, float[] plainLights) {
        if (weights[0] <= 0.0f) return null;

        int size = weights.length;
        int[] mean = new int[size];
        float[] light = new float[size];
        float[] chroma = new float[size];
        for (int cell = 0; cell < size; cell++) {
            if (weights[cell] <= 0.0f || plainWeights[cell] <= 0.0f) continue;

            int red = Math.round(reds[cell] / weights[cell]);
            int green = Math.round(greens[cell] / weights[cell]);
            int blue = Math.round(blues[cell] / weights[cell]);
            mean[cell] = 0xFF000000 | (red << 16) | (green << 8) | blue;
            light[cell] = plainLights[cell] / plainWeights[cell];
            chroma[cell] = chromas[cell] / weights[cell];
        }
        return new Cells(mean, light, chroma, gain(light));
    }

    // WHY: тёмная обложка целиком ушла бы под стекло, но гасить контраст внутри неё нельзя:
    // WHY: поднимается вся лесенка разом по своей самой светлой ячейке, а не каждая по себе.
    // WHY: низ диапазона не обрезается, а поджимается: обрезка сплющила бы все тёмные ячейки
    // WHY: в одну светлоту, и тень на обложке перестала бы отличаться от чёрного
    private static float gain(float[] light) {
        float top = 0.0f;
        for (float value : light) {
            top = Math.max(top, value);
        }
        if (top <= 0.0f) return 1.0f;

        return Math.max(1.0f, Math.min(TOP_GAIN, TOP_TARGET / top));
    }

    private static int shown(Cells snapshot, int cell) {
        if (snapshot.mean()[cell] == 0) return UiAccent.color();

        int mean = snapshot.mean()[cell];
        float band = DARK_FLOOR + (1.0f - DARK_FLOOR) * clamp(snapshot.light()[cell] * snapshot.gain());
        float light = clamp(band * shownLight);
        return vivid(UiOklab.withLightness(mean, light), mean, snapshot.chroma()[cell], light);
    }

    // WHY: густота берётся средней по пикселям области, а не у усреднённого цвета - противоположные
    // WHY: оттенки в среднем гасят друг друга. Сверху она ограничена, чтобы пара ярких пикселей
    // WHY: не вытянула почти серую область в чистый цвет
    private static int vivid(int placed, int mean, float meanChroma, float light) {
        float plain = Math.max(1.0E-4f, UiOklab.lightness(mean));
        float target = meanChroma * light / plain * shownColor;
        return UiOklab.withChroma(placed, Math.min(target, UiOklab.chroma(placed) * CHROMA_CAP * shownColor));
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static int argbOf(int abgr) {
        return (FastColor.ABGR32.alpha(abgr) << 24) | (FastColor.ABGR32.red(abgr) << 16)
                | (FastColor.ABGR32.green(abgr) << 8) | FastColor.ABGR32.blue(abgr);
    }
}
