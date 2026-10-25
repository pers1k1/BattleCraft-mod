package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.mode.MatchScore;
import com.persiki84.battlecraft.mode.ModeId;
import com.persiki84.battlecraft.mode.S2CMatchScorePacket;
import com.persiki84.battlecraft.mode.S2CModeLobbyPacket;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

// WHY: лобби и счёт приходят готовыми с сервера: клиент видит только точки своего измерения и не
// WHY: знает чужих убийств, поэтому ничего здесь не досчитывает, а лишь хранит последний снимок
public final class ClientMatchMode {
    private static final int[] votes = new int[ModeId.values().length];
    private static final Map<String, Integer> scores = new HashMap<>();
    private static ModeId active = ModeId.MAIN;
    @Nullable
    private static ModeId mine;
    private static boolean locked;
    private static int lobbyVersion;

    private static boolean running;
    private static ModeId mode = ModeId.MAIN;
    private static int scoreTarget;
    private static int capMinutes;
    private static long suddenEndMillis = MatchScore.NO_SUDDEN;
    private static int scoreVersion;

    private ClientMatchMode() {}

    public static void acceptLobby(S2CModeLobbyPacket packet) {
        active = modeAt(packet.active, ModeId.MAIN);
        mine = packet.mine == S2CModeLobbyPacket.NONE ? null : modeAt(packet.mine, null);
        locked = packet.locked;
        System.arraycopy(packet.votes, 0, votes, 0, Math.min(votes.length, packet.votes.length));
        lobbyVersion++;
    }

    public static void acceptScore(S2CMatchScorePacket packet) {
        running = packet.running;
        mode = modeAt(packet.mode, ModeId.MAIN);
        scoreTarget = packet.scoreTarget;
        capMinutes = packet.capMinutes;
        suddenEndMillis = packet.suddenEndMillis;
        scores.clear();
        for (S2CMatchScorePacket.Team team : packet.teams) {
            scores.put(team.name(), team.score());
        }
        scoreVersion++;
    }

    public static void forget() {
        Arrays.fill(votes, 0);
        scores.clear();
        active = ModeId.MAIN;
        mine = null;
        locked = false;
        running = false;
        suddenEndMillis = MatchScore.NO_SUDDEN;
        lobbyVersion++;
        scoreVersion++;
    }

    private static ModeId modeAt(int ordinal, ModeId fallback) {
        ModeId[] all = ModeId.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : fallback;
    }

    public static int votes(ModeId id) { return votes[id.ordinal()]; }
    public static ModeId active() { return active; }
    @Nullable
    public static ModeId mine() { return mine; }
    public static boolean locked() { return locked; }
    public static int lobbyVersion() { return lobbyVersion; }

    public static boolean running() { return running; }
    public static ModeId mode() { return mode; }
    public static int scoreTarget() { return scoreTarget; }
    public static int capMinutes() { return capMinutes; }
    public static boolean sudden() { return suddenEndMillis != MatchScore.NO_SUDDEN; }
    public static long suddenEndMillis() { return suddenEndMillis; }
    public static int scoreVersion() { return scoreVersion; }

    public static int scoreOf(@Nullable String team) {
        return team == null ? 0 : scores.getOrDefault(team, 0);
    }

    @Nullable
    public static String bestRivalTeam(@Nullable String own) {
        String best = null;
        int top = -1;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getKey().equals(own) || entry.getValue() <= top) continue;
            top = entry.getValue();
            best = entry.getKey();
        }
        return best;
    }

    // WHY: соперник на полосе счёта - лидер среди остальных команд, а не первая попавшаяся
    public static int bestRival(@Nullable String own) {
        int best = 0;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (!entry.getKey().equals(own)) best = Math.max(best, entry.getValue());
        }
        return best;
    }
}
