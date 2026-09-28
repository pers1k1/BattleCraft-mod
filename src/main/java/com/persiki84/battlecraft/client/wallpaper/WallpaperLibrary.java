package com.persiki84.battlecraft.client.wallpaper;

import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class WallpaperLibrary {
    private static final String FOLDER = "battlecraft";

    private static WallpaperStore store;

    private WallpaperLibrary() {}

    static synchronized WallpaperStore store() {
        if (store == null) store = new WallpaperStore(Minecraft.getInstance().gameDirectory.toPath().resolve(FOLDER));
        return store;
    }

    public static Path folder() {
        Path folder = store().folder();
        try {
            Files.createDirectories(folder);
        } catch (IOException error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] wallpaper folder not created: {}", error.toString());
        }
        return folder;
    }

    public static List<WallpaperEntry> entries() {
        return store().entries();
    }

    public static boolean has(String id) {
        WallpaperMeta meta = store().meta(id);
        return meta != null && meta.id().equals(id);
    }

    public static boolean supports(Path file) {
        return WallpaperFormat.of(file.getFileName().toString()) != null;
    }

    public static boolean refused(Path file) {
        return store().refused(file);
    }

    public static WallpaperImport importFile(Path source) {
        return store().importFile(source);
    }

    public static boolean remove(String id) {
        if (WallpaperPlayer.showing(id)) WallpaperPlayer.hide();
        return store().remove(id);
    }
}
