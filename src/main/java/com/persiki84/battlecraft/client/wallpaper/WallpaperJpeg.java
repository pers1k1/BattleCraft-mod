package com.persiki84.battlecraft.client.wallpaper;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

final class WallpaperJpeg implements AutoCloseable {
    private static final float QUALITY = 0.9f;

    private final ImageWriter writer;
    private final ImageWriteParam param;

    WallpaperJpeg() throws WallpaperFailure {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new WallpaperFailure(WallpaperFailure.WRITE, "no jpeg writer");

        writer = writers.next();
        param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(QUALITY);
    }

    void write(BufferedImage image, Path target) throws WallpaperFailure {
        try {
            Files.deleteIfExists(target);
            try (FileImageOutputStream output = new FileImageOutputStream(target.toFile())) {
                writer.setOutput(output);
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.setOutput(null);
            }
        } catch (IOException error) {
            throw new WallpaperFailure(WallpaperFailure.WRITE, error.toString());
        }
    }

    @Override
    public void close() {
        writer.dispose();
    }
}
