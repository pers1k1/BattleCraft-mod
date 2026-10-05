package com.persiki84.minimap.client;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiFrame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

// WHY: вещь на карте не встаёт и не пропадает кадром: новая вырастает из точки, удалённая гаснет
// WHY: и сжимается, перенесённая командой «ко мне» или чужой правкой доезжает до нового места
final class MapMotion {
    private static final float PRESENCE_SPEED = 9.0f;
    private static final float GLIDE_SPEED = 12.0f;
    private static final float LIFT_SPEED = 14.0f;
    private static final float GONE = 0.01f;

    private final Map<String, Track> tracks = new HashMap<>();

    record Drawn(MapThing thing, double x, double z, float presence, float lift) {}

    private static final class Track {
        private final Smooth presence = new Smooth(0.0f, PRESENCE_SPEED);
        private final Smooth lift = new Smooth(0.0f, LIFT_SPEED);
        private MapThing last;
        private double x;
        private double z;
    }

    void reset() {
        tracks.clear();
    }

    List<Drawn> advance(List<MapThing> current, String heldKey, int heldX, int heldZ) {
        float delta = UiFrame.delta();
        float share = 1.0f - (float) Math.exp(-GLIDE_SPEED * delta);
        Set<String> seen = new HashSet<>();
        List<Drawn> drawn = new ArrayList<>();
        for (MapThing thing : current) {
            if (!thing.here()) continue;
            seen.add(thing.key());
            boolean held = thing.key().equals(heldKey);
            MapThing target = held ? thing.at(heldX, heldZ) : thing;
            Track track = tracks.computeIfAbsent(thing.key(), unused -> born(target));
            follow(track, target, held ? 1.0f : share);
            drawn.add(new Drawn(target, track.x, track.z, track.presence.to(1.0f, delta),
                    track.lift.to(held ? 1.0f : 0.0f, delta)));
        }
        fadeMissing(seen, drawn, delta);
        return drawn;
    }

    private static Track born(MapThing thing) {
        Track track = new Track();
        track.x = thing.centerX();
        track.z = thing.centerZ();
        return track;
    }

    private static void follow(Track track, MapThing target, float share) {
        track.last = target;
        track.x += (target.centerX() - track.x) * share;
        track.z += (target.centerZ() - track.z) * share;
    }

    private void fadeMissing(Set<String> seen, List<Drawn> drawn, float delta) {
        Iterator<Map.Entry<String, Track>> entries = tracks.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Track> entry = entries.next();
            if (seen.contains(entry.getKey())) continue;
            Track track = entry.getValue();
            float presence = track.presence.to(0.0f, delta);
            if (presence < GONE || track.last == null) {
                entries.remove();
                continue;
            }
            drawn.add(new Drawn(track.last, track.x, track.z, presence, track.lift.to(0.0f, delta)));
        }
    }
}
