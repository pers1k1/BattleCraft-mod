package com.persiki84.capturepoints.capture;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CaptureSession {
    public static final int GRACE_TICKS = 60;
    public static final int ARM_TICKS = 100;

    private final CapturePoint point;
    private final CaptureMode mode;
    private final String initiatorTeam;
    private final Map<UUID, Member> members = new HashMap<>();
    private final CaptureLedger ledger = new CaptureLedger();

    private String attackerTeam;
    private UUID soloCaptor;
    private float progress;
    private float arming;
    private boolean running;
    private boolean decaying;
    private boolean held;
    private boolean wipeAnnounced;
    private int attackerCount;
    private int rivalCount;

    public CaptureSession(CapturePoint point, String initiatorTeam, boolean running) {
        this.point = point;
        this.mode = point.getMode();
        this.initiatorTeam = initiatorTeam;
        this.attackerTeam = initiatorTeam;
        this.running = running;
    }

    public CapturePoint getPoint() { return point; }
    public CaptureMode getMode() { return mode; }
    public String getInitiatorTeam() { return initiatorTeam; }
    public String getAttackerTeam() { return attackerTeam; }
    public void setAttackerTeam(String team) { this.attackerTeam = team; }
    public CaptureLedger getLedger() { return ledger; }
    public Map<UUID, Member> getMembers() { return members; }

    public UUID getSoloCaptor() { return soloCaptor; }
    public void setSoloCaptor(UUID captor) { this.soloCaptor = captor; }

    public float getProgress() { return progress; }
    public void setProgress(float value) { this.progress = value; }

    public boolean isRunning() { return running; }
    public void setRunning(boolean value) { this.running = value; }

    public boolean isDecaying() { return decaying; }
    public void setDecaying(boolean value) { this.decaying = value; }

    public boolean isHeld() { return held; }
    public void setHeld(boolean value) { this.held = value; }

    public boolean isWipeAnnounced() { return wipeAnnounced; }
    public void setWipeAnnounced(boolean value) { this.wipeAnnounced = value; }

    public int getAttackerCount() { return attackerCount; }
    public int getRivalCount() { return rivalCount; }

    public void setCounts(int attackers, int rivals) {
        this.attackerCount = attackers;
        this.rivalCount = rivals;
    }

    public void addArming(float ticks) { this.arming = Math.min(ARM_TICKS, this.arming + ticks); }
    public boolean isArmed() { return arming >= ARM_TICKS; }

    public float armingPercent() {
        return arming / ARM_TICKS;
    }

    public int armingSecondsLeft() {
        return (int) Math.ceil((ARM_TICKS - arming) / 20.0f);
    }

    public float progressPercent() {
        int span = Math.max(1, point.getCaptureTime());
        return Math.min(1.0f, progress / span);
    }

    public boolean isComplete() {
        return progress >= point.getCaptureTime();
    }

    public void beginScan() {
        for (Member member : members.values()) {
            member.marked = false;
        }
    }

    public Member mark(UUID playerId, String team) {
        Member member = members.get(playerId);
        if (member == null) {
            member = new Member(team);
            members.put(playerId, member);
        }
        member.team = team;
        member.marked = true;
        member.ticksOutside = 0;
        return member;
    }

    public boolean holds(UUID playerId) {
        return members.containsKey(playerId);
    }

    public boolean anyOutside() {
        for (Member member : members.values()) {
            if (!member.marked) return true;
        }
        return false;
    }

    public String teamOfMember(UUID playerId) {
        Member member = members.get(playerId);
        return member == null ? null : member.team;
    }

    public static final class Member {
        private String team;
        private int ticksOutside;
        private boolean marked;

        private Member(String team) {
            this.team = team;
        }

        public String team() { return team; }
        public boolean marked() { return marked; }
        public int ticksOutside() { return ticksOutside; }
        public void ageOutside() { ticksOutside++; }
        public boolean expired() { return ticksOutside >= GRACE_TICKS; }
        public int secondsLeftOutside() { return (int) Math.ceil((GRACE_TICKS - ticksOutside) / 20.0); }
    }
}
