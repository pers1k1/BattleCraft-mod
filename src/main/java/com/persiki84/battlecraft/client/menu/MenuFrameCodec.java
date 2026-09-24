package com.persiki84.battlecraft.client.menu;

import com.mojang.blaze3d.platform.NativeImage;
import com.persiki84.battlecraft.menu.MenuFrames;
import org.lwjgl.stb.STBIWriteCallback;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public final class MenuFrameCodec {
    public static final int MAX_SIDE = 512;

    private static final int[] QUALITIES = {82, 64, 46};
    private static final int RGB = 3;
    private static final int RGBA = 4;

    private static final ByteArrayOutputStream sink = new ByteArrayOutputStream(MenuFrames.MAX_BYTES);
    private static STBIWriteCallback writer;

    private MenuFrameCodec() {}

    // WHY: зовётся только с одного рабочего потока захвата: приёмник и обратный вызов stb общие
    public static byte[] encode(ByteBuffer rgba, int width, int height) {
        ByteBuffer rgb = MemoryUtil.memAlloc(width * height * RGB);
        try {
            uprightRgb(rgba, rgb, width, height);
            for (int quality : QUALITIES) {
                byte[] image = compress(rgb, width, height, quality);
                if (image != null && image.length <= MenuFrames.MAX_BYTES) return image;
            }
            return null;
        } finally {
            MemoryUtil.memFree(rgb);
        }
    }

    // WHY: glReadPixels отдаёт строки снизу вверх, а JPEG пишется сверху вниз; глобальный
    // WHY: stbi_flip_vertically_on_write не годится, его делят снимки экрана игры
    private static void uprightRgb(ByteBuffer rgba, ByteBuffer rgb, int width, int height) {
        for (int row = 0; row < height; row++) {
            int from = (height - 1 - row) * width * RGBA;
            int to = row * width * RGB;
            for (int column = 0; column < width; column++) {
                rgb.put(to + column * RGB, rgba.get(from + column * RGBA));
                rgb.put(to + column * RGB + 1, rgba.get(from + column * RGBA + 1));
                rgb.put(to + column * RGB + 2, rgba.get(from + column * RGBA + 2));
            }
        }
    }

    private static byte[] compress(ByteBuffer rgb, int width, int height, int quality) {
        sink.reset();
        if (writer == null) writer = STBIWriteCallback.create(MenuFrameCodec::collect);
        int written = STBImageWrite.stbi_write_jpg_to_func(writer, 0L, width, height, RGB, rgb, quality);
        return written == 0 ? null : sink.toByteArray();
    }

    private static void collect(long context, long data, int size) {
        ByteBuffer chunk = STBIWriteCallback.getData(data, size);
        byte[] bytes = new byte[size];
        chunk.get(bytes);
        sink.write(bytes, 0, size);
    }

    // WHY: картинка пришла от чужого клиента: размер проверяется по заголовку до распаковки,
    // WHY: иначе крошечный файл с огромными заявленными сторонами съел бы память
    public static NativeImage decode(byte[] image) {
        if (image.length == 0 || image.length > MenuFrames.MAX_BYTES) return null;

        ByteBuffer packed = MemoryUtil.memAlloc(image.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            packed.put(image).flip();
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer components = stack.mallocInt(1);
            if (!STBImage.stbi_info_from_memory(packed, width, height, components)) return null;
            if (width.get(0) <= 0 || height.get(0) <= 0) return null;
            if (width.get(0) > MAX_SIDE || height.get(0) > MAX_SIDE) return null;
            return NativeImage.read(NativeImage.Format.RGBA, packed);
        } catch (Exception error) {
            return null;
        } finally {
            MemoryUtil.memFree(packed);
        }
    }
}
