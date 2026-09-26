package com.persiki84.airdrop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class AirDropFiles {
    private static final String BROKEN_SUFFIX = ".broken-";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private AirDropFiles() {}

    // WHY: нечитаемый файл уезжает в сторону, а не перезаписывается следующим сохранением: пустой
    // WHY: список поверх стёр бы тайники и таблицы насовсем, а из отложенного их восстановят руками
    public static void setAside(Path file) {
        Path spoiled = file.resolveSibling(file.getFileName() + BROKEN_SUFFIX + LocalDateTime.now().format(STAMP));
        try {
            Files.move(file, spoiled, StandardCopyOption.REPLACE_EXISTING);
            AirDropMod.LOGGER.warn("[airdrop] unreadable {} moved to {}", file.getFileName(), spoiled.getFileName());
        } catch (IOException error) {
            AirDropMod.LOGGER.warn("[airdrop] cannot set aside {}: {}", file.getFileName(), error.toString());
        }
    }
}
