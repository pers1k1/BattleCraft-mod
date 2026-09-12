package com.persiki84.capturepoints.capture;

import java.util.Locale;

public enum CaptureMode {
    CLASSIC(Trigger.KEY, Crew.SOLO, false),
    AUTO(Trigger.PRESENCE, Crew.SOLO, false),
    RELAY(Trigger.KEY, Crew.SOLO, true),
    SQUAD(Trigger.KEY, Crew.TEAM, false),
    SQUAD_AUTO(Trigger.PRESENCE, Crew.TEAM, false),
    CONTEST(Trigger.KEY, Crew.EVERYONE, false),
    CONTEST_AUTO(Trigger.PRESENCE, Crew.EVERYONE, false);

    public static final CaptureMode DEFAULT = CLASSIC;

    public enum Trigger { KEY, PRESENCE }

    public enum Crew { SOLO, TEAM, EVERYONE }

    private final Trigger trigger;
    private final Crew crew;
    private final boolean relay;

    CaptureMode(Trigger trigger, Crew crew, boolean relay) {
        this.trigger = trigger;
        this.crew = crew;
        this.relay = relay;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "capturepoints.mode." + id();
    }

    public boolean startsByKey() {
        return trigger == Trigger.KEY;
    }

    public boolean startsByPresence() {
        return trigger == Trigger.PRESENCE;
    }

    public boolean solo() {
        return crew == Crew.SOLO;
    }

    public boolean teamWide() {
        return crew == Crew.TEAM;
    }

    public boolean contested() {
        return crew == Crew.EVERYONE;
    }

    public boolean relays() {
        return relay;
    }

    public static CaptureMode byId(String id) {
        if (id == null || id.isEmpty()) return DEFAULT;

        for (CaptureMode mode : values()) {
            if (mode.id().equalsIgnoreCase(id)) return mode;
        }
        return DEFAULT;
    }
}
