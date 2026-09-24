package com.persiki84.battlecraft.rules;

import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class ConfigScope {
    private static final Set<String> PERSONAL_DIRS = Set.of("voicechat", "spark", "backup", "backups", "logs");
    private static final Set<String> PERSONAL_FILES = Set.of("fml.toml", "forge-client.toml");
    private static final List<String> PERSONAL_PREFIXES = List.of("embeddium", "rubidium", "sodium", "oculus",
            "iris", "distanthorizons", "immediatelyfast", "entityculling", "modernfix", "ferritecore", "dynamiclights");
    private static final List<String> VOLATILE_SUFFIXES = List.of(".bak", ".old", ".tmp", ".log", ".lock");
    private static final String VOLATILE_MARK = "cache";
    private static final String OWN_TAILS = "-_.";

    private static volatile Set<String> ownIds;

    private ConfigScope() {}

    public static boolean watched(String path) {
        if (path.isEmpty() || path.length() > ConfigManifest.MAX_PATH) return false;

        String[] segments = path.toLowerCase(Locale.ROOT).split("/");
        for (String segment : segments) {
            if (segment.isEmpty() || segment.startsWith(".") || PERSONAL_DIRS.contains(segment) || own(segment)) {
                return false;
            }
        }
        String name = segments[segments.length - 1];
        return !personal(name) && !temporary(name);
    }

    private static boolean own(String segment) {
        for (String id : ownIds()) {
            if (!segment.startsWith(id)) continue;
            if (segment.length() == id.length() || OWN_TAILS.indexOf(segment.charAt(id.length())) >= 0) return true;
        }
        return false;
    }

    private static boolean personal(String name) {
        if (PERSONAL_FILES.contains(name)) return true;
        for (String prefix : PERSONAL_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    private static boolean temporary(String name) {
        if (name.contains(VOLATILE_MARK)) return true;
        for (String suffix : VOLATILE_SUFFIXES) {
            if (name.endsWith(suffix)) return true;
        }
        return false;
    }

    public static Set<String> ownIds() {
        Set<String> known = ownIds;
        if (known != null) return known;

        ModList mods = ModList.get();
        IModFileInfo file = mods == null ? null : mods.getModFileById(BattleCraftMod.MOD_ID);
        if (file == null) return Set.of(BattleCraftMod.MOD_ID);

        known = file.getMods().stream().map(IModInfo::getModId)
                .map(id -> id.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        ownIds = known;
        return known;
    }
}
