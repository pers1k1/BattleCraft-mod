package com.persiki84.battlecraft.client.custom;

public enum HudSlot {
    ISLAND("island", HudAnchor.TOP_LEFT, 8.0f, 0.0f, true, 140.0f, 17.0f),
    HOTBAR("hotbar", HudAnchor.BOTTOM_CENTER, 4.0f, 0.0f, false, 228.0f, 28.0f),
    ITEM_NAME("itemName", HudAnchor.BOTTOM_CENTER, 0.0f, 0.0f, false, 96.0f, 19.0f),
    STATUS("status", HudAnchor.BOTTOM_RIGHT, 8.0f, 0.0f, true, 128.0f, 65.5f),
    AMMO("ammo", HudAnchor.BOTTOM_RIGHT, 8.0f, 0.0f, true, 84.0f, 30.0f),
    VOICE("voice", HudAnchor.BOTTOM_RIGHT, 8.0f, 0.0f, true, 15.0f, 15.0f),
    CURRENCY("currency", HudAnchor.BOTTOM_RIGHT, 8.0f, 0.0f, true, 84.0f, 22.0f),
    GOGGLES("goggles", HudAnchor.BOTTOM_RIGHT, 8.0f, 0.0f, true, 84.0f, 26.0f),
    MINIMAP("minimap", HudAnchor.TOP_RIGHT, 8.0f, 0.0f, false, 100.0f, 100.0f),
    EFFECTS("effects", HudAnchor.TOP_RIGHT, 8.0f, 0.0f, false, 68.0f, 20.0f),
    TOASTS("toasts", HudAnchor.TOP_LEFT, 8.0f, 0.0f, true, 162.0f, 30.0f),
    OBJECTIVE("objective", HudAnchor.TOP_CENTER, 8.0f, 0.0f, true, 240.0f, 46.0f),
    CAPTURE("capture", HudAnchor.CENTER, 0.0f, 40.0f, false, 110.0f, 34.0f),
    KNOCKDOWN("knockdown", HudAnchor.CENTER, 0.0f, 34.0f, false, 133.0f, 27.5f),
    ZONE_PROMPT("zonePrompt", HudAnchor.CENTER, 0.0f, 26.0f, false, 112.0f, 14.5f),
    MESSAGES("messages", HudAnchor.BOTTOM_CENTER, 46.0f, 0.0f, false, 150.0f, 20.0f),
    LOBBY("lobby", HudAnchor.TOP_CENTER, 8.0f, 32.0f, false, 150.0f, 34.0f),
    WARNING("warning", HudAnchor.TOP_CENTER, 8.0f, 6.0f, false, 220.0f, 20.0f),
    SCAN("scan", HudAnchor.TOP_LEFT, 8.0f, 0.0f, true, 176.0f, 44.0f),
    VOTE("vote", HudAnchor.MIDDLE_RIGHT, 12.0f, 0.0f, false, 180.0f, 54.0f);

    private final String id;
    private final HudAnchor fallback;
    private final float margin;
    private final float baseOffsetY;
    private final boolean stacked;
    private final float outlineWidth;
    private final float outlineHeight;
    private HudDock leaning;

    HudSlot(String id, HudAnchor fallback, float margin, float baseOffsetY, boolean stacked,
            float outlineWidth, float outlineHeight) {
        this.id = id;
        this.fallback = fallback;
        this.margin = margin;
        this.baseOffsetY = baseOffsetY;
        this.stacked = stacked;
        this.outlineWidth = outlineWidth;
        this.outlineHeight = outlineHeight;
    }

    static {
        ITEM_NAME.leaning = new HudDock(HOTBAR, HudSide.TOP, HudAlign.FREE);
        MESSAGES.leaning = new HudDock(HOTBAR, HudSide.TOP, HudAlign.FREE);
        EFFECTS.leaning = new HudDock(MINIMAP, HudSide.BOTTOM, HudAlign.FREE);
    }

    public HudDock leaning() {
        return leaning;
    }

    public float outlineWidth() {
        return outlineWidth;
    }

    public float outlineHeight() {
        return outlineHeight;
    }

    public String id() {
        return id;
    }

    public HudAnchor fallback() {
        return fallback;
    }

    public float margin() {
        return margin;
    }

    public float baseOffsetY() {
        return baseOffsetY;
    }

    public boolean stacked() {
        return stacked;
    }

    public String translationKey() {
        return "battlecraft.custom.slot." + id;
    }

    public static HudSlot byId(String id) {
        if (id == null) return null;
        for (HudSlot slot : values()) {
            if (slot.id.equalsIgnoreCase(id.trim())) return slot;
        }
        return null;
    }
}
