package com.persiki84.shared.client.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class MsdfAtlas {
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final String field;
    private final float distanceRange;
    private final int width;
    private final int height;

    private MsdfAtlas(JsonObject root) {
        JsonObject atlas = root.getAsJsonObject("atlas");
        this.field = atlas.get("type").getAsString();
        this.distanceRange = atlas.get("distanceRange").getAsFloat();
        this.width = atlas.get("width").getAsInt();
        this.height = atlas.get("height").getAsInt();

        readGlyphs(root.getAsJsonArray("glyphs"));
    }

    private void readGlyphs(JsonArray entries) {
        for (int i = 0; i < entries.size(); i++) {
            JsonObject entry = entries.get(i).getAsJsonObject();
            int codepoint = entry.get("unicode").getAsInt();
            float advance = entry.get("advance").getAsFloat();

            if (!entry.has("planeBounds") || !entry.has("atlasBounds")) {
                glyphs.put(codepoint, new Glyph(advance, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
                continue;
            }

            JsonObject plane = entry.getAsJsonObject("planeBounds");
            JsonObject sheet = entry.getAsJsonObject("atlasBounds");

            glyphs.put(codepoint, new Glyph(
                    advance,
                    plane.get("left").getAsFloat(),
                    plane.get("bottom").getAsFloat(),
                    plane.get("right").getAsFloat(),
                    plane.get("top").getAsFloat(),
                    sheet.get("left").getAsFloat() / width,
                    1.0f - sheet.get("top").getAsFloat() / height,
                    sheet.get("right").getAsFloat() / width,
                    1.0f - sheet.get("bottom").getAsFloat() / height));
        }
    }

    public static Optional<MsdfAtlas> load(ResourceLocation metadata) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(metadata);
        if (resource.isEmpty()) return Optional.empty();

        try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return Optional.of(new MsdfAtlas(root));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public String field() { return field; }
    public float distanceRange() { return distanceRange; }
    public boolean has(int codepoint) { return glyphs.containsKey(codepoint); }
    public Glyph glyph(int codepoint) { return glyphs.get(codepoint); }

    public record Glyph(float advance,
                        float planeLeft, float planeBottom, float planeRight, float planeTop,
                        float u0, float v0, float u1, float v1) {

        public boolean empty() {
            return planeRight - planeLeft <= 0.0f || planeTop - planeBottom <= 0.0f;
        }
    }
}
