package com.persiki84.battlecraft.client.custom;

import com.persiki84.battlecraft.BattleCraftMod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class PresetLibrary {
    public enum Result {
        SAVED("saved"),
        NO_NAME("no_name"),
        NO_SECTIONS("no_sections"),
        FAILED("failed");

        private final String id;

        Result(String id) {
            this.id = id;
        }

        public String translationKey() {
            return "battlecraft.custom.mine." + id;
        }

        public boolean alerting() {
            return this != SAVED;
        }
    }

    private static final int NAME_LIMIT = 48;
    private static final char FILLER = '_';

    private static final List<UserPreset> presets = new ArrayList<>();
    private static boolean scanned;

    private PresetLibrary() {}

    public static List<UserPreset> all() {
        if (!scanned) refresh();
        return List.copyOf(presets);
    }

    public static void refresh() {
        scanned = true;
        presets.clear();

        Path folder = ConfigFile.presetsFolder();
        if (!Files.isDirectory(folder)) return;

        try (Stream<Path> files = Files.list(folder)) {
            files.filter(PresetLibrary::ours).forEach(PresetLibrary::take);
        } catch (Exception error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] cannot list presets: {}", error.toString());
        }
        presets.sort(Comparator.comparing(UserPreset::name, String.CASE_INSENSITIVE_ORDER));
    }

    private static boolean ours(Path file) {
        return Files.isRegularFile(file)
                && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ConfigFile.EXTENSION);
    }

    private static void take(Path file) {
        ConfigDoc doc = ConfigFile.open(file);
        if (doc == null) return;

        presets.add(new UserPreset(shownName(doc, file), file, doc.sections(), doc.created(), doc));
    }

    private static String shownName(ConfigDoc doc, Path file) {
        String stored = doc.name();
        if (stored != null && !stored.isBlank()) return stored.trim();

        String plain = file.getFileName().toString();
        return plain.substring(0, plain.length() - ConfigFile.EXTENSION.length());
    }

    public static Result save(String rawName, Set<ConfigSection> sections) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) return Result.NO_NAME;
        if (sections.isEmpty()) return Result.NO_SECTIONS;

        String slug = slug(name);
        if (slug.isEmpty()) return Result.NO_NAME;

        ConfigDoc doc = ConfigCodec.snapshot(name, sections);
        if (!ConfigFile.store(doc, ConfigFile.presetsFolder().resolve(slug + ConfigFile.EXTENSION))) {
            return Result.FAILED;
        }
        refresh();
        return Result.SAVED;
    }

    // WHY: имя файла и имя пресета живут врозь: в файле остаются только безопасные для диска
    // WHY: знаки, а показывается игроку то, что он набрал, из поля name внутри документа
    public static String slug(String name) {
        StringBuilder built = new StringBuilder();
        for (char symbol : name.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if (built.length() >= NAME_LIMIT) break;
            built.append(keeps(symbol) ? symbol : FILLER);
        }
        return trimmed(built.toString());
    }

    private static boolean keeps(char symbol) {
        return Character.isLetterOrDigit(symbol) || symbol == '-' || symbol == FILLER;
    }

    private static String trimmed(String slug) {
        int from = 0;
        int until = slug.length();
        while (from < until && slug.charAt(from) == FILLER) from++;
        while (until > from && slug.charAt(until - 1) == FILLER) until--;
        return slug.substring(from, until);
    }

    public static void apply(UserPreset preset) {
        ConfigCodec.applyPresent(preset.doc());
        Customization.apply();
        Customization.save();
    }

    public static boolean delete(UserPreset preset) {
        try {
            Files.deleteIfExists(preset.file());
        } catch (Exception error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] cannot delete {}: {}", preset.file().getFileName(),
                    error.toString());
            return false;
        }
        refresh();
        return true;
    }

    public static Path folder() {
        Path folder = ConfigFile.presetsFolder();
        try {
            Files.createDirectories(folder);
        } catch (Exception error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] cannot open presets folder: {}", error.toString());
        }
        return folder;
    }
}
