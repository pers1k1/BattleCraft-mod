package com.persiki84.battlecraft.client.wallpaper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

final class WallpaperVideoFeed {
    private static final int RAW_CHANNELS = 4;

    private final Path raw;
    private final Path cache;
    private final WallpaperImport progress;
    private volatile long lastHeardMs = System.currentTimeMillis();
    private float fps;
    private ByteBuffer bytes;
    private WallpaperEncoders encoders;

    WallpaperVideoFeed(Path raw, Path cache, WallpaperImport progress) {
        this.raw = raw;
        this.cache = cache;
        this.progress = progress;
    }

    long lastHeardMs() {
        return lastHeardMs;
    }

    WallpaperClip consume(InputStream stream) throws WallpaperFailure {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                lastHeardMs = System.currentTimeMillis();
                WallpaperClip finished = accept(line.trim());
                if (finished != null) return finished;
            }
        } catch (IOException error) {
            throw new WallpaperFailure(WallpaperFailure.BRIDGE, error.toString());
        } finally {
            if (encoders != null) encoders.close();
        }
        throw new WallpaperFailure(WallpaperFailure.BRIDGE, "bridge exited");
    }

    private WallpaperClip accept(String line) throws WallpaperFailure {
        JsonObject message = parse(line);
        if (message == null) return null;
        if (message.has("error")) throw failure(message.get("error").getAsString());
        if (message.has("clip")) begin(message);
        else if (message.has("frame")) store(message.get("frame").getAsInt());
        else if (message.has("done")) return finish(message.get("done").getAsInt());
        return null;
    }

    private static JsonObject parse(String line) {
        if (line.isEmpty() || line.charAt(0) != '{') return null;
        try {
            return JsonParser.parseString(line).getAsJsonObject();
        } catch (RuntimeException garbled) {
            return null;
        }
    }

    private static WallpaperFailure failure(String reason) {
        String lowered = reason.toLowerCase(Locale.ROOT);
        if (lowered.startsWith("bridge")) return new WallpaperFailure(WallpaperFailure.BRIDGE, reason);
        boolean codec = lowered.startsWith("codec") || lowered.startsWith("open") || lowered.startsWith("decode")
                || lowered.startsWith("stalled") || lowered.startsWith("video");
        return new WallpaperFailure(codec ? WallpaperFailure.CODEC : WallpaperFailure.VIDEO, reason);
    }

    private void begin(JsonObject clip) throws WallpaperFailure {
        int width = clip.get("width").getAsInt();
        int height = clip.get("height").getAsInt();
        int rotation = clip.has("rotation") ? clip.get("rotation").getAsInt() : 0;
        if (width < 1 || height < 1 || !WallpaperScale.fits(width, height)) {
            throw new WallpaperFailure(WallpaperFailure.SIZE, width + "x" + height);
        }
        fps = clip.get("fps").getAsFloat();
        bytes = ByteBuffer.allocateDirect(width * height * RAW_CHANNELS).order(ByteOrder.LITTLE_ENDIAN);
        encoders = new WallpaperEncoders(width, height, rotation, clip.get("count").getAsInt(), cache, progress);
        try {
            Files.createDirectories(WallpaperCache.frames(cache));
        } catch (IOException error) {
            throw new WallpaperFailure(WallpaperFailure.WRITE, error.toString());
        }
    }

    // WHY: мост отдаёт BGRX построчно сверху вниз; в little-endian это ровно 0xXXRRGGBB, то есть
    // WHY: пиксель TYPE_INT_RGB, и кадр ложится в растр одним копированием без разбора по байтам.
    // WHY: Сырой кадр удаляется сразу после чтения: мост ждёт этого, прежде чем писать следующие
    private void store(int index) throws WallpaperFailure {
        if (encoders == null) throw new WallpaperFailure(WallpaperFailure.BRIDGE, "frame before clip");

        Path file = raw.resolve(String.format(Locale.ROOT, "%06d.raw", index));
        try {
            readFully(file);
            encoders.encode(bytes.asIntBuffer(), index);
            Files.delete(file);
        } catch (IOException error) {
            throw new WallpaperFailure(WallpaperFailure.BRIDGE, error.toString());
        }
    }

    private void readFully(Path file) throws IOException {
        bytes.clear();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            while (bytes.hasRemaining() && channel.read(bytes) >= 0) {
                lastHeardMs = System.currentTimeMillis();
            }
        }
        if (bytes.hasRemaining()) throw new IOException("short frame " + file.getFileName());
        bytes.flip();
    }

    private WallpaperClip finish(int stored) throws WallpaperFailure {
        if (stored < 1 || encoders == null) throw new WallpaperFailure(WallpaperFailure.CODEC, "no frames");

        encoders.finish();
        int width = encoders.fittedWidth();
        int height = encoders.fittedHeight();
        if (stored == 1) return WallpaperClip.still(width, height);
        return new WallpaperClip(WallpaperEntry.Kind.ANIMATED, stored, fps, width, height);
    }
}
