package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.network.S2CSyncGamePhasePacket;

public class ClientGameData {
    private static BattleCraftManager.GamePhase currentPhase = BattleCraftManager.GamePhase.LOBBY;
    private static boolean hasActiveVote = false;
    private static String voteTeam = "";
    private static int yesCount = 0;
    private static int totalRequired = 0;
    private static long voteEndTime = 0;
    private static boolean softDisabled = false;
    private static int lobbyTimer = 0;
    private static int lobbyMaxTimer = 0;
    private static long lastLobbySyncTime = 0;
    private static String missingPlayers = "";
    private static boolean isReady = false;

    public static void updateGamePhase(S2CSyncGamePhasePacket packet) {
        currentPhase = packet.phase;
        hasActiveVote = packet.hasActiveVote;
        voteTeam = packet.voteTeam;
        yesCount = packet.yesCount;
        totalRequired = packet.totalRequired;
        voteEndTime = packet.endTime;
        softDisabled = packet.softDisabled;
        lobbyTimer = packet.lobbyTimer;
        lobbyMaxTimer = packet.lobbyMaxTimer;
        lastLobbySyncTime = System.currentTimeMillis();
        missingPlayers = packet.missingPlayers;
        isReady = packet.isReady;

        if (currentPhase != BattleCraftManager.GamePhase.ACTIVE) {
            com.persiki84.minimap.client.ClientMapData.syncMarkers(new java.util.ArrayList<>());
            com.persiki84.minimap.client.ClientMapData.syncPlayers(new java.util.ArrayList<>());
        }

        DiscordRpcManager.getInstance().updateStatus();
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

    public static long getVoteEndTime() {
        return voteEndTime;
    }

    public static int getLobbyTimer() {
        return lobbyTimer;
    }

    public static int getLobbyMaxTimer() {
        return lobbyMaxTimer;
    }

    public static float getInterpolatedLobbyTimer() {
        if (lobbyTimer <= 0) return 0;
        float elapsedTicks = (System.currentTimeMillis() - lastLobbySyncTime) / 50f;
        return Math.max(0, lobbyTimer - elapsedTicks);
    }

    public static String getMissingPlayers() {
        return missingPlayers;
    }

    public static boolean isReady() {
        return isReady;
    }
}
