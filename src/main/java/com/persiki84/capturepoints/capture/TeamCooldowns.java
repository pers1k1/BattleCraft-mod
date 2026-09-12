package com.persiki84.capturepoints.capture;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class TeamCooldowns {
    private static final String ANY_POINT = "*";
    private static final String SEPARATOR = "\u001f";
    private static final Map<String, Long> expiry = new HashMap<>();

    private TeamCooldowns() {}

    public static void charge(String team, CapturePoint point) {
        if (team == null || point.getTeamCooldown() <= 0) return;

        String scopeKey = point.getTeamCooldownScope() == CooldownScope.ALL ? ANY_POINT : point.getName();
        long until = System.currentTimeMillis() + point.getTeamCooldown() * 1000L;
        expiry.merge(key(team, scopeKey), until, Math::max);
    }

    public static boolean blocked(String team, String pointName) {
        return remainingSeconds(team, pointName) > 0L;
    }

    public static long remainingSeconds(String team, String pointName) {
        if (team == null) return 0L;

        long now = System.currentTimeMillis();
        long left = Math.max(remaining(key(team, pointName), now), remaining(key(team, ANY_POINT), now));
        return left <= 0L ? 0L : (left + 999L) / 1000L;
    }

    private static long remaining(String key, long now) {
        Long until = expiry.get(key);
        return until == null ? 0L : until - now;
    }

    public static void forgetPoint(String pointName) {
        String suffix = SEPARATOR + pointName;
        expiry.keySet().removeIf(key -> key.endsWith(suffix));
    }

    public static void clear() {
        expiry.clear();
    }

    public static void save(CompoundTag tag) {
        prune();
        ListTag list = new ListTag();
        for (Map.Entry<String, Long> entry : expiry.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("key", entry.getKey());
            row.putLong("until", entry.getValue());
            list.add(row);
        }
        tag.put("teamCooldowns", list);
    }

    public static void load(CompoundTag tag) {
        expiry.clear();
        ListTag list = tag.getList("teamCooldowns", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag row = list.getCompound(index);
            expiry.put(row.getString("key"), row.getLong("until"));
        }
        prune();
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> rows = expiry.entrySet().iterator();
        while (rows.hasNext()) {
            if (rows.next().getValue() <= now) rows.remove();
        }
    }

    // WHY: ключ склеивался пробелом, а имена команд и точек его допускают: точка «Red P1» и
    // WHY: команда «Red» с точкой «P1» давали один ключ, и снятие кулдауна било по чужому
    private static String key(String team, String pointName) {
        return team + SEPARATOR + pointName;
    }
}
