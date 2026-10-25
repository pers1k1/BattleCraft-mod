package com.persiki84.battlecraft.mode;

import java.util.ArrayList;
import java.util.List;

// WHY: настройки режима меняет только ModeBook, а идущий матч держит свою копию: правка пресета
// WHY: посреди матча не сдвигает цель и потолок у тех, кто уже играет
public final class MatchMode {
    private final ModeId id;
    private boolean quarries;
    private int pointScore;
    private int killScore;
    private int vehicleScore;
    private int transportScore;
    private int scoreTarget;
    private int capMinutes;
    private int suddenMargin;
    private int suddenMinutes;
    private long startMoney;
    private final List<KitEntry> kit = new ArrayList<>();

    private MatchMode(ModeId id) {
        this.id = id;
    }

    public static MatchMode defaults(ModeId id) {
        MatchMode mode = new MatchMode(id);
        boolean fast = id == ModeId.FAST;
        mode.quarries(!fast);
        mode.pointScore(1);
        mode.killScore(2);
        mode.vehicleScore(5);
        mode.transportScore(0);
        mode.scoreTarget(fast ? 100 : 200);
        mode.capMinutes(fast ? 35 : 75);
        mode.suddenMargin(5);
        mode.suddenMinutes(fast ? 3 : 5);
        return mode;
    }

    public MatchMode copy() {
        MatchMode copy = new MatchMode(id);
        copy.quarries = quarries;
        copy.pointScore = pointScore;
        copy.killScore = killScore;
        copy.vehicleScore = vehicleScore;
        copy.transportScore = transportScore;
        copy.scoreTarget = scoreTarget;
        copy.capMinutes = capMinutes;
        copy.suddenMargin = suddenMargin;
        copy.suddenMinutes = suddenMinutes;
        copy.startMoney = startMoney;
        copy.kit.addAll(kit);
        return copy;
    }

    public ModeId id() { return id; }
    public boolean quarries() { return quarries; }
    public int pointScore() { return pointScore; }
    public int killScore() { return killScore; }
    public int vehicleScore() { return vehicleScore; }
    public int transportScore() { return transportScore; }
    public int scoreTarget() { return scoreTarget; }
    public int capMinutes() { return capMinutes; }
    public int suddenMargin() { return suddenMargin; }
    public int suddenMinutes() { return suddenMinutes; }
    public long startMoney() { return startMoney; }
    public List<KitEntry> kit() { return kit; }

    public void quarries(boolean value) { quarries = value; }
    public void pointScore(int value) { pointScore = ModeLimits.clamp(value, ModeLimits.SCORE_MAX); }
    public void killScore(int value) { killScore = ModeLimits.clamp(value, ModeLimits.SCORE_MAX); }
    public void vehicleScore(int value) { vehicleScore = ModeLimits.clamp(value, ModeLimits.SCORE_MAX); }
    public void transportScore(int value) { transportScore = ModeLimits.clamp(value, ModeLimits.SCORE_MAX); }
    public void scoreTarget(int value) { scoreTarget = ModeLimits.clamp(value, ModeLimits.TARGET_MAX); }
    public void capMinutes(int value) { capMinutes = ModeLimits.clamp(value, ModeLimits.CAP_MINUTES_MAX); }
    public void suddenMargin(int value) { suddenMargin = ModeLimits.clamp(value, ModeLimits.SUDDEN_MARGIN_MAX); }
    public void suddenMinutes(int value) { suddenMinutes = ModeLimits.clamp(value, ModeLimits.SUDDEN_MINUTES_MAX); }
    public void startMoney(long value) { startMoney = ModeLimits.money(value); }

    // WHY: набор режется по пределу записей при вводе и при чтении файла, а не при выдаче:
    // WHY: иначе файл, правленый руками, раздавал бы больше, чем видно в студии
    public boolean addKit(KitEntry entry) {
        if (kit.size() >= ModeLimits.KIT_ENTRIES_MAX) return false;
        kit.add(entry);
        return true;
    }
}
