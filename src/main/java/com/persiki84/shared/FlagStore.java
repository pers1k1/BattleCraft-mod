package com.persiki84.shared;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

// WHY: выключатели модулей и игровые правила держали два побайтово одинаковых хранилища, и любая
// WHY: правка формата требовалась в обоих местах сразу
public final class FlagStore<E extends Enum<E>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TEMPORARY = ".tmp";

    private final String fileName;
    private final E[] keys;
    private final Function<E, String> id;
    private final Function<E, Boolean> fallback;
    private final Map<E, Boolean> values = new LinkedHashMap<>();

    private JsonObject attic = new JsonObject();

    public FlagStore(String fileName, E[] keys, Function<E, String> id, Function<E, Boolean> fallback) {
        this.fileName = fileName;
        this.keys = keys;
        this.id = id;
        this.fallback = fallback;
    }

    public boolean allows(E key) {
        Boolean stored = values.get(key);
        return stored == null ? fallback.apply(key) : stored;
    }

    public void set(E key, boolean enabled) {
        values.put(key, enabled);
        save();
    }

    public int mask() {
        int mask = 0;
        for (E key : keys) {
            if (allows(key)) mask |= 1 << key.ordinal();
        }
        return mask;
    }

    public void load() {
        values.clear();
        attic = new JsonObject();
        Path file = path();
        if (!Files.exists(file)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed.isJsonObject()) read(parsed.getAsJsonObject());
        } catch (Exception error) {
            LOGGER.warn("[battlecraft] cannot read {}: {}", fileName, error.toString());
        }
    }

    // WHY: незнакомые ключи чужой версии мода остаются в документе и уезжают обратно в файл:
    // WHY: откат сборки на версию назад иначе стирал бы настройки, которых эта версия не знает
    private void read(JsonObject stored) {
        attic = stored;
        for (E key : keys) {
            Boolean saved = JsonRead.flag(stored, id.apply(key));
            if (saved != null) values.put(key, saved);
        }
    }

    private void save() {
        for (E key : keys) {
            attic.addProperty(id.apply(key), allows(key));
        }

        Path file = path();
        Path temporary = file.resolveSibling(fileName + TEMPORARY);
        try {
            Files.writeString(temporary, GSON.toJson(attic), StandardCharsets.UTF_8);
            WorldFiles.moveIntoPlace(temporary, file);
        } catch (Exception error) {
            LOGGER.warn("[battlecraft] cannot write {}: {}", fileName, error.toString());
        }
    }

    private Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(fileName);
    }
}
