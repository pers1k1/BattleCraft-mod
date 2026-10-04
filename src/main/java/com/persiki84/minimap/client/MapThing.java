package com.persiki84.minimap.client;

import com.persiki84.shared.zone.ZoneShape;
import net.minecraft.network.chat.Component;

public record MapThing(String key, Component name, int x, int y, int z, ZoneShape shape, double size, int color,
                       boolean here) {
    public static final double PIN_ONLY = 0.0;

    public double centerX() {
        return x + 0.5;
    }

    public double centerZ() {
        return z + 0.5;
    }

    public boolean covers(double worldX, double worldZ) {
        if (size <= PIN_ONLY) return false;
        double dx = Math.abs(worldX - centerX());
        double dz = Math.abs(worldZ - centerZ());
        if (shape == ZoneShape.SQUARE) return Math.max(dx, dz) <= size;
        return dx * dx + dz * dz <= size * size;
    }

    public MapThing at(int movedX, int movedZ) {
        return new MapThing(key, name, movedX, y, movedZ, shape, size, color, here);
    }
}
