package com.persiki84.battlecraft.client.loading;

import com.mojang.logging.LogUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

// WHY: картинка читается с classpath через ImageIO, а не менеджером ресурсов: экран запуска идёт
// WHY: до первой загрузки ресурсов, и ни атласа, ни текстуры к этому моменту ещё не существует
public final class LoadingMark {
    private static final String PATH = "/assets/battlecraft/textures/gui/loading_mark.png";
    private static final int OPAQUE = 0x60;

    private static int[] pixels;
    private static int width;
    private static int height;
    private static boolean attempted;

    private LoadingMark() {}

    public static boolean ready() {
        load();
        return pixels != null;
    }

    public static int width() {
        return width;
    }

    public static int height() {
        return height;
    }

    public static int pixel(int x, int y) {
        return pixels[y * width + x];
    }

    public static boolean opaque(int x, int y) {
        return (pixel(x, y) >>> 24) >= OPAQUE;
    }

    private static synchronized void load() {
        if (attempted) return;

        attempted = true;
        try (InputStream stream = LoadingMark.class.getResourceAsStream(PATH)) {
            if (stream == null) throw new IllegalStateException("нет ресурса " + PATH);
            store(ImageIO.read(stream));
        } catch (Exception error) {
            LogUtils.getLogger().warn("[battlecraft] метка загрузки не прочитана: {}", String.valueOf(error));
        }
    }

    private static void store(BufferedImage image) {
        width = image.getWidth();
        height = image.getHeight();
        pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
    }
}
