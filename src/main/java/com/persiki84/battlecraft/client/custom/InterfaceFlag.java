package com.persiki84.battlecraft.client.custom;

import com.persiki84.battlecraft.client.DiscordRpcManager;
import com.persiki84.battlecraft.client.hud.HudConfig;

import java.util.function.BooleanSupplier;

// WHY: здесь живут только ручки, чей дом в battlecraft-hud.toml: конфиг мода их не хранит,
// WHY: чтобы у значения не было двух источников, и они попадают лишь в пресеты игрока
public enum InterfaceFlag {
    DISCORD("discord", true, HudConfig::discordRpc, InterfaceFlag::discord),
    HUD_ACCENT_TEXT("hudAccentText", true, HudConfig::hudAccentText, HudConfig::hudAccentText),
    ISLAND("island", true, HudConfig::island, HudConfig::island),
    ISLAND_MEDIA("islandMedia", true, HudConfig::islandMedia, HudConfig::islandMedia),
    ISLAND_AVATAR("islandAvatar", true, HudConfig::islandAvatar, HudConfig::islandAvatar),
    ISLAND_NICK("islandNick", true, HudConfig::islandNick, HudConfig::islandNick),
    ISLAND_FPS("islandFps", true, HudConfig::islandFps, HudConfig::islandFps),
    ISLAND_PING("islandPing", true, HudConfig::islandPing, HudConfig::islandPing),
    ISLAND_COVER("islandCover", true, HudConfig::islandCover, HudConfig::islandCover),
    ISLAND_TITLE("islandTitle", true, HudConfig::islandTitle, HudConfig::islandTitle),
    ISLAND_ARTIST("islandArtist", true, HudConfig::islandArtist, HudConfig::islandArtist),
    ISLAND_BAR("islandBar", true, HudConfig::islandBar, HudConfig::islandBar),
    ISLAND_TIME("islandTime", true, HudConfig::islandTime, HudConfig::islandTime),
    ISLAND_VISUALIZER("islandVisualizer", true, HudConfig::islandVisualizer, HudConfig::islandVisualizer),
    ISLAND_COVER_TINT("islandCoverTint", true, HudConfig::islandCoverTint, HudConfig::islandCoverTint),
    RADIO_HUD("radioHud", true, HudConfig::radioHud, HudConfig::radioHud),
    VOICE_TRACE("voiceTrace", true, HudConfig::voiceTrace, HudConfig::voiceTrace),
    RADIO_STATIC("radioStatic", true, HudConfig::radioStatic, HudConfig::radioStatic),
    RADIO_CHIRP("radioChirp", true, HudConfig::radioChirp, HudConfig::radioChirp);

    public interface Switch {
        void set(boolean value);
    }

    private final String id;
    private final boolean fallback;
    private final BooleanSupplier reader;
    private final Switch setter;

    InterfaceFlag(String id, boolean fallback, BooleanSupplier reader, Switch setter) {
        this.id = id;
        this.fallback = fallback;
        this.reader = reader;
        this.setter = setter;
    }

    public String id() {
        return id;
    }

    public boolean fallback() {
        return fallback;
    }

    public boolean get() {
        return reader.getAsBoolean();
    }

    public void set(boolean value) {
        setter.set(value);
    }

    public void reset() {
        set(fallback);
    }
    // WHY: статус Discord собирается заранее и сам не пересматривается, поэтому выключатель
    // WHY: обязан толкнуть менеджер, иначе строка висит в профиле до перезапуска игры
    private static void discord(boolean value) {
        HudConfig.discordRpc(value);
        DiscordRpcManager.getInstance().refresh();
    }
}
