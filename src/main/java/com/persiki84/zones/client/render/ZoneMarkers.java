package com.persiki84.zones.client.render;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiHud;
import com.persiki84.shared.client.ui.UiTagStack;
import com.persiki84.shared.client.ui.UiWorldPalette;
import com.persiki84.shared.zone.ZoneArea;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneSource;
import com.persiki84.zones.ZoneType;
import com.persiki84.zones.client.ClientMarkData;
import com.persiki84.zones.client.ClientZoneData;
import com.persiki84.zones.mark.MapMark;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ZoneMarkers {
    private static final double BASE_RANGE = 1500.0;
    private static final double MARK_RANGE = 2000.0;
    private static final String MARK_PREFIX = "mark:";
    private static final double SHOP_RANGE = 260.0;
    private static final float LABEL_LIFT = 2.5f;
    private static final double RANGE_SHOWN_FROM = 24.0;
    private static final float MARGIN = 64.0f;
    private static final float FOCUS_SPEED = 10.0f;
    private static final float RANGE_FADE_SPEED = 6.0f;
    private static final float PRESENCE_SPEED = 8.0f;

    private static final Component BASE_LABEL = Component.translatable("zones.marker.base");
    private static final Component SHOP_LABEL = Component.translatable("zones.marker.shop");

    private static final Map<String, Marker> known = new HashMap<>();
    private static final List<Marker> live = new ArrayList<>();
    private static final Vector4f scratch = new Vector4f();

    private ZoneMarkers() {}

    public static List<Marker> live() {
        return live;
    }

    public static void reset() {
        live.clear();
        known.clear();
    }

    public static void forget(String zoneId) {
        known.remove(zoneId);
    }

    public static void project(Vec3 camera, Matrix4f view, Matrix4f projection) {
        live.clear();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        boolean sheltered = VisibleZones.sheltered(minecraft.getCameraEntity());
        float screenWidth = minecraft.getWindow().getGuiScaledWidth();
        float screenHeight = minecraft.getWindow().getGuiScaledHeight();

        boolean running = VisibleZones.matchRunning();
        boolean shown = !UiHud.rosterOpen();
        for (Zone zone : ClientZoneData.all()) {
            if (!listed(zone)) continue;
            place(zone, shown && running && wanted(zone, sheltered), camera, view, projection,
                    screenWidth, screenHeight);
        }
        placeMarks(shown, camera, view, projection, screenWidth, screenHeight);
        prune();
    }

    private static void place(Zone zone, boolean wanted, Vec3 camera, Matrix4f view, Matrix4f projection,
                              float screenWidth, float screenHeight) {
        ZoneArea area = zone.area();
        project(zone.id(), baseLabel(zone), area.centerX(), area.maxY() + LABEL_LIFT, area.centerZ(),
                ZoneColors.packed(zone), range(zone), wanted, camera, view, projection, screenWidth, screenHeight);
    }

    private static void placeMarks(boolean shown, Vec3 camera, Matrix4f view, Matrix4f projection,
                                   float screenWidth, float screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        ResourceLocation here = minecraft.level.dimension().location();
        for (MapMark mark : ClientMarkData.all()) {
            if (!here.equals(mark.dimension())) continue;

            project(MARK_PREFIX + mark.id(), Component.literal(mark.label()),
                    mark.position().getX() + 0.5, mark.position().getY() + LABEL_LIFT,
                    mark.position().getZ() + 0.5, markColor(mark), MARK_RANGE, shown,
                    camera, view, projection, screenWidth, screenHeight);
        }
    }

    private static int markColor(MapMark mark) {
        return mark.color() == MapMark.DEFAULT_COLOR ? UiWorldPalette.mark() : mark.color();
    }

    // WHY: метка забирается из известных до всех отказов: снятая с кадра обязана сбросить своё
    // WHY: присутствие в ноль, иначе вернувшаяся из-за края экрана вспыхнула бы сразу целиком
    private static void project(String key, Component name, double x, double y, double z, int color,
                                double range, boolean wanted, Vec3 camera, Matrix4f view, Matrix4f projection,
                                float screenWidth, float screenHeight) {
        Marker marker = claim(key, name);
        double dx = x - camera.x;
        double dy = y - camera.y;
        double dz = z - camera.z;

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > range) {
            marker.hide();
            return;
        }

        scratch.set((float) dx, (float) dy, (float) dz, 1.0f);
        scratch.mul(view);
        scratch.mul(projection);
        if (scratch.w() <= 0.0f) {
            marker.hide();
            return;
        }

        float screenX = (scratch.x() / scratch.w() + 1.0f) * 0.5f * screenWidth;
        float screenY = (1.0f - scratch.y() / scratch.w()) * 0.5f * screenHeight;
        if (offScreen(screenX, screenY, screenWidth, screenHeight)) {
            marker.hide();
            return;
        }

        live.add(marker.place(screenX, screenY, (int) distance, color, wanted));
    }

    private static boolean offScreen(float x, float y, float width, float height) {
        return x < -MARGIN || y < -MARGIN || x > width + MARGIN || y > height + MARGIN;
    }

    private static double range(Zone zone) {
        return zone.type() == ZoneType.BASE ? BASE_RANGE : SHOP_RANGE;
    }

    // WHY: метка живёт по идентификатору зоны, а подпись зависит от типа: смена базы на магазин
    // WHY: оставляла в мире старое слово, пока зона не пересоздана
    private static Marker claim(String key, Component name) {
        Marker marker = known.get(key);
        if (marker != null) {
            marker.rename(name);
            return marker;
        }

        Marker created = new Marker(name);
        known.put(key, created);
        return created;
    }

    private static void prune() {
        if (known.size() <= ClientZoneData.all().size() + ClientMarkData.all().size()) return;
        known.keySet().removeIf(ZoneMarkers::forgotten);
    }

    private static boolean forgotten(String key) {
        if (key.startsWith(MARK_PREFIX)) {
            return ClientMarkData.byId(key.substring(MARK_PREFIX.length())) == null;
        }
        return ClientZoneData.byId(key) == null;
    }

    private static boolean listed(Zone zone) {
        if (zone.source() == ZoneSource.CAPTURE_POINT) return false;
        return zone.type() == ZoneType.BASE || zone.type() == ZoneType.SHOP;
    }

    // WHY: метка магазина держится только под крышей базы, и это условие снимается так же плавно,
    // WHY: как ставится: выход из базы гасит её присутствием, а не изъятием из кадра
    private static boolean wanted(Zone zone, boolean sheltered) {
        if (!ZoneColors.visibleToOwnTeam(zone)) return false;
        return zone.type() != ZoneType.SHOP || sheltered;
    }

    private static Component baseLabel(Zone zone) {
        return zone.type() == ZoneType.SHOP ? SHOP_LABEL : BASE_LABEL;
    }

    public static final class Marker {
        private final Smooth focus = new Smooth(0.0f, FOCUS_SPEED);
        private final Smooth ranged = new Smooth(0.0f, RANGE_FADE_SPEED);
        private final Smooth presence = new Smooth(0.0f, PRESENCE_SPEED);
        private final UiTagStack.Slot stack = UiTagStack.slot();
        private Component name;
        private Component rangeLabel = Component.empty();
        private int labelledDistance = -1;
        private float screenX;
        private float screenY;
        private int distance;
        private int color;
        private boolean held;

        private Marker(Component name) {
            this.name = name;
        }

        private void rename(Component value) {
            if (name.getString().equals(value.getString())) return;
            name = value;
        }

        private Marker place(float x, float y, int away, int packedColor, boolean kept) {
            screenX = x;
            screenY = y;
            distance = away;
            color = packedColor;
            held = kept;
            stack.depth(away);
            relabel();
            return this;
        }

        private void hide() {
            held = false;
            presence.snap(0.0f);
        }

        public float presence(float delta) {
            return UiAnim.easeOut(presence.to(held ? 1.0f : 0.0f, delta));
        }

        private void relabel() {
            if (distance == labelledDistance) return;

            labelledDistance = distance;
            rangeLabel = Component.translatable("zones.marker.range.only", distance);
        }

        public float ranged(float delta) {
            return ranged.to(distance >= RANGE_SHOWN_FROM ? 1.0f : 0.0f, delta);
        }

        public float focus(boolean aimed, float delta) {
            return focus.to(aimed ? 1.0f : 0.0f, delta);
        }

        public UiTagStack.Slot stack() { return stack; }
        public Component label() { return name; }
        public Component rangeLabel() { return rangeLabel; }
        public float screenX() { return screenX; }
        public float screenY() { return screenY; }
        public int color() { return color; }
    }
}
