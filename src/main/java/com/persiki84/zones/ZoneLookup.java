package com.persiki84.zones;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;

public final class ZoneLookup {

    private ZoneLookup() {}

    public static Zone smallestOfTypeAt(ZoneType type, double x, double y, double z) {
        Zone smallest = null;
        for (Zone zone : ZoneRegistry.all()) {
            if (zone.type() != type) continue;
            if (!zone.area().contains(x, y, z)) continue;
            if (smallest == null || zone.area().footprint() < smallest.area().footprint()) {
                smallest = zone;
            }
        }
        return smallest;
    }

    public static Zone forbidding(ZoneRule rule, double x, double y, double z) {
        if (!ZoneRegistry.anyZoneForbids(rule)) return null;

        for (Zone zone : ZoneRegistry.all()) {
            if (zone.allows(rule)) continue;
            if (zone.area().contains(x, y, z)) return zone;
        }
        return null;
    }

    public static boolean forbids(ZoneRule rule, double x, double y, double z) {
        return forbidding(rule, x, y, z) != null;
    }

    public static Zone barring(ZoneRule rule, Entity actor, double x, double y, double z) {
        if (!ZoneRegistry.anyZoneForbids(rule)) return null;

        ZoneRule exemption = rule.ownerExemption();
        for (Zone zone : ZoneRegistry.all()) {
            if (zone.allows(rule)) continue;
            if (!zone.area().contains(x, y, z)) continue;
            if (exemptsOwner(zone, exemption, actor)) continue;
            return zone;
        }
        return null;
    }

    private static boolean exemptsOwner(Zone zone, ZoneRule exemption, Entity actor) {
        if (exemption == null || actor == null || zone.ownerTeam() == null) return false;
        if (!zone.allows(exemption)) return false;
        return zone.belongsTo(teamNameOf(actor));
    }

    public static Zone grantingTo(ZoneRule rule, Entity entity) {
        return zoneCovering(rule, entity, true);
    }

    public static Zone denyingTo(ZoneRule rule, Entity entity) {
        return zoneCovering(rule, entity, false);
    }

    private static Zone zoneCovering(ZoneRule rule, Entity entity, boolean allowed) {
        if (!allowed && !ZoneRegistry.anyZoneForbids(rule)) return null;

        for (Zone zone : ZoneRegistry.all()) {
            if (zone.allows(rule) != allowed) continue;
            if (!zone.area().contains(entity.getX(), entity.getY(), entity.getZ())) continue;
            if (coversTeamOf(zone, rule, entity)) return zone;
        }
        return null;
    }

    private static boolean coversTeamOf(Zone zone, ZoneRule rule, Entity entity) {
        if (!rule.scopedToOwnerTeam() || zone.ownerTeam() == null) return true;
        return zone.belongsTo(teamNameOf(entity));
    }

    public static String teamNameOf(Entity entity) {
        PlayerTeam team = entity.level().getScoreboard().getPlayersTeam(entity.getScoreboardName());
        return team == null ? null : team.getName();
    }
}
