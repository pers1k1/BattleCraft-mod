package com.persiki84.battlecraft.client.media;

import java.util.Locale;

public enum MediaSource {
    SPOTIFY("Spotify"),
    SOUNDCLOUD("SoundCloud"),
    YOUTUBE_MUSIC("YouTube Music"),
    YOUTUBE("YouTube"),
    YANDEX_MUSIC("Yandex Music"),
    VK("VK"),
    TWITCH("Twitch"),
    DEEZER("Deezer"),
    BANDCAMP("Bandcamp"),
    APPLE_MUSIC("Apple Music"),
    ZVUK("Zvuk"),

    CHROME("Chrome"),
    CHROMIUM("Chromium"),
    EDGE("Edge"),
    FIREFOX("Firefox"),
    ZEN("Zen"),
    TOR("Tor"),
    BRAVE("Brave"),
    OPERA_GX("Opera GX"),
    OPERA("Opera"),
    YANDEX_BROWSER("Yandex"),
    VIVALDI("Vivaldi"),

    VLC("VLC"),
    AIMP("AIMP"),
    FOOBAR("foobar2000"),
    TELEGRAM("Telegram"),
    MEDIA_PLAYER("Media Player"),
    WINDOWS_MEDIA_PLAYER("Windows Media Player"),
    WINAMP("Winamp"),
    MUSICBEE("MusicBee"),
    POTPLAYER("PotPlayer"),
    MPC("MPC"),
    ITUNES("iTunes"),
    GENERIC("");

    private final String label;

    MediaSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean known() {
        return this != GENERIC;
    }

    public static MediaSource ofSite(String site) {
        if (site == null || site.isEmpty()) return GENERIC;

        String needle = site.toLowerCase(Locale.ROOT);
        if (needle.contains("soundcloud")) return SOUNDCLOUD;
        if (needle.contains("youtube music")) return YOUTUBE_MUSIC;
        if (needle.contains("youtube")) return YOUTUBE;
        if (needle.contains("spotify")) return SPOTIFY;
        if (needle.contains("music.yandex")) return YANDEX_MUSIC;
        if (needle.contains("vk")) return VK;
        if (needle.contains("twitch")) return TWITCH;
        if (needle.contains("deezer")) return DEEZER;
        if (needle.contains("bandcamp")) return BANDCAMP;
        if (needle.contains("apple music")) return APPLE_MUSIC;
        if (needle.contains("zvuk")) return ZVUK;
        return GENERIC;
    }

    public static MediaSource ofApp(String app) {
        if (app == null || app.isEmpty()) return GENERIC;

        String needle = app.toLowerCase(Locale.ROOT);
        MediaSource player = ofPlayer(needle);
        if (player != GENERIC) return player;
        if (needle.contains("spotify")) return SPOTIFY;
        if (needle.contains("yandexmusic") || needle.contains("yandex music")) return YANDEX_MUSIC;
        if (needle.contains("chromium")) return CHROMIUM;
        if (needle.contains("chrome")) return CHROME;
        if (needle.contains("msedge") || needle.contains("edge")) return EDGE;
        if (needle.contains("zen")) return ZEN;
        if (needle.contains("tor")) return TOR;
        if (needle.contains("firefox") || needle.contains("librewolf") || needle.contains("waterfox")) return FIREFOX;
        if (needle.contains("brave")) return BRAVE;
        if (needle.contains("operagx") || needle.contains("opera gx")) return OPERA_GX;
        if (needle.contains("opera")) return OPERA;
        if (needle.contains("vivaldi")) return VIVALDI;
        if (needle.contains("browser") || needle.contains("yandex")) return YANDEX_BROWSER;
        if (needle.contains("vlc")) return VLC;
        if (needle.contains("aimp")) return AIMP;
        if (needle.contains("foobar")) return FOOBAR;
        return GENERIC;
    }

    // WHY: проверяется раньше браузеров: сессия Windows у магазинных приложений и Telegram
    // WHY: называется идентификатором пакета, и в «microsoft.zunemusic» или «unigram» проверки
    // WHY: браузеров по короткой подстроке (tor, zen, edge) могли бы найти своё
    private static MediaSource ofPlayer(String needle) {
        if (needle.contains("telegram") || needle.contains("unigram")) return TELEGRAM;
        if (needle.contains("zunemusic") || needle.contains("microsoft.media.player")
                || needle.contains("music.ui")) return MEDIA_PLAYER;
        if (needle.contains("wmplayer") || needle.contains("mediaplayer32")) return WINDOWS_MEDIA_PLAYER;
        if (needle.contains("winamp")) return WINAMP;
        if (needle.contains("musicbee")) return MUSICBEE;
        if (needle.contains("potplayer")) return POTPLAYER;
        if (needle.contains("mpc-hc") || needle.contains("mpc-be")) return MPC;
        if (needle.contains("itunes")) return ITUNES;
        if (needle.contains("applemusic")) return APPLE_MUSIC;
        return GENERIC;
    }

    public static String nameOf(String app) {
        if (app == null || app.isEmpty()) return "";

        String trimmed = app;
        int slash = Math.max(trimmed.lastIndexOf('\\'), trimmed.lastIndexOf('/'));
        if (slash >= 0) trimmed = trimmed.substring(slash + 1);
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(".exe")) {
            trimmed = trimmed.substring(0, trimmed.length() - 4);
        }
        if (trimmed.isEmpty()) return "";
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }
}
