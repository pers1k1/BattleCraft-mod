package com.persiki84.zones;

import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ZoneRules {
    private int overridden;
    private int allowed;

    public boolean allows(ZoneRule rule, ZoneType type) {
        return isOverridden(rule) ? overriddenValue(rule) : rule.allowedByDefaultIn(type);
    }

    public boolean isOverridden(ZoneRule rule) {
        return (overridden & (1 << rule.ordinal())) != 0;
    }

    private boolean overriddenValue(ZoneRule rule) {
        return (allowed & (1 << rule.ordinal())) != 0;
    }

    public void override(ZoneRule rule, boolean value) {
        int bit = 1 << rule.ordinal();
        overridden |= bit;
        if (value) {
            allowed |= bit;
        } else {
            allowed &= ~bit;
        }
    }

    public void resetToDefault(ZoneRule rule) {
        int bit = 1 << rule.ordinal();
        overridden &= ~bit;
        allowed &= ~bit;
    }

    public Map<String, Boolean> overrides() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (ZoneRule rule : ZoneRule.values()) {
            if (isOverridden(rule)) map.put(rule.id(), overriddenValue(rule));
        }
        return map;
    }

    public static ZoneRules ofOverrides(Map<String, Boolean> overrides) {
        ZoneRules rules = new ZoneRules();
        if (overrides == null) return rules;

        for (Map.Entry<String, Boolean> entry : overrides.entrySet()) {
            ZoneRule rule = ZoneRule.byId(entry.getKey());
            if (rule != null && entry.getValue() != null) rules.override(rule, entry.getValue());
        }
        return rules;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(overridden);
        buf.writeVarInt(allowed);
    }

    public static ZoneRules read(FriendlyByteBuf buf) {
        ZoneRules rules = new ZoneRules();
        rules.overridden = buf.readVarInt();
        rules.allowed = buf.readVarInt();
        return rules;
    }
}
