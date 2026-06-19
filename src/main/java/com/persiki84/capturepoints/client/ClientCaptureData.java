package com.persiki84.capturepoints.client;

import net.minecraft.client.Minecraft;

import com.persiki84.capturepoints.network.PointSyncData;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClientCaptureData {
    private static final Map<String, String> pointOwners = new HashMap<>();
    private static final Map<String, BlockPos> pointPositions = new HashMap<>();
    private static final Map<String, String> finalPointOwners = new HashMap<>();
    private static final Map<String, BlockPos> finalPointPositions = new HashMap<>();
    private static final Map<String, Float> captureProgress = new HashMap<>();
    private static final Map<String, UUID> capturePlayers = new HashMap<>();
    private static final Map<String, Long> lastUpdateTimes = new HashMap<>();
    private static boolean serverCaptureMarkers = true;
    private static boolean serverFinalMarkers = true;
    private static boolean localMarkersEnabled = true;

    private static String localCapturingPoint = null;

    public static void setServerMarkerConfig(boolean capture, boolean fin) {
        serverCaptureMarkers = capture;
        serverFinalMarkers = fin;
    }

    public static boolean isServerCaptureMarkers() { return serverCaptureMarkers; }
    public static boolean isServerFinalMarkers() { return serverFinalMarkers; }

    public static boolean isLocalMarkersEnabled() { return localMarkersEnabled; }
    public static void setLocalMarkersEnabled(boolean enabled) { localMarkersEnabled = enabled; }

    public static void updateCapture(UUID playerId, String pointName, float progress, boolean active) {
        if (active) {
            captureProgress.put(pointName, progress);
            capturePlayers.put(pointName, playerId);
            lastUpdateTimes.put(pointName, System.currentTimeMillis());
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getUUID().equals(playerId)) {
                localCapturingPoint = pointName;
            }
        } else {
            captureProgress.remove(pointName);
            capturePlayers.remove(pointName);
            lastUpdateTimes.remove(pointName);
            if (pointName.equals(localCapturingPoint)) {
                localCapturingPoint = null;
            }
        }
    }

    public static void completeCapture(String pointName, String teamName) {
        if (finalPointOwners.containsKey(pointName) || capturingFinal(pointName)) {
            finalPointOwners.put(pointName, teamName);
        } else {
            pointOwners.put(pointName, teamName);
        }

        captureProgress.remove(pointName);
        capturePlayers.remove(pointName);
        lastUpdateTimes.remove(pointName);
        if (pointName.equals(localCapturingPoint)) {
            localCapturingPoint = null;
        }
    }
    
    private static boolean capturingFinal(String pointName) {
        return finalPointOwners.containsKey(pointName) || (pointOwners.isEmpty() && areAllPointsCapturedBySameTeam());
    }

    public static void removeFinalPoint(String pointName) {
        finalPointOwners.remove(pointName);
        finalPointPositions.remove(pointName);
        captureProgress.remove(pointName);
        capturePlayers.remove(pointName);
        lastUpdateTimes.remove(pointName);
        if (pointName.equals(localCapturingPoint)) {
            localCapturingPoint = null;
        }
    }

    public static void removePoint(String pointName) {
        pointOwners.remove(pointName);
        pointPositions.remove(pointName);
        finalPointOwners.remove(pointName);
        finalPointPositions.remove(pointName);
        captureProgress.remove(pointName);
        capturePlayers.remove(pointName);
        lastUpdateTimes.remove(pointName);
        if (pointName.equals(localCapturingPoint)) {
            localCapturingPoint = null;
        }
    }

    public static void syncPoints(Map<String, PointSyncData> points) {
        pointOwners.clear();
        pointPositions.clear();
        for (Map.Entry<String, PointSyncData> entry : points.entrySet()) {
            pointOwners.put(entry.getKey(), entry.getValue().owner);
            pointPositions.put(entry.getKey(), entry.getValue().pos);
        }
    }

    public static void syncFinalPoints(Map<String, PointSyncData> points) {
        finalPointOwners.clear();
        finalPointPositions.clear();
        for (Map.Entry<String, PointSyncData> entry : points.entrySet()) {
            finalPointOwners.put(entry.getKey(), entry.getValue().owner);
            finalPointPositions.put(entry.getKey(), entry.getValue().pos);
        }
    }

    public static Map<String, String> getAllPointOwners() {
        return new HashMap<>(pointOwners);
    }

    public static Map<String, String> getAllFinalPointOwners() {
        return new HashMap<>(finalPointOwners);
    }

    public static BlockPos getPointPosition(String pointName) {
        return pointPositions.get(pointName);
    }

    public static BlockPos getFinalPointPosition(String pointName) {
        return finalPointPositions.get(pointName);
    }

    public static boolean isLocalPlayerCapturing() {
        return localCapturingPoint != null;
    }

    public static boolean isLocalCapturingFinal() {
        return localCapturingPoint != null && finalPointOwners.containsKey(localCapturingPoint);
    }

    public static String getLocalCapturingPoint() {
        return localCapturingPoint;
    }
    
    public static float getProgress(String pointName) {
        return captureProgress.getOrDefault(pointName, 0.0f);
    }

    public static long getLastUpdateTime(String pointName) {
        return lastUpdateTimes.getOrDefault(pointName, 0L);
    }

    public static UUID getCapturingPlayer(String pointName) {
        return capturePlayers.get(pointName);
    }

    public static String getPointOwner(String pointName) {
        return pointOwners.get(pointName);
    }

    public static String getFinalPointOwner(String pointName) {
        return finalPointOwners.get(pointName);
    }

    public static boolean areAllPointsCapturedBySameTeam() {
        if (pointOwners.isEmpty()) {
            return false;
        }

        String firstTeam = null;
        for (String owner : pointOwners.values()) {
            if (owner == null) {
                return false;
            }
            if (firstTeam == null) {
                firstTeam = owner;
            } else if (!firstTeam.equals(owner)) {
                return false;
            }
        }

        return true;
    }

    public static Map<String, Integer> getTeamPoints() {
        Map<String, Integer> teamPoints = new HashMap<>();

        for (String owner : pointOwners.values()) {
            if (owner != null) {
                teamPoints.put(owner, teamPoints.getOrDefault(owner, 0) + 1);
            }
        }

        return teamPoints;
    }
}
