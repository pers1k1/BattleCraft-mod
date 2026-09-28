package com.persiki84.battlecraft.client.wallpaper;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class WallpaperFrameDecoder implements AutoCloseable {
    private static final int RGBA = 4;
    private static final int INITIAL_BYTES = 1 << 20;

    private final int width;
    private final int height;
    private final IntBuffer decodedWidth = MemoryUtil.memAllocInt(1);
    private final IntBuffer decodedHeight = MemoryUtil.memAllocInt(1);
    private final IntBuffer channels = MemoryUtil.memAllocInt(1);
    private ByteBuffer file = MemoryUtil.memAlloc(INITIAL_BYTES);

    WallpaperFrameDecoder(int width, int height) {
        this.width = width;
        this.height = height;
    }

    // WHY: stb_image, которым ваниль читает PNG, декодирует JPEG кадр 4K за ~40 мс против ~95 мс у
    // WHY: ImageIO: без него пул из трёх потоков не держал 4K на 30 к/с. Результат это RGBA, альфа
    // WHY: у JPEG всегда единица
    ByteBuffer decode(Path path) throws IOException {
        read(path);
        ByteBuffer pixels = STBImage.stbi_load_from_memory(file, decodedWidth, decodedHeight, channels, RGBA);
        if (pixels == null) throw new IOException(path.getFileName() + ": " + STBImage.stbi_failure_reason());
        if (decodedWidth.get(0) != width || decodedHeight.get(0) != height) {
            STBImage.stbi_image_free(pixels);
            throw new IOException(path.getFileName() + ": size differs from meta");
        }
        return pixels;
    }

    void release(ByteBuffer pixels) {
        STBImage.stbi_image_free(pixels);
    }

    private void read(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size > Integer.MAX_VALUE) throw new IOException(path.getFileName() + ": too large");
            if (size > file.capacity()) file = MemoryUtil.memRealloc(file, (int) size);
            file.clear();
            file.limit((int) size);
            int read = 0;
            while (file.hasRemaining() && read >= 0) read = channel.read(file);
            file.flip();
        }
    }

    @Override
    public void close() {
        MemoryUtil.memFree(file);
        MemoryUtil.memFree(decodedWidth);
        MemoryUtil.memFree(decodedHeight);
        MemoryUtil.memFree(channels);
    }
}
