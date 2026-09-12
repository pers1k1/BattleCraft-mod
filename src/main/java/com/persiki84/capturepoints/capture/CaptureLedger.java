package com.persiki84.capturepoints.capture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CaptureLedger {
    public static final float KILL_WEIGHT = 60.0f;

    private final Map<UUID, Entry> entries = new HashMap<>();

    // WHY: вклад принадлежит команде, за которую он заработан: раньше запись просто меняла
    // WHY: команду, и всё накопленное за прежнюю уезжало в новую вместе с долей награды
    public void credit(UUID playerId, String team, float weight) {
        Entry entry = entries.get(playerId);
        if (entry == null || !entry.team.equals(team)) {
            entry = new Entry(playerId, team);
            entries.put(playerId, entry);
        }
        entry.weight += weight;
    }

    public void creditKill(UUID playerId, String team) {
        credit(playerId, team, KILL_WEIGHT);
    }

    public List<Entry> shareOf(String team) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.team.equals(team) && entry.weight > 0.0f) result.add(entry);
        }
        result.sort((left, right) -> Float.compare(right.weight, left.weight));
        return result;
    }

    public static final class Entry {
        private final UUID playerId;
        private String team;
        private float weight;

        private Entry(UUID playerId, String team) {
            this.playerId = playerId;
            this.team = team;
        }

        public UUID playerId() { return playerId; }
        public float weight() { return weight; }
    }
}
