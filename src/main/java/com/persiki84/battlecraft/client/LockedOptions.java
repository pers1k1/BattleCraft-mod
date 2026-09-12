package com.persiki84.battlecraft.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.shared.JsonRead;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// WHY: правило зажимает ванильные настройки в памяти, но ваниль сохраняет options.txt сама - при
// WHY: закрытии игры прямо из мира в файл уходят нули, и настройка игрока пропадает насовсем.
// WHY: Поэтому снимок ложится на диск: следующий запуск возвращает значения и убирает журнал
public final class LockedOptions {
    private static final String FOLDER = "battlecraft";
    private static final String FILE = "glint-restore.json";
    private static final String SPEED = "glintSpeed";
    private static final String STRENGTH = "glintStrength";

    private LockedOptions() {}

    public static void hold(double speed, double strength) {
        JsonObject body = new JsonObject();
        body.addProperty(SPEED, speed);
        body.addProperty(STRENGTH, strength);
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            Files.writeString(file, body.toString(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] glint snapshot not saved: {}", error.toString());
        }
    }

    public static void drop() {
        try {
            Files.deleteIfExists(file());
        } catch (IOException error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] glint snapshot left on disk: {}", error.toString());
        }
    }

    public static double[] read() {
        Path file = file();
        if (!Files.isRegularFile(file)) return null;

        try {
            JsonObject body = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Float speed = JsonRead.number(body, SPEED);
            Float strength = JsonRead.number(body, STRENGTH);
            if (speed == null || strength == null) return null;

            return new double[]{speed, strength};
        } catch (Exception error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] glint snapshot unreadable: {}", error.toString());
            return null;
        }
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(FOLDER).resolve(FILE);
    }
}
