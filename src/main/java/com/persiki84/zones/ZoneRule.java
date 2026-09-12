package com.persiki84.zones;

import java.util.Locale;

public enum ZoneRule {
    INVULNERABLE(ZoneType.BASE),
    PVP(ZoneType.SHOP, ZoneType.CAPTURE_POINT),
    BLOCK_BREAK(ZoneType.values()),
    BLOCK_PLACE(ZoneType.values()),
    OWNER_BLOCK_BREAK(),
    OWNER_BLOCK_PLACE(),
    EXPLOSIONS(ZoneType.values()),
    MOB_SPAWN(ZoneType.values()),
    INTERACT(ZoneType.values()),
    ITEM_DROP(ZoneType.values()),
    HUNGER(ZoneType.values());

    private final int allowedByDefault;

    ZoneRule(ZoneType... typesAllowingIt) {
        int mask = 0;
        for (ZoneType type : typesAllowingIt) {
            mask |= 1 << type.ordinal();
        }
        this.allowedByDefault = mask;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean allowedByDefaultIn(ZoneType type) {
        return (allowedByDefault & (1 << type.ordinal())) != 0;
    }

    public boolean scopedToOwnerTeam() {
        return this == INVULNERABLE || this == PVP;
    }

    public ZoneRule ownerExemption() {
        if (this == BLOCK_BREAK) return OWNER_BLOCK_BREAK;
        if (this == BLOCK_PLACE) return OWNER_BLOCK_PLACE;
        return null;
    }

    public static ZoneRule byId(String id) {
        if (id == null) return null;

        for (ZoneRule rule : values()) {
            if (rule.id().equalsIgnoreCase(id)) return rule;
        }
        return null;
    }
}
