package com.persiki84.zones.client;

import com.persiki84.zones.Zone;
import com.persiki84.zones.client.render.VisibleZones;
import net.minecraft.world.entity.player.Player;

public final class ZoneOccupancy {
    private static final double EXIT_MARGIN = 1.0;

    private static Zone current;

    private ZoneOccupancy() {}

    public static Zone current() {
        return current;
    }

    public static void refresh(Player player) {
        Zone smallest = null;
        for (Zone zone : VisibleZones.current()) {
            double margin = zone == current ? EXIT_MARGIN : 0.0;
            if (!zone.area().contains(player.getX(), player.getY(), player.getZ(), margin)) continue;
            if (smallest == null || zone.area().footprint() < smallest.area().footprint()) {
                smallest = zone;
            }
        }
        current = smallest;
    }

    public static void clear() {
        current = null;
    }
}
