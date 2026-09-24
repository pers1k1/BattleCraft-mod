package com.persiki84.zones.client.render;

import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.zones.Zone;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// WHY: список видимых зон меняется тиком (старт матча, смена команды, прогрузка чанков, дальность),
// WHY: и объём, рисуемый только по нему, возникал и пропадал кадром; ушедшая зона дорисовывается
// WHY: по последней ссылке, пока не погаснет
final class ZonePresence {
    private static final float APPEAR_SECONDS = 0.7f;
    private static final float LEAVE_SECONDS = 0.5f;
    private static final float MAX_STEP = 0.1f;

    static final class Entry {
        private Zone zone;
        private float shown;
        private long seen;

        Zone zone() {
            return zone;
        }

        float alpha() {
            return UiAnim.easeOut(shown);
        }
    }

    private static final Map<String, Entry> entries = new HashMap<>();
    private static final List<Entry> order = new ArrayList<>();
    private static long frame;
    private static Level level;

    private ZonePresence() {}

    // WHY: гаснущая зона прежнего мира дорисовалась бы в новом по тем же координатам
    static void begin(Level current) {
        if (current != level) {
            reset();
            level = current;
        }
        frame++;
    }

    static void want(List<Zone> zones) {
        for (int index = 0; index < zones.size(); index++) {
            Zone zone = zones.get(index);
            Entry entry = entries.get(zone.cacheKey());
            if (entry == null) {
                entry = new Entry();
                entries.put(zone.cacheKey(), entry);
                order.add(entry);
            }
            entry.zone = zone;
            entry.seen = frame;
        }
    }

    static void advance(float seconds) {
        float step = Math.min(MAX_STEP, seconds);
        for (int index = order.size() - 1; index >= 0; index--) {
            Entry entry = order.get(index);
            if (entry.seen == frame) {
                entry.shown = Math.min(1.0f, entry.shown + step / APPEAR_SECONDS);
                continue;
            }
            entry.shown -= step / LEAVE_SECONDS;
            if (entry.shown <= 0.0f) drop(index, entry);
        }
    }

    private static void drop(int index, Entry entry) {
        order.remove(index);
        String key = entry.zone.cacheKey();
        entries.remove(key);
        ZoneMeshes.invalidate(key);
    }

    static int size() {
        return order.size();
    }

    static Entry get(int index) {
        return order.get(index);
    }

    static boolean idle() {
        return order.isEmpty();
    }

    static void reset() {
        entries.clear();
        order.clear();
        level = null;
    }
}
