package com.persiki84.battlecraft.client.menu.desktop;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// WHY: «Добавить» открывал папку обоев, и её принимали за окно выбора файла. Теперь это обычный
// WHY: диалог выбора Windows (tinyfd идёт с Minecraft). Он модальный и ждёт игрока, поэтому живёт
// WHY: в своём потоке, а выбранные файлы уходят в импорт уже на рендер-потоке
final class WallpaperPicker {
    private static final String[] PATTERNS = {"*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.mp4", "*.mov",
            "*.m4v", "*.wmv", "*.avi", "*.mkv", "*.webm"};

    private static volatile boolean asking;

    private WallpaperPicker() {}

    static void pick(Consumer<List<Path>> chosen) {
        if (asking) return;
        asking = true;
        String title = Component.translatable("battlecraft.desktop.wallpaper.pick").getString();
        String kinds = Component.translatable("battlecraft.desktop.wallpaper.kinds").getString();
        Thread thread = new Thread(() -> ask(title, kinds, chosen), "battlecraft-wallpaper-picker");
        thread.setDaemon(true);
        thread.start();
    }

    private static void ask(String title, String kinds, Consumer<List<Path>> chosen) {
        try {
            List<Path> picked = open(title, kinds);
            if (!picked.isEmpty()) Minecraft.getInstance().execute(() -> chosen.accept(picked));
        } catch (Throwable error) {
            LogUtils.getLogger().warn("[battlecraft] wallpaper picker failed: {}", String.valueOf(error));
        } finally {
            asking = false;
        }
    }

    private static List<Path> open(String title, String kinds) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(PATTERNS.length);
            for (String pattern : PATTERNS) filters.put(stack.UTF8(pattern));
            filters.flip();
            String answer = TinyFileDialogs.tinyfd_openFileDialog(title, startFolder(), filters, kinds, true);
            return answer == null ? List.of() : split(answer);
        }
    }

    private static String startFolder() {
        Path pictures = Path.of(System.getProperty("user.home"), "Pictures");
        Path folder = Files.isDirectory(pictures) ? pictures : Path.of(System.getProperty("user.home"));
        return folder + java.io.File.separator;
    }

    private static List<Path> split(String answer) {
        List<Path> paths = new ArrayList<>();
        for (String part : answer.split("\\|")) {
            if (!part.isBlank()) paths.add(Path.of(part));
        }
        return paths;
    }
}
