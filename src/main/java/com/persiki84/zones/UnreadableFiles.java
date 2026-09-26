package com.persiki84.zones;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class UnreadableFiles {
    private static final String BROKEN_SUFFIX = ".broken-";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private UnreadableFiles() {}

    // WHY: нечитаемый файл уезжает в сторону, а не перезаписывается пустым списком при первом же
    // WHY: сохранении: разметка карты стоит одного лишнего файла на диске
    public static void setAside(Path file, Logger logger) {
        Path spoiled = file.resolveSibling(file.getFileName() + BROKEN_SUFFIX + LocalDateTime.now().format(STAMP));
        try {
            Files.move(file, spoiled, StandardCopyOption.REPLACE_EXISTING);
            logger.warn("Unreadable {} moved to {}", file.getFileName(), spoiled.getFileName());
        } catch (IOException error) {
            logger.error("Cannot set aside {}", file, error);
        }
    }
}
