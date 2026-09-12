package com.persiki84.zones.client.render;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiWorldPalette;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.Team;

import java.util.HashMap;
import java.util.Map;

public final class ZoneColors {

    private static final float TWEEN_MILLIS = 400.0f;

    private static final Map<String, float[]> tweened = new HashMap<>();

    private ZoneColors() {}

    public static float[] resolve(Zone zone, float captureProgress, long elapsedMillis) {
        int target = targetColor(zone, captureProgress);
        float[] current = tweened.get(zone.id());
        if (current == null) {
            current = new float[] { red(target), green(target), blue(target) };
            tweened.put(zone.id(), current);
            return current;
        }

        float step = Math.min(1.0f, elapsedMillis / TWEEN_MILLIS);
        current[0] += (red(target) - current[0]) * step;
        current[1] += (green(target) - current[1]) * step;
        current[2] += (blue(target) - current[2]) * step;
        return current;
    }

    public static int packed(Zone zone) {
        float[] color = tweened.get(zone.id());
        if (color == null) return targetColor(zone, 0.0f);
        return ((int) (color[0] * 255.0f) << 16) | ((int) (color[1] * 255.0f) << 8) | (int) (color[2] * 255.0f);
    }

    public static void forget(String zoneId) {
        tweened.remove(zoneId);
    }

    public static void reset() {
        tweened.clear();
    }

    private static int neutral() {
        return UiAccent.color() & 0xFFFFFF;
    }

    private static int targetColor(Zone zone, float captureProgress) {
        if (captureProgress > 0.0f) return UiWorldPalette.contested() & 0xFFFFFF;
        if (zone.hasCustomColor()) return zone.color();

        if (zone.ownerTeam() == null) {
            return zone.type() == ZoneType.SHOP ? UiWorldPalette.shop() & 0xFFFFFF : neutral();
        }
        return (isOwnTeam(zone) ? UiWorldPalette.friendly() : UiWorldPalette.hostile()) & 0xFFFFFF;
    }

    public static boolean visibleToOwnTeam(Zone zone) {
        return zone.ownerTeam() == null || isOwnTeam(zone);
    }

    public static boolean isOwnTeam(Zone zone) {
        if (zone.ownerTeam() == null) return false;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;

        Team team = minecraft.player.getTeam();
        return team != null && zone.belongsTo(team.getName());
    }

    private static float red(int color) {
        return ((color >> 16) & 0xFF) / 255.0f;
    }

    private static float green(int color) {
        return ((color >> 8) & 0xFF) / 255.0f;
    }

    private static float blue(int color) {
        return (color & 0xFF) / 255.0f;
    }
}
