package com.persiki84.battlecraft.client.wallpaper;

import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class WallpaperGif {
    private static final String STREAM_FORMAT = "javax_imageio_gif_stream_1.0";
    private static final String IMAGE_FORMAT = "javax_imageio_gif_image_1.0";
    private static final String RESTORE_BACKGROUND = "restoreToBackgroundColor";
    private static final String RESTORE_PREVIOUS = "restoreToPrevious";
    private static final int CENTISECONDS = 100;
    private static final int FASTEST_DELAY = 1;
    private static final int BROWSER_DELAY = 10;
    private static final long PLAYED_CENTISECONDS = (long) WallpaperClip.MAX_SECONDS * CENTISECONDS;

    private WallpaperGif() {}

    static WallpaperClip convert(Path source, Path cache, WallpaperImport progress) throws WallpaperFailure {
        ImageReader reader = gifReader();
        try (FileImageInputStream input = new FileImageInputStream(source.toFile())) {
            reader.setInput(input, false, false);
            int count = reader.getNumImages(true);
            if (count <= 1) return WallpaperStill.convert(source, cache);

            Placement[] placements = placements(reader, count);
            return render(reader, placements, screen(reader, placements), cache, progress);
        } catch (IOException | RuntimeException error) {
            throw new WallpaperFailure(WallpaperFailure.READ, error.toString());
        } finally {
            reader.dispose();
        }
    }

    private static ImageReader gifReader() throws WallpaperFailure {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) throw new WallpaperFailure(WallpaperFailure.READ, "no gif reader");
        return readers.next();
    }

    // WHY: кадры дальше предела длительности всё равно не попадут в обои, поэтому гифка на тысячи
    // WHY: кадров разбирается только до него. Кадр и холст больше бюджета отказываются до
    // WHY: декодирования: холст 65535x65535 из заголовка это десятки гигабайт кучи
    private static Placement[] placements(ImageReader reader, int count) throws IOException, WallpaperFailure {
        List<Placement> placements = new ArrayList<>();
        long total = 0L;
        for (int index = 0; index < count && total < PLAYED_CENTISECONDS; index++) {
            fitting(reader.getWidth(index), reader.getHeight(index));
            Placement placement = Placement.of(reader.getImageMetadata(index));
            placements.add(placement);
            total += placement.delay();
        }
        return placements.toArray(new Placement[0]);
    }

    private static void fitting(long width, long height) throws WallpaperFailure {
        if (!WallpaperScale.fits(width, height)) {
            throw new WallpaperFailure(WallpaperFailure.SIZE, width + "x" + height);
        }
    }

    private static int[] screen(ImageReader reader, Placement[] placements) throws IOException, WallpaperFailure {
        IIOMetadata stream = reader.getStreamMetadata();
        Node descriptor = stream == null ? null : child(stream.getAsTree(STREAM_FORMAT), "LogicalScreenDescriptor");
        int width = number(descriptor, "logicalScreenWidth", 0);
        int height = number(descriptor, "logicalScreenHeight", 0);
        if (width <= 0 || height <= 0) {
            width = placements[0].x() + reader.getWidth(0);
            height = placements[0].y() + reader.getHeight(0);
        }
        fitting(width, height);
        return new int[] {width, height};
    }

    private static WallpaperClip render(ImageReader reader, Placement[] placements, int[] screen, Path cache,
                                        WallpaperImport progress) throws IOException, WallpaperFailure {
        long total = 0L;
        for (Placement placement : placements) total += placement.delay();
        int frames = WallpaperClip.frameCount((double) total / CENTISECONDS, WallpaperClip.MAX_FPS);

        Canvas canvas = new Canvas(screen[0], screen[1]);
        Output output = new Output(cache, frames, progress);
        try (WallpaperJpeg jpeg = new WallpaperJpeg()) {
            long end = 0L;
            for (int index = 0; index < placements.length && !output.full(); index++) {
                end += placements[index].delay();
                canvas.draw(reader.read(index), placements[index]);
                output.emitUntil(index == placements.length - 1 ? frames : framesBefore(end, frames), canvas, jpeg);
                canvas.dispose(placements[index]);
            }
        }
        return new WallpaperClip(WallpaperEntry.Kind.ANIMATED, frames, WallpaperClip.MAX_FPS,
                output.width(), output.height());
    }

    private static int framesBefore(long endCentiseconds, int frames) {
        long covered = (endCentiseconds * WallpaperClip.MAX_FPS + CENTISECONDS - 1) / CENTISECONDS;
        return (int) Math.min(frames, covered);
    }

    private static Node child(Node parent, String name) {
        for (Node node = parent == null ? null : parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (name.equals(node.getNodeName())) return node;
        }
        return null;
    }

    private static int number(Node node, String attribute, int fallback) {
        if (!(node instanceof IIOMetadataNode element) || !element.hasAttribute(attribute)) return fallback;
        try {
            return Integer.parseInt(element.getAttribute(attribute));
        } catch (NumberFormatException broken) {
            return fallback;
        }
    }

    private record Placement(int x, int y, String disposal, int delay) {
        // WHY: задержку 0 и 1 сотую браузеры играют как 10 сотых, и авторы GIF на это рассчитывают:
        // WHY: честные 100 кадров в секунду превратили бы такой ролик в мельтешение
        static Placement of(IIOMetadata metadata) {
            Node root = metadata.getAsTree(IMAGE_FORMAT);
            Node descriptor = child(root, "ImageDescriptor");
            Node control = child(root, "GraphicControlExtension");
            String disposal = control instanceof IIOMetadataNode element ? element.getAttribute("disposalMethod") : "";
            int delay = number(control, "delayTime", 0);
            return new Placement(number(descriptor, "imageLeftPosition", 0), number(descriptor, "imageTopPosition", 0),
                    disposal, delay <= FASTEST_DELAY ? BROWSER_DELAY : delay);
        }
    }

    private static final class Canvas {
        private final BufferedImage image;
        private final Graphics2D graphics;
        private BufferedImage saved;
        private int drawnWidth;
        private int drawnHeight;

        Canvas(int width, int height) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            graphics = image.createGraphics();
        }

        void draw(BufferedImage frame, Placement placement) {
            if (RESTORE_PREVIOUS.equals(placement.disposal())) saved = copy(image);
            drawnWidth = frame.getWidth();
            drawnHeight = frame.getHeight();
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.drawImage(frame, placement.x(), placement.y(), null);
        }

        void dispose(Placement placement) {
            if (RESTORE_BACKGROUND.equals(placement.disposal())) {
                graphics.setComposite(AlphaComposite.Clear);
                graphics.fillRect(placement.x(), placement.y(), drawnWidth, drawnHeight);
            } else if (RESTORE_PREVIOUS.equals(placement.disposal()) && saved != null) {
                graphics.setComposite(AlphaComposite.Src);
                graphics.drawImage(saved, 0, 0, null);
            }
        }

        BufferedImage flattened() {
            return WallpaperScale.fit(image);
        }

        private static BufferedImage copy(BufferedImage source) {
            BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D pen = copy.createGraphics();
            pen.setComposite(AlphaComposite.Src);
            pen.drawImage(source, 0, 0, null);
            pen.dispose();
            return copy;
        }
    }

    private static final class Output {
        private final Path cache;
        private final int frames;
        private final WallpaperImport progress;
        private int written;
        private int width = 1;
        private int height = 1;

        Output(Path cache, int frames, WallpaperImport progress) {
            this.cache = cache;
            this.frames = frames;
            this.progress = progress;
        }

        boolean full() {
            return written >= frames;
        }

        // WHY: кадр сетки 30 к/с берёт то, что GIF показывает в его момент; повтор того же кадра
        // WHY: не жмётся заново, а копируется файлом
        void emitUntil(int limit, Canvas canvas, WallpaperJpeg jpeg) throws IOException, WallpaperFailure {
            int first = written;
            while (written < limit) {
                if (written == first) encode(canvas, jpeg);
                else Files.copy(WallpaperCache.frame(cache, first), WallpaperCache.frame(cache, written),
                        StandardCopyOption.REPLACE_EXISTING);
                written++;
                progress.advance((float) written / frames);
            }
        }

        private void encode(Canvas canvas, WallpaperJpeg jpeg) throws WallpaperFailure {
            BufferedImage frame = canvas.flattened();
            width = frame.getWidth();
            height = frame.getHeight();
            jpeg.write(frame, WallpaperCache.frame(cache, written));
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }
    }
}
