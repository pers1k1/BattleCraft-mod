package com.persiki84.battlecraft.craft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.shared.JsonRead;
import com.persiki84.shared.WorldFiles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class CraftBanStore {
    private static final String FOLDER = "battlecraft";
    private static final String FILE = "craft_bans.json";
    private static final String TEMPORARY = ".tmp";
    private static final int FORMAT = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    record Loaded(List<CraftBan> bans, List<JsonObject> attic) {
        static final Loaded EMPTY = new Loaded(List.of(), List.of());
    }

    private CraftBanStore() {}

    static Path pathOf(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FOLDER).resolve(FILE);
    }

    static Loaded load(MinecraftServer server) {
        Path file = pathOf(server);
        if (!Files.exists(file)) return Loaded.EMPTY;

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) throw new IllegalStateException("root is not an object");
            return read(root.getAsJsonObject());
        } catch (Exception unreadable) {
            BattleCraftMod.LOGGER.error("Cannot read {}", file, unreadable);
            WorldFiles.setAside(file, BattleCraftMod.LOGGER);
            return Loaded.EMPTY;
        }
    }

    // WHY: запись, которую не удалось понять (предмета нет в сборке, потому что мод сняли на время,
    // WHY: или тег испорчен руками), не выбрасывается: она лежит на чердаке и уходит в файл обратно,
    // WHY: иначе первое же сохранение стёрло бы её молча
    private static Loaded read(JsonObject root) {
        List<CraftBan> bans = new ArrayList<>();
        List<JsonObject> attic = new ArrayList<>();
        JsonElement listed = root.get("bans");
        JsonArray rows = listed != null && listed.isJsonArray() ? listed.getAsJsonArray() : new JsonArray();
        for (JsonElement row : rows) {
            if (!row.isJsonObject()) continue;
            JsonObject entry = row.getAsJsonObject();
            CraftBan ban = parse(entry);
            if (ban != null) {
                bans.add(ban);
            } else {
                BattleCraftMod.LOGGER.warn("Craft ban {} is not understood and kept as is", entry);
                attic.add(entry);
            }
        }
        return new Loaded(bans, attic);
    }

    @Nullable
    private static CraftBan parse(JsonObject entry) {
        ResourceLocation id = ResourceLocation.tryParse(textOf(entry, "item"));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;

        CraftPhase phase = phaseOf(entry);
        if (phase == null) return null;

        Item item = BuiltInRegistries.ITEM.get(id);
        String snbt = textOf(entry, "tag");
        try {
            CompoundTag tag = snbt.isEmpty() ? null : TagParser.parseTag(snbt);
            return new CraftBan(item, tag, phase);
        } catch (CommandSyntaxException broken) {
            return null;
        }
    }

    // WHY: без ключа фаза это «всегда», как у первых записей формата. Незнакомая фаза (опечатка,
    // WHY: фаза будущей версии) не подменяется «всегда»: такой запрет действовал бы и там, где не
    // WHY: задумывался, а сохранение переписало бы исходное значение, поэтому запись уходит на чердак
    @Nullable
    private static CraftPhase phaseOf(JsonObject entry) {
        String id = textOf(entry, "phase");
        return id.isEmpty() ? CraftPhase.ALWAYS : CraftPhase.byId(id);
    }

    private static String textOf(JsonObject entry, String key) {
        String value = JsonRead.text(entry, key);
        return value == null ? "" : value;
    }

    static void save(MinecraftServer server, List<CraftBan> bans, List<JsonObject> attic) {
        Path file = pathOf(server);
        Path temporary = file.resolveSibling(FILE + TEMPORARY);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(document(bans, attic), writer);
            }
            WorldFiles.moveIntoPlace(temporary, file);
        } catch (IOException failure) {
            BattleCraftMod.LOGGER.error("Failed to save craft bans", failure);
        }
    }

    private static JsonObject document(List<CraftBan> bans, List<JsonObject> attic) {
        JsonArray rows = new JsonArray();
        for (CraftBan ban : bans) {
            JsonObject entry = new JsonObject();
            entry.addProperty("item", ban.itemId());
            if (ban.tag() != null) entry.addProperty("tag", ban.tag().toString());
            entry.addProperty("phase", ban.phase().id());
            rows.add(entry);
        }
        for (JsonObject kept : attic) {
            rows.add(kept);
        }
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.add("bans", rows);
        return root;
    }
}
