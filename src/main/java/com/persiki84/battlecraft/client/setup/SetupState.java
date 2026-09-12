package com.persiki84.battlecraft.client.setup;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.shared.WorldFiles;
import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SetupState {
    private static final String FOLDER = "battlecraft";
    private static final String FILE = "setup.json";
    private static final String DONE_KEY = "done";

    private static final Set<SetupStep> done = EnumSet.noneOf(SetupStep.class);
    private static final Set<String> foreign = new LinkedHashSet<>();
    private static boolean loaded;

    private SetupState() {}

    public static void load() {
        if (loaded) return;

        loaded = true;
        Path file = file();
        if (!Files.isRegularFile(file)) return;

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed.isJsonObject()) read(parsed.getAsJsonObject());
        } catch (Exception error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] cannot read {}: {}", FILE, error.toString());
        }
    }

    // WHY: шаг чужой версии мода запоминается строкой и возвращается в файл: откат сборки
    // WHY: назад и обратно иначе заставил бы игрока проходить уже пройденное второй раз
    private static void read(JsonObject saved) {
        JsonElement answered = saved.get(DONE_KEY);
        if (answered == null || !answered.isJsonArray()) return;

        for (JsonElement element : answered.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) continue;

            String id = element.getAsString();
            SetupStep step = SetupStep.byId(id);
            if (step == null) {
                foreign.add(id);
            } else {
                done.add(step);
            }
        }
    }

    public static List<SetupStep> pending() {
        load();
        List<SetupStep> waiting = new ArrayList<>();
        for (SetupStep step : SetupStep.values()) {
            if (!done.contains(step)) waiting.add(step);
        }
        return waiting;
    }

    public static boolean needed() {
        return !pending().isEmpty();
    }

    public static void answered(SetupStep step) {
        load();
        if (step != null) done.add(step);
    }

    public static void skipped(SetupStep step) {
        answered(step);
    }

    public static void save() {
        JsonArray answered = new JsonArray();
        for (SetupStep step : SetupStep.values()) {
            if (done.contains(step)) answered.add(step.id());
        }
        for (String id : foreign) {
            answered.add(id);
        }
        JsonObject saved = new JsonObject();
        saved.add(DONE_KEY, answered);

        Path file = file();
        Path temp = file.resolveSibling(FILE + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temp, saved.toString(), StandardCharsets.UTF_8);
            WorldFiles.moveIntoPlace(temp, file);
        } catch (Exception error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] cannot write {}: {}", FILE, error.toString());
        }
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(FOLDER).resolve(FILE);
    }
}
