package com.persiki84.battlecraft.client.custom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ConfigFile {
    public static final String EXTENSION = ".bcfg";

    private static final String FOLDER = "battlecraft";
    private static final String PRESETS = "presets";
    private static final String FILE = "customization" + EXTENSION;
    private static final String LEGACY = "customization.json";
    private static final String LEGACY_RETIRED = "customization.json.old";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String BROKEN_SUFFIX = ".broken-";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ConfigDoc live = ConfigDoc.fresh(ConfigDoc.KIND_CONFIG);

    private ConfigFile() {}

    // WHY: раскатка идёт на любом пути, включая первый запуск без файла: пустой документ
    // WHY: это выбранный по умолчанию пресет, и его тюнинг обязан встать так же, как из файла
    static void read() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            readLegacy();
            return;
        }

        ConfigDoc found = open(file);
        if (found == null) {
            quarantine(file);
        } else {
            live = found;
        }
        ConfigCodec.applyAll(live);
    }

    // WHY: старый json остаётся на диске нетронутым до тех пор, пока новый файл не лёг на место,
    // WHY: и только после этого получает суффикс .old: обрыв посреди перехода ничего не теряет
    private static void readLegacy() {
        Path legacy = folder().resolve(LEGACY);
        JsonObject flat = Files.isRegularFile(legacy) ? parse(legacy) : null;
        if (flat != null) live = ConfigMigrations.fromLegacy(flat);

        ConfigCodec.applyAll(live);
        if (flat != null && write()) retire(legacy);
    }

    static boolean write() {
        ConfigCodec.captureAll(live);
        return store(live, file());
    }

    public static ConfigDoc open(Path file) {
        JsonObject parsed = parse(file);
        if (parsed == null) return null;

        ConfigDoc doc = ConfigDoc.wrap(parsed);
        if (!doc.ours()) {
            BattleCraftMod.LOGGER.warn("[battlecraft] {} is not a battlecraft config", file.getFileName());
            return null;
        }
        ConfigMigrations.run(doc);
        return doc;
    }

    private static JsonObject parse(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed.isJsonObject()) return parsed.getAsJsonObject();

            BattleCraftMod.LOGGER.warn("[battlecraft] {} holds no object", file.getFileName());
        } catch (Exception error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] cannot read {}: {}", file.getFileName(), error.toString());
        }
        return null;
    }

    public static boolean store(ConfigDoc doc, Path file) {
        Path temp = file.resolveSibling(file.getFileName() + TEMP_SUFFIX);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temp, GSON.toJson(doc.root()), StandardCharsets.UTF_8);
            WorldFiles.moveIntoPlace(temp, file);
            return true;
        } catch (Exception error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] cannot write {}: {}", file.getFileName(), error.toString());
            return false;
        }
    }

    // WHY: нечитаемый файл уезжает в сторону, а не перезаписывается первым же сохранением:
    // WHY: настройки года работы стоят одного лишнего файла на диске
    private static void quarantine(Path file) {
        Path spoiled = file.resolveSibling(file.getFileName() + BROKEN_SUFFIX
                + LocalDateTime.now().format(STAMP));
        try {
            Files.move(file, spoiled, StandardCopyOption.REPLACE_EXISTING);
            BattleCraftMod.LOGGER.warn("[battlecraft] broken config moved to {}", spoiled.getFileName());
        } catch (Exception error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] cannot set aside {}: {}", file.getFileName(),
                    error.toString());
        }
    }

    private static void retire(Path legacy) {
        try {
            Files.move(legacy, legacy.resolveSibling(LEGACY_RETIRED), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] cannot retire {}: {}", LEGACY, error.toString());
        }
    }

    public static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(FOLDER);
    }

    public static Path presetsFolder() {
        return folder().resolve(PRESETS);
    }

    private static Path file() {
        return folder().resolve(FILE);
    }
}
