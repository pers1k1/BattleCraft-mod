package com.persiki84.battlecraft.client.island;

import com.persiki84.battlecraft.client.media.MediaBridge;
import com.persiki84.battlecraft.client.media.MediaTrack;
import com.persiki84.battlecraft.client.media.MediaWatch;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Spring;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

public final class IslandModel {
    private static final long CARD_MS = 5000L;
    private static final long PAUSE_HOLD_MS = 3000L;
    private static final long STALL_HOLD_MS = 10000L;
    private static final float SHOW_RESPONSE = 0.42f;
    private static final float SHOW_DAMPING = 0.86f;
    private static final float EXPAND_RESPONSE = 0.36f;
    private static final float EXPAND_DAMPING = 0.78f;
    private static final float BLIND_RESPONSE = 0.34f;
    private static final float BLIND_DAMPING = 0.9f;
    private static final float ENERGY_SPEED = 4.5f;
    private static final int PING_CEILING = 300;

    private static final Spring shown = new Spring(SHOW_RESPONSE, SHOW_DAMPING, 0.0f);
    private static final Spring opened = new Spring(EXPAND_RESPONSE, EXPAND_DAMPING, 0.0f);
    private static final Spring blinded = new Spring(BLIND_RESPONSE, BLIND_DAMPING, 0.0f);
    private static final Smooth energy = new Smooth(0.0f, ENERGY_SPEED);


    private static MediaTrack track = MediaTrack.NONE;
    private static long playingAt;
    private static long cardUntil;
    private static boolean dormant = true;
    private static int frames = -1;
    private static int latency = -1;
    private static Component framesLabel = Component.empty();
    private static Component latencyLabel = Component.empty();
    private static Component nick = Component.empty();
    private static String nickRaw = "";

    private IslandModel() {}

    public static void pollStats() {
        Minecraft mc = Minecraft.getInstance();
        int shownFrames = mc.getFps();
        if (shownFrames != frames) {
            frames = shownFrames;
            framesLabel = Component.literal(String.valueOf(frames));
        }

        int shownLatency = ping(mc);
        if (shownLatency != latency) {
            latency = shownLatency;
            latencyLabel = Component.literal(String.valueOf(latency));
        }
        rememberNick(mc);
    }

    private static void rememberNick(Minecraft mc) {
        String name = mc.getUser() == null ? "" : mc.getUser().getName();
        if (name.equals(nickRaw)) return;

        nickRaw = name;
        nick = Component.literal(name);
    }

    private static int ping(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return 0;

        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : Math.max(0, info.getLatency());
    }

    public static void advance(float delta) {
        MediaTrack fresh = MediaWatch.current();
        long now = System.currentTimeMillis();
        if (fresh.present() && fresh.playing()) playingAt = now;

        boolean live = fresh.present() && (fresh.playing() || now - playingAt < holdFor());
        retarget(fresh, live, now);

        if (fresh.present() && !fresh.blind()) IslandArt.accept(fresh.artStamp(), MediaBridge.artFile());
        IslandProgress.advance(track, delta);
        IslandFlip.advance(delta);
        IslandTone.advance(delta);
        shown.to(live ? 1.0f : 0.0f, delta);
        opened.to(live && !fresh.blind() && now < cardUntil ? 1.0f : 0.0f, delta);
        blinded.to(live && fresh.blind() ? 1.0f : 0.0f, delta);
        energy.to(fresh.playing() ? 1.0f : 0.0f, delta);
    }

    private static long holdFor() {
        return MediaWatch.stalled() ? STALL_HOLD_MS : PAUSE_HOLD_MS;
    }

    private static void retarget(MediaTrack fresh, boolean live, long now) {
        if (!live) {
            cardUntil = 0L;
            dormant = true;
            return;
        }

        if (dormant || !fresh.sameTrack(track)) {
            if (!dormant) IslandOrder.note(track, track.elapsedMs(now), fresh);
            cardUntil = now + CARD_MS;
        }
        dormant = false;
        track = fresh;
    }

    public static MediaTrack track() {
        return track;
    }

    public static float media() {
        return clamp(shown.get());
    }

    public static float expand() {
        return clamp(opened.get());
    }

    public static float blind() {
        return clamp(blinded.get());
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public static float energy() {
        return energy.get();
    }

    public static Component frames() {
        return framesLabel;
    }

    public static Component latency() {
        return latencyLabel;
    }

    public static Component nick() {
        return nick;
    }

    public static float quality() {
        if (latency <= 0) return 1.0f;
        return Math.max(0.0f, 1.0f - latency / (float) PING_CEILING);
    }

    public static void forget() {
        track = MediaTrack.NONE;
        dormant = true;
        cardUntil = 0L;
        playingAt = 0L;
        shown.snap(0.0f);
        opened.snap(0.0f);
        blinded.snap(0.0f);
        IslandProgress.forget();
        IslandFlip.forget();
        IslandOrder.forget();
        IslandArt.forget();
    }
}
