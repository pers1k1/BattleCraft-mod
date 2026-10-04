package com.persiki84.battlecraft.client.lyrics;

import com.persiki84.battlecraft.BattleCraftMod;

import java.io.IOException;

// WHY: текст Spotify и Spicy Lyrics приходит от расширения Spicetify внутри десктопного Spotify:
// WHY: оно берёт его под сессией самого клиента, поэтому в мод не попадает ни токен, ни кука. Мод
// WHY: только слушает 127.0.0.1 и держит последнюю посылку, а сверка с треком идёт в LyricsService
public final class SpotifyBridge {
    public static final int PORT = 47823;
    private static final long RETRY_MS = 30_000L;

    private static final Object lock = new Object();
    private static volatile BridgeDrop latest;
    private static BridgeServer server;
    private static boolean wanted;
    private static boolean opening;
    private static boolean warned;
    private static long retryAt;

    private SpotifyBridge() {}

    public static void want(boolean enabled) {
        long now = System.nanoTime() / 1_000_000L;
        synchronized (lock) {
            wanted = enabled;
            if (!enabled) {
                close();
                return;
            }
            if (server != null || opening || now < retryAt) return;
            opening = true;
            retryAt = now + RETRY_MS;
        }
        Thread opener = new Thread(SpotifyBridge::open, BattleCraftMod.MOD_ID + "-bridge");
        opener.setDaemon(true);
        opener.start();
    }

    public static void stop() {
        want(false);
    }

    static BridgeDrop latest() {
        return latest;
    }

    // WHY: HttpServer берёт признак демона у потока, который его запускает: старт из потока-демона,
    // WHY: иначе диспетчер сервера держал бы JVM живой после выхода из игры
    private static void open() {
        BridgeServer opened = attempt();
        synchronized (lock) {
            opening = false;
            if (opened != null && wanted) server = opened;
            else if (opened != null) opened.close();
        }
    }

    private static BridgeServer attempt() {
        try {
            BridgeServer opened = BridgeServer.open(PORT, drop -> latest = drop);
            warned = false;
            return opened;
        } catch (IOException | LinkageError error) {
            if (!warned) BattleCraftMod.LOGGER.warn("spotify bridge not started on port {}: {}", PORT, error.toString());
            warned = true;
            return null;
        }
    }

    private static void close() {
        latest = null;
        retryAt = 0L;
        if (server == null) return;
        server.close();
        server = null;
    }
}
