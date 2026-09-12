package com.persiki84.shared;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class WorldFiles {

    private WorldFiles() {}

    public static Path pathFor(ServerLevel level, String folder, String file) {
        if (level == null || level.getServer() == null) return null;
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve(folder).resolve(file);
    }

    public static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
