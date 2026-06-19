package com.persiki84.capturepoints.capture;

import net.minecraft.server.level.ServerPlayer;

public class CaptureProgress {
    private final ServerPlayer player;
    private final CapturePoint point;
    private final String team;
    private int progress;
    private int ticksOutsideRadius;
    private static final int MAX_TICKS_OUTSIDE = 60;

    public CaptureProgress(ServerPlayer player, CapturePoint point, String team) {
        this.player = player;
        this.point = point;
        this.team = team;
        this.progress = 0;
        this.ticksOutsideRadius = 0;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public CapturePoint getPoint() {
        return point;
    }

    public String getTeam() {
        return team;
    }

    public int getProgress() {
        return progress;
    }

    public void incrementProgress() {
        progress++;
    }

    public boolean isComplete() {
        return progress >= point.getCaptureTime();
    }

    public float getProgressPercent() {
        return (float) progress / point.getCaptureTime();
    }

    public void incrementTicksOutside() {
        ticksOutsideRadius++;
    }

    public void resetTicksOutside() {
        ticksOutsideRadius = 0;
    }

    public boolean shouldCancel() {
        return ticksOutsideRadius >= MAX_TICKS_OUTSIDE;
    }

    public int getRemainingTicksOutside() {
        return MAX_TICKS_OUTSIDE - ticksOutsideRadius;
    }
}
