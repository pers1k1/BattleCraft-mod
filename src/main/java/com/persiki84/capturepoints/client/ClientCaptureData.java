package com.persiki84.capturepoints.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import com.persiki84.capturepoints.capture.CaptureMode;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.knockdown.cap.KnockdownCapability;
import com.persiki84.knockdown.cap.KnockdownProvider;
import com.persiki84.capturepoints.network.PointSyncData;
import com.persiki84.shared.zone.ZoneArea;
import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class ClientCaptureData {
    private static final Map<String, String> pointOwners = new HashMap<>();
    private static final Map<String, BlockPos> pointPositions = new HashMap<>();
    private static final Map<String, String> finalPointOwners = new HashMap<>();
    private static final Map<String, BlockPos> finalPointPositions = new HashMap<>();
    private static final Map<String, ZoneArea> pointAreas = new HashMap<>();
    private static final Map<String, ZoneArea> finalPointAreas = new HashMap<>();
    private static final Map<String, CaptureMode> pointModes = new HashMap<>();
    private static final Map<String, Integer> pointRanges = new HashMap<>();
    private static final Set<String> optionalPoints = new HashSet<>();
    private static final Set<String> hiddenFromHud = new HashSet<>();
    private static final Set<String> shownInside = new HashSet<>();
    private static final Map<String, Session> sessions = new HashMap<>();
    private static final Map<String, String> pointOwnersView = Collections.unmodifiableMap(pointOwners);
    private static final Map<String, String> finalPointOwnersView = Collections.unmodifiableMap(finalPointOwners);
    private static boolean serverCaptureMarkers = true;
    private static boolean serverFinalMarkers = true;
    private static boolean localMarkersEnabled = true;

    private static String localCapturingPoint = null;
    private static String localAttackerTeam = null;
    private static boolean localMember;

    public static void setServerMarkerConfig(boolean capture, boolean fin) {
        serverCaptureMarkers = capture;
        serverFinalMarkers = fin;
    }

    public static boolean isServerCaptureMarkers() { return serverCaptureMarkers; }
    public static boolean isServerFinalMarkers() { return serverFinalMarkers; }

    public static boolean isLocalMarkersEnabled() { return localMarkersEnabled; }
    public static void setLocalMarkersEnabled(boolean enabled) { localMarkersEnabled = enabled; }

    public static void updateCapture(String pointName, boolean active, boolean running, float progress,
                                     boolean decaying, boolean held, int armingSeconds, String attackerTeam,
                                     int attackerCount, int rivalCount, List<UUID> members) {
        if (!active) {
            forgetProgress(pointName);
            return;
        }

        Session session = sessions.computeIfAbsent(pointName, name -> new Session());
        session.remember(progress);
        session.running = running;
        session.decaying = decaying;
        session.held = held;
        session.armingSeconds = armingSeconds;
        session.attackerTeam = attackerTeam;
        session.attackerCount = attackerCount;
        session.rivalCount = rivalCount;
        session.members.clear();
        session.members.addAll(members);
        trackLocal(pointName, session);
    }

    private static void trackLocal(String pointName, Session session) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        if (session.members.contains(minecraft.player.getUUID())) {
            localCapturingPoint = pointName;
            localAttackerTeam = session.attackerTeam;
            localMember = true;
            return;
        }
        if (!pointName.equals(localCapturingPoint)) return;

        localMember = false;
        if (!Objects.equals(session.attackerTeam, localAttackerTeam)) {
            localCapturingPoint = null;
        }
    }

    private static void forgetProgress(String pointName) {
        sessions.remove(pointName);
        if (pointName.equals(localCapturingPoint)) {
            localCapturingPoint = null;
            localMember = false;
        }
    }

    public static void forget() {
        pointOwners.clear();
        pointPositions.clear();
        pointAreas.clear();
        finalPointOwners.clear();
        finalPointPositions.clear();
        finalPointAreas.clear();
        pointModes.clear();
        pointRanges.clear();
        optionalPoints.clear();
        hiddenFromHud.clear();
        shownInside.clear();
        sessions.clear();
        localCapturingPoint = null;
        localAttackerTeam = null;
        localMember = false;
    }

    public static boolean isLocalCaptureShown() {
        if (localCapturingPoint == null) return false;
        if (!downed()) return true;

        localCapturingPoint = null;
        localMember = false;
        return false;
    }

    private static boolean downed() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive()) return true;

        KnockdownCapability knocked = player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).orElse(null);
        return knocked != null && knocked.isKnocked();
    }

    public static void completeCapture(String pointName, String teamName) {
        if (finalPointOwners.containsKey(pointName) || capturingFinal(pointName)) {
            finalPointOwners.put(pointName, teamName);
        } else {
            pointOwners.put(pointName, teamName);
        }
        forgetProgress(pointName);
    }

    private static boolean capturingFinal(String pointName) {
        return finalPointOwners.containsKey(pointName) || (pointOwners.isEmpty() && areAllPointsCapturedBySameTeam());
    }

    public static void removeFinalPoint(String pointName) {
        finalPointOwners.remove(pointName);
        finalPointPositions.remove(pointName);
        forgetPoint(pointName);
    }

    public static void removePoint(String pointName) {
        pointOwners.remove(pointName);
        pointPositions.remove(pointName);
        pointAreas.remove(pointName);
        finalPointOwners.remove(pointName);
        finalPointPositions.remove(pointName);
        finalPointAreas.remove(pointName);
        forgetPoint(pointName);
    }

    private static void forgetPoint(String pointName) {
        pointModes.remove(pointName);
        pointRanges.remove(pointName);
        optionalPoints.remove(pointName);
        hiddenFromHud.remove(pointName);
        shownInside.remove(pointName);
        forgetProgress(pointName);
    }

    public static void syncPoints(Map<String, PointSyncData> points) {
        pointOwners.clear();
        pointPositions.clear();
        pointAreas.clear();
        for (Map.Entry<String, PointSyncData> entry : points.entrySet()) {
            if (!inCurrentDimension(entry.getValue())) continue;

            pointOwners.put(entry.getKey(), entry.getValue().owner);
            pointPositions.put(entry.getKey(), entry.getValue().pos());
            pointAreas.put(entry.getKey(), entry.getValue().area);
            rememberFlags(entry.getKey(), entry.getValue());
        }
    }

    public static void syncFinalPoints(Map<String, PointSyncData> points) {
        finalPointOwners.clear();
        finalPointPositions.clear();
        finalPointAreas.clear();
        for (Map.Entry<String, PointSyncData> entry : points.entrySet()) {
            if (!inCurrentDimension(entry.getValue())) continue;

            finalPointOwners.put(entry.getKey(), entry.getValue().owner);
            finalPointPositions.put(entry.getKey(), entry.getValue().pos());
            finalPointAreas.put(entry.getKey(), entry.getValue().area);
            rememberFlags(entry.getKey(), entry.getValue());
        }
    }

    private static void rememberFlags(String name, PointSyncData data) {
        pointModes.put(name, data.mode);
        pointRanges.put(name, data.markerRange);
        if (data.required) {
            optionalPoints.remove(name);
        } else {
            optionalPoints.add(name);
        }
        if (data.shownInHud) {
            hiddenFromHud.remove(name);
        } else {
            hiddenFromHud.add(name);
        }
        if (data.hiddenInside) {
            shownInside.remove(name);
        } else {
            shownInside.add(name);
        }
    }

    public static int getMarkerRange(String pointName) {
        return pointRanges.getOrDefault(pointName, CapturePoint.KIND_RANGE);
    }

    public static boolean isPointRequired(String pointName) {
        return !optionalPoints.contains(pointName);
    }

    // WHY: невидимая в HUD точка остаётся на карте и берётся как обычно: прячется только полоса
    // WHY: целей и метка над местностью, потому что иначе её нечем было бы найти
    public static boolean isPointShownInHud(String pointName) {
        return !hiddenFromHud.contains(pointName);
    }

    public static boolean isPointHiddenInside(String pointName) {
        return !shownInside.contains(pointName);
    }

    private static boolean inCurrentDimension(PointSyncData data) {
        if (data.dimension == null) return true;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return true;
        return data.dimension.equals(minecraft.level.dimension().location());
    }

    public static ZoneArea getPointArea(String pointName) {
        return pointAreas.get(pointName);
    }

    public static ZoneArea getFinalPointArea(String pointName) {
        return finalPointAreas.get(pointName);
    }

    public static CaptureMode getPointMode(String pointName) {
        return pointModes.getOrDefault(pointName, CaptureMode.DEFAULT);
    }

    public static Map<String, String> getAllPointOwners() {
        return pointOwnersView;
    }

    public static Map<String, String> getAllFinalPointOwners() {
        return finalPointOwnersView;
    }

    public static BlockPos getPointPosition(String pointName) {
        return pointPositions.get(pointName);
    }

    public static BlockPos getFinalPointPosition(String pointName) {
        return finalPointPositions.get(pointName);
    }

    public static boolean isLocalPlayerCapturing() {
        return localCapturingPoint != null && localMember;
    }

    public static boolean isLocalCapturingFinal() {
        return localCapturingPoint != null && finalPointOwners.containsKey(localCapturingPoint);
    }

    public static String getLocalCapturingPoint() {
        return localCapturingPoint;
    }

    public static boolean isRunning(String pointName) {
        Session session = sessions.get(pointName);
        return session != null && session.running;
    }

    public static boolean isDecaying(String pointName) {
        Session session = sessions.get(pointName);
        return session != null && session.decaying;
    }

    public static boolean isHeld(String pointName) {
        Session session = sessions.get(pointName);
        return session != null && session.held;
    }

    public static int getArmingSeconds(String pointName) {
        Session session = sessions.get(pointName);
        return session == null ? 0 : session.armingSeconds;
    }

    public static String getAttackerTeam(String pointName) {
        Session session = sessions.get(pointName);
        return session == null ? null : session.attackerTeam;
    }

    public static int getAttackerCount(String pointName) {
        Session session = sessions.get(pointName);
        return session == null ? 0 : session.attackerCount;
    }

    public static int getRivalCount(String pointName) {
        Session session = sessions.get(pointName);
        return session == null ? 0 : session.rivalCount;
    }

    public static float getProgress(String pointName) {
        Session session = sessions.get(pointName);
        if (session == null || !session.running) return 0.0f;
        return Math.min(1.0f, session.shown());
    }

    public static float getBarValue(String pointName) {
        Session session = sessions.get(pointName);
        return session == null ? 0.0f : Math.min(1.0f, session.shown());
    }

    public static long getLastUpdateTime(String pointName) {
        Session session = sessions.get(pointName);
        return session == null ? 0L : session.updatedAt;
    }

    // WHY: то же правило, что на сервере в CapturePointManager: финальную открывают обязательные
    // WHY: точки, а когда обязательных нет вовсе - открывать нечего, и она доступна сразу
    public static boolean areAllPointsCapturedBySameTeam() {
        String firstTeam = null;
        boolean anyRequired = false;
        for (Map.Entry<String, String> point : pointOwners.entrySet()) {
            if (!isPointRequired(point.getKey())) continue;

            anyRequired = true;
            String owner = point.getValue();
            if (owner == null) {
                return false;
            }
            if (firstTeam == null) {
                firstTeam = owner;
            } else if (!firstTeam.equals(owner)) {
                return false;
            }
        }

        return !anyRequired || firstTeam != null;
    }

    private static final class Session {
        private final Set<UUID> members = new HashSet<>();
        private String attackerTeam;
        private boolean running;
        private boolean decaying;
        private boolean held;
        private int armingSeconds;
        private int attackerCount;
        private int rivalCount;
        private float progress;
        private float previous;
        private long updatedAt;
        private long previousAt;

        private void remember(float reported) {
            previous = progress;
            previousAt = updatedAt;
            progress = reported;
            updatedAt = System.currentTimeMillis();
        }

        private float shown() {
            long span = updatedAt - previousAt;
            float step = progress - previous;
            if (span <= 0L || previousAt == 0L) return progress;

            long elapsed = Math.min(System.currentTimeMillis() - updatedAt, span);
            return Math.max(0.0f, progress + step * elapsed / span);
        }
    }
}
