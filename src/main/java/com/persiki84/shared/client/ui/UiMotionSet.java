package com.persiki84.shared.client.ui;

public enum UiMotionSet {
    IGNITE("ignite", 0.85f, 2.30f, 0.55f, 1.5f),
    GLASS("glass", 0.47f, 1.10f, 0.50f, 0.62f);

    private final String id;
    private final float enterSeconds;
    private final float titleSeconds;
    private final float menuSeconds;
    private final float worldSeconds;

    UiMotionSet(String id, float enterSeconds, float titleSeconds, float menuSeconds, float worldSeconds) {
        this.id = id;
        this.enterSeconds = enterSeconds;
        this.titleSeconds = titleSeconds;
        this.menuSeconds = menuSeconds;
        this.worldSeconds = worldSeconds;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "battlecraft.motion." + id;
    }

    public float enterSeconds() {
        return enterSeconds;
    }

    // WHY: титульный экран занимает кадр целиком, и вход там читается дольше, чем у панели:
    // WHY: у света это каскад приветствия, у резкости - наводка на весь экран, а не на окно
    public float titleSeconds() {
        return titleSeconds;
    }

    public float enterMode() {
        return this == GLASS ? UiReveal.FOCUS : UiReveal.ENTER;
    }

    public float leaveSeconds(boolean inWorld) {
        return inWorld ? worldSeconds : menuSeconds;
    }

    public static UiMotionSet byId(String id) {
        for (UiMotionSet set : values()) {
            if (set.id.equals(id)) return set;
        }
        return IGNITE;
    }
}
