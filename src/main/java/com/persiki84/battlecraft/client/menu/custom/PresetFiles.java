package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.custom.ConfigDoc;
import com.persiki84.battlecraft.client.custom.ConfigFile;
import com.persiki84.battlecraft.client.custom.PresetLibrary;
import com.persiki84.battlecraft.client.custom.UserPreset;

import java.nio.file.Files;
import java.nio.file.Path;

// WHY: переименование и копия пресета работают с готовым документом из файла, а не со снимком
// WHY: текущих настроек: пресет переносит ровно то, что в нём сохранили, а не то, что сейчас на экране
final class PresetFiles {
    enum Result { DONE, NO_NAME, TAKEN, FAILED }

    private static final int COPY_LIMIT = 99;

    private PresetFiles() {
    }

    static Result rename(UserPreset preset, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        String slug = PresetLibrary.slug(name);
        if (slug.isEmpty()) return Result.NO_NAME;
        Path target = PresetLibrary.folder().resolve(slug + ConfigFile.EXTENSION);
        boolean sameFile = target.equals(preset.file());
        if (!sameFile && Files.exists(target)) return Result.TAKEN;
        if (!ConfigFile.store(copyNamed(preset, name), target)) return Result.FAILED;
        if (!sameFile) remove(preset.file());
        PresetLibrary.refresh();
        return Result.DONE;
    }

    static Path duplicate(UserPreset preset, String name) {
        for (int index = 1; index <= COPY_LIMIT; index++) {
            String candidate = index == 1 ? name : name + " " + index;
            String slug = PresetLibrary.slug(candidate);
            if (slug.isEmpty()) return null;
            Path target = PresetLibrary.folder().resolve(slug + ConfigFile.EXTENSION);
            if (Files.exists(target)) continue;
            if (!ConfigFile.store(copyNamed(preset, candidate), target)) return null;
            PresetLibrary.refresh();
            return target;
        }
        return null;
    }

    private static ConfigDoc copyNamed(UserPreset preset, String name) {
        ConfigDoc copy = ConfigDoc.wrap(preset.doc().root().deepCopy());
        copy.name(name);
        return copy;
    }

    private static void remove(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (Exception error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] cannot remove renamed preset {}: {}", file.getFileName(),
                    error.toString());
        }
    }
}
