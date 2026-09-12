package com.persiki84.shared.client.ui;

public final class UiOklab {
    private static final int GAMUT_STEPS = 16;
    private static final float GAMUT_SLACK = 0.001f;
    private static final float DECODE_KNEE = 0.04045f;
    private static final float ENCODE_KNEE = 0.0031308f;
    private static final float HUE_FLOOR = 0.004f;

    private record Lab(float lightness, float greenRed, float blueYellow) {}

    private record Linear(float red, float green, float blue) {}

    private UiOklab() {}

    public static int lift(int argb, float minLightness) {
        if (lightness(argb) >= minLightness) return argb;

        return withLightness(argb, minLightness);
    }

    public static float lightness(int argb) {
        return toLab(argb).lightness();
    }

    public static float chroma(int argb) {
        return chromaOf(toLab(argb));
    }

    // WHY: усреднение области картинки гасит насыщенность, потому что противоположные оттенки
    // WHY: гасят друг друга: тон и светлота у среднего верные, а густоту ему возвращают отдельно
    public static int withChroma(int argb, float chroma) {
        Lab lab = toLab(argb);
        float have = chromaOf(lab);
        if (have < HUE_FLOOR) return argb;

        float share = chroma / have;
        Lab shifted = new Lab(lab.lightness(), lab.greenRed() * share, lab.blueYellow() * share);
        return (argb & 0xFF000000) | (fitToGamut(shifted) & 0x00FFFFFF);
    }

    // WHY: цвет переезжает по дуге тона, а не по прямой между каналами: прямая проходит через
    // WHY: серый и на середине перехода цвет выцветает, поэтому вместо смены оттенка видно грязь.
    // WHY: добавка к насыщенности на середине дуги возвращает переходу сочность
    public static int sweep(int from, int to, float weight, float bloom) {
        float share = clamp(weight);
        Lab start = toLab(from);
        Lab end = toLab(to);
        float startChroma = chromaOf(start);
        float endChroma = chromaOf(end);
        float angle = arc(hueOf(start, end), hueOf(end, start), share);
        float chroma = startChroma + (endChroma - startChroma) * share
                + bloom * Math.max(startChroma, endChroma) * (float) Math.sin(Math.PI * share);

        Lab blended = new Lab(start.lightness() + (end.lightness() - start.lightness()) * share,
                chroma * (float) Math.cos(angle), chroma * (float) Math.sin(angle));
        return (blendAlpha(from, to, share) << 24) | (fitToGamut(blended) & 0x00FFFFFF);
    }

    public static float distance(int from, int to) {
        Lab start = toLab(from);
        Lab end = toLab(to);
        float lightness = end.lightness() - start.lightness();
        float greenRed = end.greenRed() - start.greenRed();
        float blueYellow = end.blueYellow() - start.blueYellow();
        float opacity = (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) / 255.0f;
        return (float) Math.sqrt(lightness * lightness + greenRed * greenRed
                + blueYellow * blueYellow + opacity * opacity);
    }

    private static int blendAlpha(int from, int to, float share) {
        int start = (from >>> 24) & 0xFF;
        int end = (to >>> 24) & 0xFF;
        return Math.round(start + (end - start) * share) & 0xFF;
    }

    private static float chromaOf(Lab lab) {
        return (float) Math.hypot(lab.greenRed(), lab.blueYellow());
    }

    // WHY: у серого тона нет, и своей дугой он утащил бы переход в случайный оттенок,
    // WHY: поэтому бесцветный конец занимает тон у цветного
    private static float hueOf(Lab lab, Lab borrowed) {
        if (chromaOf(lab) < HUE_FLOOR) {
            return chromaOf(borrowed) < HUE_FLOOR ? 0.0f
                    : (float) Math.atan2(borrowed.blueYellow(), borrowed.greenRed());
        }
        return (float) Math.atan2(lab.blueYellow(), lab.greenRed());
    }

    private static float arc(float from, float to, float share) {
        float travel = to - from;
        while (travel > (float) Math.PI) travel -= (float) (Math.PI * 2.0);
        while (travel < -(float) Math.PI) travel += (float) (Math.PI * 2.0);
        return from + travel * share;
    }

    public static int scaleLightness(int argb, float share) {
        return withLightness(argb, lightness(argb) * share);
    }

    // WHY: насыщенность тянется за светлотой, а не остаётся прежней: при неизменной chroma
    // WHY: поднятый цвет выцветает (бордовый уходил в розовый), а опущенный, наоборот, густеет,
    // WHY: и тройка текста расходилась по тону вместо того, чтобы отличаться одной яркостью
    public static int withLightness(int argb, float lightness) {
        Lab source = toLab(argb);
        float target = clamp(lightness);
        if (source.lightness() <= 0.0f) {
            return (argb & 0xFF000000) | (fitToGamut(new Lab(target, 0.0f, 0.0f)) & 0x00FFFFFF);
        }

        float share = target / source.lightness();
        Lab shifted = new Lab(target, source.greenRed() * share, source.blueYellow() * share);
        return (argb & 0xFF000000) | (fitToGamut(shifted) & 0x00FFFFFF);
    }

    private static int fitToGamut(Lab lab) {
        if (inGamut(lab)) return toArgb(lab);

        float kept = 0.0f;
        float rejected = 1.0f;
        for (int step = 0; step < GAMUT_STEPS; step++) {
            float share = (kept + rejected) * 0.5f;
            if (inGamut(scaleChroma(lab, share))) {
                kept = share;
            } else {
                rejected = share;
            }
        }
        return toArgb(scaleChroma(lab, kept));
    }

    private static Lab scaleChroma(Lab lab, float share) {
        return new Lab(lab.lightness(), lab.greenRed() * share, lab.blueYellow() * share);
    }

    private static boolean inGamut(Lab lab) {
        Linear linear = toLinear(lab);
        return within(linear.red()) && within(linear.green()) && within(linear.blue());
    }

    private static boolean within(float value) {
        return value >= -GAMUT_SLACK && value <= 1.0f + GAMUT_SLACK;
    }

    // WHY: матрицы и кубические корни это перевод sRGB в OKLab по Bjorn Ottosson
    // WHY: (https://bottosson.github.io/posts/oklab/); в этом пространстве светлота отделена от
    // WHY: тона и насыщенности, поэтому подъём L не уводит бордовый в розовый, как подмес белого
    private static Lab toLab(int argb) {
        float red = decode(((argb >> 16) & 0xFF) / 255.0f);
        float green = decode(((argb >> 8) & 0xFF) / 255.0f);
        float blue = decode((argb & 0xFF) / 255.0f);

        float longCone = cbrt(0.4122214708f * red + 0.5363325363f * green + 0.0514459929f * blue);
        float mediumCone = cbrt(0.2119034982f * red + 0.6806995451f * green + 0.1073969566f * blue);
        float shortCone = cbrt(0.0883024619f * red + 0.2817188376f * green + 0.6299787005f * blue);

        return new Lab(
                0.2104542553f * longCone + 0.7936177850f * mediumCone - 0.0040720468f * shortCone,
                1.9779984951f * longCone - 2.4285922050f * mediumCone + 0.4505937099f * shortCone,
                0.0259040371f * longCone + 0.7827717662f * mediumCone - 0.8086757660f * shortCone);
    }

    private static Linear toLinear(Lab lab) {
        float longCone = cube(lab.lightness()
                + 0.3963377774f * lab.greenRed() + 0.2158037573f * lab.blueYellow());
        float mediumCone = cube(lab.lightness()
                - 0.1055613458f * lab.greenRed() - 0.0638541728f * lab.blueYellow());
        float shortCone = cube(lab.lightness()
                - 0.0894841775f * lab.greenRed() - 1.2914855480f * lab.blueYellow());

        return new Linear(
                4.0767416621f * longCone - 3.3077115913f * mediumCone + 0.2309699292f * shortCone,
                -1.2684380046f * longCone + 2.6097574011f * mediumCone - 0.3413193965f * shortCone,
                -0.0041960863f * longCone - 0.7034186147f * mediumCone + 1.7076147010f * shortCone);
    }

    private static int toArgb(Lab lab) {
        Linear linear = toLinear(lab);
        return (byteOf(linear.red()) << 16) | (byteOf(linear.green()) << 8) | byteOf(linear.blue());
    }

    private static int byteOf(float linear) {
        return Math.round(encode(clamp(linear)) * 255.0f) & 0xFF;
    }

    private static float decode(float value) {
        if (value <= DECODE_KNEE) return value / 12.92f;

        return (float) Math.pow((value + 0.055f) / 1.055f, 2.4f);
    }

    private static float encode(float value) {
        if (value <= ENCODE_KNEE) return value * 12.92f;

        return 1.055f * (float) Math.pow(value, 1.0f / 2.4f) - 0.055f;
    }

    private static float cbrt(float value) {
        return (float) Math.cbrt(value);
    }

    private static float cube(float value) {
        return value * value * value;
    }

    private static float clamp(float value) {
        if (value < 0.0f) return 0.0f;
        return Math.min(value, 1.0f);
    }
}
