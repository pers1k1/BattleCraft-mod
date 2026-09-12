package com.persiki84.battlecraft.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.hud.HudConfig;
import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LauncherTheme {
    private static final String THEME_FOLDER = "launcher_theme";
    private static final String THEME_FILE = "theme.json";
    private static final String ACCENT_KEY = "accent";
    private static final int HEX_DIGITS = 6;
    private static final int UNSET = 0;

    private LauncherTheme() {}

    public static void apply() {
        Customization.load();
        Customization.apply();
    }

    public static int accent() {
        int chosen = parse(HudConfig.accentColor());
        if (chosen != UNSET) return chosen;
        if (!HudConfig.accentFromLauncher()) return UNSET;

        return parse(readAccent());
    }

    private static String readAccent() {
        Path file = themeFile();
        if (!Files.isRegularFile(file)) return "";

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject theme = JsonParser.parseReader(reader).getAsJsonObject();
            return theme.has(ACCENT_KEY) ? theme.get(ACCENT_KEY).getAsString() : "";
        } catch (Exception error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] cannot read {}: {}", THEME_FILE, error.toString());
            return "";
        }
    }

    private static Path themeFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(THEME_FOLDER).resolve(THEME_FILE);
    }

    private static int parse(String hex) {
        if (hex == null) return UNSET;

        String digits = hex.trim();
        if (digits.startsWith("#")) digits = digits.substring(1);
        if (digits.length() == 8) digits = digits.substring(2);
        if (digits.length() != HEX_DIGITS) return UNSET;

        try {
            return 0xFF000000 | Integer.parseInt(digits, 16);
        } catch (NumberFormatException malformed) {
            return UNSET;
        }
    }
}
