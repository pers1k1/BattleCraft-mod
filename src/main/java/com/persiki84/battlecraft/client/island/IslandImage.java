package com.persiki84.battlecraft.client.island;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.FastColor;

public final class IslandImage {
    public static final float CORNER_SHARE = 0.26f;

    private static final float EDGE_SOFTNESS = 0.8f;
    private static final int CLEAR_ALPHA = 16;
    private static final int PALE_LEVEL = 246;
    private static final float KEEP_SHARE = 0.30f;

    private static final int LEFT = 0;
    private static final int TOP = 1;
    private static final int RIGHT = 2;
    private static final int BOTTOM = 3;

    private IslandImage() {}

    public static NativeImage squared(NativeImage source) {
        int[] box = {0, 0, source.getWidth() - 1, source.getHeight() - 1};
        trim(source, box, false);
        if (framed(source)) trim(source, box, true);
        return crop(source, box);
    }

    private static void trim(NativeImage image, int[] box, boolean pale) {
        int keepX = pale ? Math.round((box[RIGHT] - box[LEFT] + 1) * KEEP_SHARE) : 1;
        int keepY = pale ? Math.round((box[BOTTOM] - box[TOP] + 1) * KEEP_SHARE) : 1;

        while (span(box, LEFT, RIGHT) > keepX && columnBlank(image, box, box[LEFT], pale)) box[LEFT]++;
        while (span(box, LEFT, RIGHT) > keepX && columnBlank(image, box, box[RIGHT], pale)) box[RIGHT]--;
        while (span(box, TOP, BOTTOM) > keepY && rowBlank(image, box, box[TOP], pale)) box[TOP]++;
        while (span(box, TOP, BOTTOM) > keepY && rowBlank(image, box, box[BOTTOM], pale)) box[BOTTOM]--;
    }

    private static int span(int[] box, int low, int high) {
        return box[high] - box[low] + 1;
    }

    private static boolean framed(NativeImage image) {
        for (int x = 0; x < image.getWidth(); x++) {
            if (FastColor.ABGR32.alpha(image.getPixelRGBA(x, 0)) <= CLEAR_ALPHA) return true;
        }
        for (int y = 0; y < image.getHeight(); y++) {
            if (FastColor.ABGR32.alpha(image.getPixelRGBA(0, y)) <= CLEAR_ALPHA) return true;
        }
        return false;
    }

    private static boolean columnBlank(NativeImage image, int[] box, int x, boolean pale) {
        for (int y = box[TOP]; y <= box[BOTTOM]; y++) {
            if (!blank(image.getPixelRGBA(x, y), pale)) return false;
        }
        return true;
    }

    private static boolean rowBlank(NativeImage image, int[] box, int y, boolean pale) {
        for (int x = box[LEFT]; x <= box[RIGHT]; x++) {
            if (!blank(image.getPixelRGBA(x, y), pale)) return false;
        }
        return true;
    }

    private static boolean blank(int pixel, boolean pale) {
        if (FastColor.ABGR32.alpha(pixel) <= CLEAR_ALPHA) return true;
        if (!pale) return false;

        return FastColor.ABGR32.red(pixel) >= PALE_LEVEL
                && FastColor.ABGR32.green(pixel) >= PALE_LEVEL
                && FastColor.ABGR32.blue(pixel) >= PALE_LEVEL;
    }

    private static NativeImage crop(NativeImage source, int[] box) {
        int width = box[RIGHT] - box[LEFT] + 1;
        int height = box[BOTTOM] - box[TOP] + 1;
        int edge = Math.max(1, Math.min(width, height));
        int originX = box[LEFT] + (width - edge) / 2;
        int originY = box[TOP] + (height - edge) / 2;

        NativeImage cut = new NativeImage(edge, edge, false);
        for (int y = 0; y < edge; y++) {
            for (int x = 0; x < edge; x++) {
                cut.setPixelRGBA(x, y, source.getPixelRGBA(originX + x, originY + y));
            }
        }
        return cut;
    }

    public static float coverage(float x, float y, float width, float height, float radius) {
        float dx = Math.max(0.0f, Math.max(radius - x, x - (width - radius)));
        float dy = Math.max(0.0f, Math.max(radius - y, y - (height - radius)));
        if (dx <= 0.0f || dy <= 0.0f) return 1.0f;

        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        return Math.min(1.0f, Math.max(0.0f, (radius - distance) / EDGE_SOFTNESS + 0.5f));
    }
}
