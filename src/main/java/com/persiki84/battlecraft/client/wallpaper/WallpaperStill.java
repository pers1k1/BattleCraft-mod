package com.persiki84.battlecraft.client.wallpaper;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

final class WallpaperStill {
    private WallpaperStill() {}

    static WallpaperClip convert(Path source, Path cache) throws WallpaperFailure {
        BufferedImage fitted = WallpaperScale.fit(decode(source));
        try (WallpaperJpeg jpeg = new WallpaperJpeg()) {
            jpeg.write(fitted, WallpaperCache.frame(cache, 0));
        }
        return WallpaperClip.still(fitted.getWidth(), fitted.getHeight());
    }

    static BufferedImage decode(Path source) throws WallpaperFailure {
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) throw new WallpaperFailure(WallpaperFailure.READ, "unreadable image");
            return read(input);
        } catch (IOException error) {
            throw new WallpaperFailure(WallpaperFailure.READ, error.toString());
        }
    }

    // WHY: размер берётся из заголовка до декодирования, и огромный снимок читается прореженным:
    // WHY: в память ложится уже уменьшенный кадр, а не полный растр на гигабайты
    private static BufferedImage read(ImageInputStream input) throws IOException, WallpaperFailure {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw new WallpaperFailure(WallpaperFailure.READ, "unreadable image");

        ImageReader reader = readers.next();
        try {
            reader.setInput(input, true, true);
            int step = WallpaperScale.subsampling(reader.getWidth(0), reader.getHeight(0));
            ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceSubsampling(step, step, 0, 0);
            BufferedImage image = reader.read(0, param);
            if (image == null) throw new WallpaperFailure(WallpaperFailure.READ, "unreadable image");
            return image;
        } finally {
            reader.dispose();
        }
    }
}
