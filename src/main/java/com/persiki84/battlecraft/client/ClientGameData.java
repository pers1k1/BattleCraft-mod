package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.network.S2CSurrenderVotePacket;
import com.persiki84.battlecraft.network.S2CSyncGamePhasePacket;

public class ClientGameData {
    private static BattleCraftManager.GamePhase currentPhase = BattleCraftManager.GamePhase.LOBBY;
    private static boolean hasActiveVote = false;
    private static String voteTeam = "";
    private static int yesCount = 0;
    private static int totalRequired = 0;
    private static int voteTicks = 0;
    private static long voteSyncTime = 0;
    private static boolean softDisabled = false;
    private static int lobbyTimer = 0;
    private static int lobbyMaxTimer = 0;
    private static int graceTicks = 0;
    private static int autoAssignTicks = 0;
    private static int switchCooldownTicks = 0;
    private static int readyCooldownTicks = 0;
    private static int matchTicks = 0;
    private static long lastLobbySyncTime = 0;
    private static String missingPlayers = "";
    private static boolean isReady = false;

    // WHY: состояние принадлежит миру, а класс статический: без сброса фаза, голосование и
    // WHY: таймеры прошлого сервера доезжали на следующий и рисовались поверх чужого матча
    public static void forget() {
        currentPhase = BattleCraftManager.GamePhase.LOBBY;
        hasActiveVote = false;
        voteTeam = "";
        yesCount = 0;
        totalRequired = 0;
        voteTicks = 0;
        voteSyncTime = 0;
        softDisabled = false;
        lobbyTimer = 0;
        lobbyMaxTimer = 0;
        graceTicks = 0;
        autoAssignTicks = 0;
        switchCooldownTicks = 0;
        readyCooldownTicks = 0;
        matchTicks = 0;
        lastLobbySyncTime = 0;
        missingPlayers = "";
        isReady = false;
    }

    public static void updateGamePhase(S2CSyncGamePhasePacket packet) {
        currentPhase = packet.phase;
        softDisabled = packet.softDisabled;
        isReady = packet.isReady;
        lobbyTimer = packet.lobbyTimer;
        lobbyMaxTimer = packet.lobbyMaxTimer;
        graceTicks = packet.graceTicks;
        autoAssignTicks = packet.autoAssignTicks;
        switchCooldownTicks = packet.switchCooldownTicks;
        readyCooldownTicks = packet.readyCooldownTicks;
        matchTicks = packet.matchTicks;
        missingPlayers = packet.missingPlayers;
        lastLobbySyncTime = System.currentTimeMillis();

        if (currentPhase != BattleCraftManager.GamePhase.ACTIVE) {
            com.persiki84.minimap.client.ClientMapData.syncMarkers(new java.util.ArrayList<>());
            com.persiki84.minimap.client.ClientMapData.syncPlayers(new java.util.ArrayList<>());
        }

        DiscordRpcManager.getInstance().updateStatus();
    }

    public static void updateVote(S2CSurrenderVotePacket packet) {
        hasActiveVote = packet.active;
        voteTeam = packet.team;
        yesCount = packet.yesCount;
        totalRequired = packet.required;
        voteTicks = packet.remainingTicks;
        voteSyncTime = System.currentTimeMillis();

        DiscordRpcManager.getInstance().updateStatus();
    }

    public static long getMatchElapsedMillis() {
        if (currentPhase != BattleCraftManager.GamePhase.ACTIVE) return 0;
        return matchTicks * 50L + (System.currentTimeMillis() - lastLobbySyncTime);
    }

    public static boolean isSoftDisabled() {
        return softDisabled;
    }

    public static BattleCraftManager.GamePhase getCurrentPhase() {
        return currentPhase;
    }

    public static boolean hasActiveVote() {
        return hasActiveVote;
    }

    public static String getVoteTeam() {
        return voteTeam;
    }

    public static int getYesCount() {
        return yesCount;
    }

    public static int getTotalRequired() {
        return totalRequired;
    }

    public static int getVoteRemainingSeconds() {
        return seconds(countdown(voteTicks, voteSyncTime));
    }

    public static int getLobbyMaxTimer() {
        return lobbyMaxTimer;
    }

    public static float getInterpolatedLobbyTimer() {
        return countdown(lobbyTimer, lastLobbySyncTime);
    }

    public static int getAutoAssignSeconds() {
        return seconds(countdown(autoAssignTicks, lastLobbySyncTime));
    }

    public static int getSwitchCooldownSeconds() {
        return seconds(countdown(switchCooldownTicks, lastLobbySyncTime));
    }

    public static int getReadyCooldownSeconds() {
        return seconds(countdown(readyCooldownTicks, lastLobbySyncTime));
    }

    public static int getGraceSeconds() {
        return seconds(countdown(graceTicks, lastLobbySyncTime));
    }

    public static String getMissingPlayers() {
        return missingPlayers;
    }

    public static boolean isReady() {
        return isReady;
    }

    private static float countdown(int ticks, long syncedAt) {
        if (ticks <= 0) return 0.0f;
        float elapsed = (System.currentTimeMillis() - syncedAt) / 50.0f;
        return Math.max(0.0f, ticks - elapsed);
    }

    private static int seconds(float ticks) {
        return (int) Math.ceil(ticks / 20.0f);
    }
}
