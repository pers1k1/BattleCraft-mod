package com.persiki84.capturepoints.util;

import org.joml.Vector3f;

public class ColorUtil {
    public static int blendWithGold(int color, float amount) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        r = (int) (r * (1 - amount) + 0xFF * amount);
        g = (int) (g * (1 - amount) + 0xD7 * amount);
        b = (int) (b * (1 - amount) + 0x00 * amount);

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static Vector3f blendWithGold(Vector3f color, float amount) {
        return new Vector3f(
                color.x * (1 - amount) + 1.0f * amount,
                color.y * (1 - amount) + 0.84f * amount,
                color.z * (1 - amount) + 0.0f * amount
        );
    }
}
