package com.persiki84.battlecraft.rules;

public enum MarkerRange {
    POINTS("points"),
    ZONES("zones"),
    MARKS("marks"),
    PLAYERS("players");

    public static final int DEFAULT_BLOCKS = 1500;
    public static final int MIN_BLOCKS = 16;
    public static final int MAX_BLOCKS = 5000;

    private final String id;

    MarkerRange(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String label() {
        return "battlecraft.markers." + id;
    }

    public String hint() {
        return "battlecraft.markers." + id + ".hint";
    }

    // WHY: ноль это «как у вида», поэтому он живёт рядом с пределами, а не зажимается к минимуму:
    // WHY: иначе снять свою дальность у метки было бы нечем
    public static int normalize(int blocks) {
        if (blocks <= 0) return 0;
        return Math.max(MIN_BLOCKS, Math.min(MAX_BLOCKS, blocks));
    }

    public static MarkerRange byId(String id) {
        for (MarkerRange range : values()) {
            if (range.id.equals(id)) return range;
        }
        return null;
    }
}
