package com.persiki84.battlecraft.client.island;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.FastColor;

public final class IslandScale {
    public static final int EDGE_LIMIT = 256;

    private static final int CHANNELS = 4;
    private static final float BYTE = 255.0f;

    private IslandScale() {}

    // WHY: картинка на экране занимает десятки физических пикселей, а приходит в сотнях. Ванильная
    // WHY: DynamicTexture кладёт её с GL_NEAREST и без мипмапов, и уменьшение выбрасывало пиксели:
    // WHY: рябь по обложке и ступеньки по скруглению. Здесь база ужимается усреднением по площади до
    // WHY: степени двойки, скругляется и дробится пополам до одного пикселя - под трилинейную фильтрацию
    public static NativeImage[] chain(NativeImage squared, float cornerShare) {
        int base = baseEdge(squared.getWidth());
        float[] plane = fitted(squared, base);
        rounded(plane, base, cornerShare);

        int count = Integer.numberOfTrailingZeros(base) + 1;
        NativeImage[] levels = new NativeImage[count];
        int edge = base;
        for (int level = 0; level < count; level++) {
            levels[level] = image(plane, edge);
            if (edge > 1) plane = halved(plane, edge);
            edge = Math.max(1, edge / 2);
        }
        return levels;
    }

    private static int baseEdge(int source) {
        return Math.max(1, Math.min(EDGE_LIMIT, Integer.highestOneBit(Math.max(1, source))));
    }

    private static float[] fitted(NativeImage source, int edge) {
        int from = source.getWidth();
        float[] line = new float[from * CHANNELS];
        float[] across = new float[edge * from * CHANNELS];
        for (int y = 0; y < from; y++) {
            premultipliedRow(source, y, line);
            shrinkLine(line, 0, from, edge, across, y * CHANNELS, from * CHANNELS);
        }

        float[] square = new float[edge * edge * CHANNELS];
        for (int row = 0; row < edge; row++) {
            shrinkLine(across, row * from * CHANNELS, from, edge, square, row * CHANNELS, edge * CHANNELS);
        }
        return square;
    }

    private static void premultipliedRow(NativeImage source, int y, float[] line) {
        for (int x = 0; x < source.getWidth(); x++) {
            int pixel = source.getPixelRGBA(x, y);
            float alpha = FastColor.ABGR32.alpha(pixel) / BYTE;
            int at = x * CHANNELS;
            line[at] = FastColor.ABGR32.red(pixel) / BYTE * alpha;
            line[at + 1] = FastColor.ABGR32.green(pixel) / BYTE * alpha;
            line[at + 2] = FastColor.ABGR32.blue(pixel) / BYTE * alpha;
            line[at + 3] = alpha;
        }
    }

    // WHY: ряд ужимается и сразу пишется поперёк: два прохода по строкам с транспонированием дают
    // WHY: разделимое усреднение по площади без отдельного прохода по столбцам
    private static void shrinkLine(float[] line, int offset, int from, int to,
                                   float[] out, int outOffset, int outStride) {
        float step = (float) from / to;
        for (int target = 0; target < to; target++) {
            float start = target * step;
            float end = start + step;
            int at = outOffset + target * outStride;
            for (int source = (int) start; source < Math.min(from, (int) Math.ceil(end)); source++) {
                float weight = (Math.min(end, source + 1.0f) - Math.max(start, source)) / step;
                int from4 = offset + source * CHANNELS;
                for (int channel = 0; channel < CHANNELS; channel++) {
                    out[at + channel] += line[from4 + channel] * weight;
                }
            }
        }
    }

    private static void rounded(float[] plane, int edge, float cornerShare) {
        float radius = edge * cornerShare;
        if (radius <= 0.5f) return;

        for (int y = 0; y < edge; y++) {
            for (int x = 0; x < edge; x++) {
                float coverage = IslandImage.coverage(x + 0.5f, y + 0.5f, edge, edge, radius);
                if (coverage >= 1.0f) continue;

                int at = (y * edge + x) * CHANNELS;
                for (int channel = 0; channel < CHANNELS; channel++) plane[at + channel] *= coverage;
            }
        }
    }

    private static float[] halved(float[] plane, int edge) {
        int half = edge / 2;
        float[] out = new float[half * half * CHANNELS];
        for (int y = 0; y < half; y++) {
            for (int x = 0; x < half; x++) {
                int top = (y * 2 * edge + x * 2) * CHANNELS;
                int bottom = top + edge * CHANNELS;
                int at = (y * half + x) * CHANNELS;
                for (int channel = 0; channel < CHANNELS; channel++) {
                    out[at + channel] = (plane[top + channel] + plane[top + CHANNELS + channel]
                            + plane[bottom + channel] + plane[bottom + CHANNELS + channel]) * 0.25f;
                }
            }
        }
        return out;
    }

    private static NativeImage image(float[] plane, int edge) {
        NativeImage image = new NativeImage(edge, edge, false);
        for (int y = 0; y < edge; y++) {
            for (int x = 0; x < edge; x++) {
                int at = (y * edge + x) * CHANNELS;
                float alpha = plane[at + 3];
                float lift = alpha > 1.0E-6f ? 1.0f / alpha : 0.0f;
                image.setPixelRGBA(x, y, FastColor.ABGR32.color(
                        channel(alpha), channel(plane[at + 2] * lift),
                        channel(plane[at + 1] * lift), channel(plane[at] * lift)));
            }
        }
        return image;
    }

    private static int channel(float share) {
        return Math.max(0, Math.min(255, Math.round(share * BYTE)));
    }
}
