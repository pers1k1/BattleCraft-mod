package com.persiki84.shared;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class WorldFiles {
    private static final String BROKEN_SUFFIX = ".broken-";
    private static final String PARTIAL_SUFFIX = ".partial-";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

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

    // WHY: нечитаемый файл уезжает в сторону, а не затирается пустым при первой записи: разобрать
    // WHY: его руками дешевле, чем потерять данные мира
    public static void setAside(Path file, Logger logger) {
        Path spoiled = file.resolveSibling(file.getFileName() + BROKEN_SUFFIX + LocalDateTime.now().format(STAMP));
        try {
            Files.move(file, spoiled, StandardCopyOption.REPLACE_EXISTING);
            logger.warn("Unreadable {} moved to {}", file.getFileName(), spoiled.getFileName());
        } catch (IOException error) {
            logger.error("Cannot set aside {}", file, error);
        }
    }

    public static void keepCopy(Path file, Logger logger) {
        Path copy = file.resolveSibling(file.getFileName() + PARTIAL_SUFFIX + LocalDateTime.now().format(STAMP));
        try {
            Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
            logger.warn("Some records of {} were unreadable, the original is kept as {}", file.getFileName(),
                    copy.getFileName());
        } catch (IOException error) {
            logger.error("Cannot copy {}", file, error);
        }
    }
}
