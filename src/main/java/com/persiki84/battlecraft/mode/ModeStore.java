package com.persiki84.battlecraft.mode;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.shared.JsonRead;
import com.persiki84.shared.WorldFiles;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// WHY: режимы принадлежат миру, как и карта с точками: другой мир - другие цифры
final class ModeStore {
    private static final String FOLDER = "battlecraft";
    private static final String FILE = "modes.json";
    private static final String TEMPORARY = ".tmp";
    private static final int FORMAT = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ModeStore() {}

    static Path pathOf(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FOLDER).resolve(FILE);
    }

    static JsonObject load(MinecraftServer server) {
        Path file = pathOf(server);
        if (!Files.exists(file)) return new JsonObject();

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) throw new IllegalStateException("root is not an object");
            return root.getAsJsonObject();
        } catch (Exception unreadable) {
            BattleCraftMod.LOGGER.error("Cannot read {}", file, unreadable);
            WorldFiles.setAside(file, BattleCraftMod.LOGGER);
            return new JsonObject();
        }
    }

    static JsonObject modeOf(JsonObject root, ModeId id) {
        JsonObject modes = JsonRead.object(root, "modes");
        JsonObject mode = modes == null ? null : JsonRead.object(modes, id.id());
        return mode == null ? new JsonObject() : mode;
    }

    static void save(MinecraftServer server, JsonObject root) {
        Path file = pathOf(server);
        Path temporary = file.resolveSibling(FILE + TEMPORARY);
        root.addProperty("format", FORMAT);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            WorldFiles.moveIntoPlace(temporary, file);
        } catch (IOException failure) {
            BattleCraftMod.LOGGER.error("Failed to save match modes", failure);
        }
    }
}
