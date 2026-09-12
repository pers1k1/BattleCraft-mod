package com.persiki84.shared.client.ui;

public final class UiColor {

    private UiColor() {}

    public static int fromHsb(float hue, float saturation, float brightness) {
        float value = UiAnim.clamp01(brightness) * 255.0f;
        float sat = UiAnim.clamp01(saturation);
        if (sat <= 0.0f) {
            int gray = Math.round(value);
            return 0xFF000000 | (gray << 16) | (gray << 8) | gray;
        }

        float sector = (hue - (float) Math.floor(hue)) * 6.0f;
        float fraction = sector - (float) Math.floor(sector);
        float dark = value * (1.0f - sat);
        float falling = value * (1.0f - sat * fraction);
        float rising = value * (1.0f - sat * (1.0f - fraction));

        return switch ((int) sector) {
            case 0 -> pack(value, rising, dark);
            case 1 -> pack(falling, value, dark);
            case 2 -> pack(dark, value, rising);
            case 3 -> pack(dark, falling, value);
            case 4 -> pack(rising, dark, value);
            default -> pack(value, dark, falling);
        };
    }

    public static float[] toHsb(int argb) {
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));

        float brightness = max / 255.0f;
        float saturation = max == 0 ? 0.0f : (max - min) / (float) max;
        return new float[] { hueOf(red, green, blue, max, min), saturation, brightness };
    }

    private static float hueOf(int red, int green, int blue, int max, int min) {
        if (max == min) return 0.0f;

        float span = max - min;
        float toRed = (max - red) / span;
        float toGreen = (max - green) / span;
        float toBlue = (max - blue) / span;

        float hue;
        if (red == max) {
            hue = toBlue - toGreen;
        } else if (green == max) {
            hue = 2.0f + toRed - toBlue;
        } else {
            hue = 4.0f + toGreen - toRed;
        }
        hue /= 6.0f;
        return hue < 0.0f ? hue + 1.0f : hue;
    }

    private static int pack(float red, float green, float blue) {
        return 0xFF000000 | (Math.round(red) << 16) | (Math.round(green) << 8) | Math.round(blue);
    }
}
