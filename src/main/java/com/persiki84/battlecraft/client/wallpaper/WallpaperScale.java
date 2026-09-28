package com.persiki84.battlecraft.client.wallpaper;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class WallpaperScale {
    static final int MAX_WIDTH = 3840;
    static final int MAX_HEIGHT = 2160;
    // WHY: исходник декодируется в память целиком, и снимок 16000x16000 это гигабайт кучи в фоновом
    // WHY: потоке: рендер-поток падал бы по нехватке памяти вместе с ним. 8K это вдвое больше
    // WHY: предела кеша по каждой стороне, то есть запас на честное усреднение при сжатии
    static final long MAX_SOURCE_PIXELS = 7680L * 4320L;

    private WallpaperScale() {}

    static boolean fits(long width, long height) {
        return width * height <= MAX_SOURCE_PIXELS;
    }

    // WHY: шаг прореживания при чтении: корень из доли лишних пикселей, округлённый вверх, чтобы
    // WHY: прочитанный кадр гарантированно лёг в бюджет
    static int subsampling(int width, int height) {
        double excess = (double) width * height / MAX_SOURCE_PIXELS;
        return excess <= 1.0 ? 1 : (int) Math.ceil(Math.sqrt(excess));
    }

    static int fittedWidth(int width, int height) {
        return Math.max(1, (int) Math.round(width * shrink(width, height)));
    }

    static int fittedHeight(int width, int height) {
        return Math.max(1, (int) Math.round(height * shrink(width, height)));
    }

    private static double shrink(int width, int height) {
        return Math.min(1.0, Math.min((double) MAX_WIDTH / width, (double) MAX_HEIGHT / height));
    }

    // WHY: одиночный билинейный проход при уменьшении больше чем вдвое берёт по четыре пикселя
    // WHY: из каждого окна и теряет остальные, отсюда рябь. Уменьшение вдвое за шаг это честное
    // WHY: усреднение 2x2, и только последний шаг, меньше двух раз, идёт билинейно
    static BufferedImage fit(BufferedImage source) {
        int width = fittedWidth(source.getWidth(), source.getHeight());
        int height = fittedHeight(source.getWidth(), source.getHeight());
        if (width == source.getWidth() && height == source.getHeight() && opaqueRgb(source)) return source;

        BufferedImage current = source;
        while (current.getWidth() / 2 >= width && current.getHeight() / 2 >= height) {
            current = drawn(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return drawn(current, width, height);
    }

    // WHY: Media Foundation отдаёт поворот из метаданных телефона как угол по часовой стрелке, на
    // WHY: который кадр надо довернуть; расширенная обработка доворачивает сама, простая нет
    static BufferedImage rotated(BufferedImage source, int clockwiseDegrees) {
        int quarter = Math.floorMod(clockwiseDegrees / 90, 4);
        if (quarter == 0) return source;

        boolean sideways = quarter % 2 == 1;
        int width = sideways ? source.getHeight() : source.getWidth();
        int height = sideways ? source.getWidth() : source.getHeight();
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.translate(quarter == 3 ? 0 : width, quarter == 1 ? 0 : height);
            graphics.rotate(Math.PI / 2 * quarter);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static boolean opaqueRgb(BufferedImage image) {
        return image.getType() == BufferedImage.TYPE_INT_RGB;
    }

    // WHY: прозрачность ложится на чёрный: JPEG кеша альфы не хранит, а меню под обоями тёмное
    static BufferedImage drawn(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }
}
