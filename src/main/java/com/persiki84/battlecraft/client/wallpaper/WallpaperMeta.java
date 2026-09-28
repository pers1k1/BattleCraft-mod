package com.persiki84.battlecraft.client.wallpaper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

record WallpaperMeta(int version, String id, String source, long size, long modified,
                     WallpaperEntry.Kind kind, int frames, float fps, int width, int height) {
    static final int VERSION = 1;
    static final String FILE = "meta.json";

    WallpaperEntry entry() {
        return new WallpaperEntry(id, source, kind, frames, fps, width, height);
    }

    boolean describes(String name, long bytes, long modifiedMs) {
        return source.equals(name) && size == bytes && modified == modifiedMs;
    }

    // WHY: meta пишется последним и только целиком: битый или недописанный файл, как и кеш без
    // WHY: последнего кадра, значит прерванный импорт, и такие обои собираются заново, а не падают
    static WallpaperMeta read(Path cache) {
        Path file = cache.resolve(FILE);
        if (!Files.isRegularFile(file)) return null;

        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            WallpaperMeta meta = parse(json);
            return meta.complete(cache) ? meta : null;
        } catch (IOException | RuntimeException broken) {
            return null;
        }
    }

    private static WallpaperMeta parse(JsonObject json) {
        return new WallpaperMeta(json.get("version").getAsInt(), json.get("id").getAsString(),
                json.get("source").getAsString(), json.get("size").getAsLong(), json.get("modified").getAsLong(),
                WallpaperEntry.Kind.valueOf(json.get("kind").getAsString()), json.get("frames").getAsInt(),
                json.get("fps").getAsFloat(), json.get("width").getAsInt(), json.get("height").getAsInt());
    }

    private boolean complete(Path cache) {
        if (version != VERSION || frames < 1 || width < 1 || height < 1) return false;
        if (kind == WallpaperEntry.Kind.ANIMATED && (fps <= 0.0f || frames < 2)) return false;
        return Files.isRegularFile(WallpaperCache.frame(cache, frames - 1));
    }

    void write(Path cache) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("version", version);
        json.addProperty("id", id);
        json.addProperty("source", source);
        json.addProperty("size", size);
        json.addProperty("modified", modified);
        json.addProperty("kind", kind.name());
        json.addProperty("frames", frames);
        json.addProperty("fps", fps);
        json.addProperty("width", width);
        json.addProperty("height", height);

        Path part = cache.resolve(FILE + ".part");
        Files.writeString(part, json.toString(), StandardCharsets.UTF_8);
        Files.move(part, cache.resolve(FILE), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
