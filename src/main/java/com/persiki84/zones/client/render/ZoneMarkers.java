package com.persiki84.zones.client.render;

import com.persiki84.battlecraft.client.ClientMarkerRanges;
import com.persiki84.battlecraft.rules.MarkerRange;
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
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ZoneMarkers {
    private static final String MARK_PREFIX = "mark:";
    private static final float LABEL_LIFT = 2.5f;
    private static final double RANGE_SHOWN_FROM = 24.0;
    private static final float MARGIN = 64.0f;
    private static final float FOCUS_SPEED = 10.0f;
    private static final float RANGE_FADE_SPEED = 6.0f;
    private static final float PRESENCE_SPEED = 8.0f;

    private static final Component BASE_LABEL = Component.translatable("zones.marker.base");
    private static final Component SHOP_LABEL = Component.translatable("zones.marker.shop");

    private static final Map<String, Marker> known = new HashMap<>();
    private static final Map<String, Component> markLabels = new HashMap<>();
    private static final Map<String, String> markKeys = new HashMap<>();
    private static final List<Marker> live = new ArrayList<>();
    private static final Vector4f scratch = new Vector4f();

    private ZoneMarkers() {}

    public static List<Marker> live() {
        return live;
    }

    public static void reset() {
        live.clear();
        known.clear();
        markLabels.clear();
        markKeys.clear();
    }

    public static void forget(String zoneId) {
        known.remove(zoneId);
    }

    public static void project(Vec3 camera, Matrix4f view, Matrix4f projection) {
        live.clear();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        Entity viewer = minecraft.getCameraEntity();
        boolean sheltered = VisibleZones.sheltered(viewer);
        float screenWidth = minecraft.getWindow().getGuiScaledWidth();
        float screenHeight = minecraft.getWindow().getGuiScaledHeight();

        boolean running = VisibleZones.matchRunning();
        boolean shown = !UiHud.rosterOpen();
        ResourceLocation here = minecraft.level == null ? null : minecraft.level.dimension().location();
        for (Zone zone : ClientZoneData.all()) {
            if (!listed(zone) || !zone.inDimension(here)) continue;
            boolean wanted = shown && running && wanted(zone, sheltered) && !standsInside(zone, viewer);
            place(zone, wanted, camera, view, projection, screenWidth, screenHeight);
        }
        placeMarks(shown, viewer, camera, view, projection, screenWidth, screenHeight);
        prune();
    }

    private static void place(Zone zone, boolean wanted, Vec3 camera, Matrix4f view, Matrix4f projection,
                              float screenWidth, float screenHeight) {
        ZoneArea area = zone.area();
        project(zone.id(), baseLabel(zone), area.centerX(), area.maxY() + LABEL_LIFT, area.centerZ(),
                ZoneColors.packed(zone), range(MarkerRange.ZONES, zone.markerRange()), wanted,
                camera, view, projection, screenWidth, screenHeight);
    }

    private static void placeMarks(boolean shown, Entity viewer, Vec3 camera, Matrix4f view,
                                   Matrix4f projection, float screenWidth, float screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        ResourceLocation here = minecraft.level.dimension().location();
        for (MapMark mark : ClientMarkData.all()) {
            if (!here.equals(mark.dimension())) continue;

            project(markKey(mark), markLabel(mark),
                    mark.position().getX() + 0.5, mark.position().getY() + LABEL_LIFT,
                    mark.position().getZ() + 0.5, markColor(mark),
                    range(MarkerRange.MARKS, mark.markerRange()),
                    shown && mark.inWorld() && !standsInside(mark, viewer),
                    camera, view, projection, screenWidth, screenHeight);
        }
    }

    // WHY: метка ведёт к месту, и тому, кто уже пришёл, она только закрывает обзор: внутри своей
    // WHY: зоны она гаснет присутствием в HUD, а на миникарте и полной карте остаётся
    private static boolean standsInside(Zone zone, Entity viewer) {
        if (viewer == null || !zone.hiddenInside()) return false;
        return zone.area().contains(viewer.getX(), viewer.getY(), viewer.getZ());
    }

    private static boolean standsInside(MapMark mark, Entity viewer) {
        if (viewer == null) return false;
        return mark.hideZone().contains(mark.position(), viewer.getX(), viewer.getY(), viewer.getZ());
    }

    // WHY: ключ и подпись метки собираются раз на смену, а не в кадре: проекция идёт каждый кадр
    // WHY: на каждую метку, и строка с компонентом на каждую были мусором в горячем пути
    private static String markKey(MapMark mark) {
        return markKeys.computeIfAbsent(mark.id(), ZoneMarkers::prefixed);
    }

    private static String prefixed(String id) {
        return MARK_PREFIX + id;
    }

    private static Component markLabel(MapMark mark) {
        String text = mark.label();
        Component cached = markLabels.get(mark.id());
        if (cached != null && cached.getContents() instanceof LiteralContents literal
                && literal.text().equals(text)) {
            return cached;
        }
        Component created = Component.literal(text);
        markLabels.put(mark.id(), created);
        return created;
    }

    private static int markColor(MapMark mark) {
        return mark.color() == MapMark.DEFAULT_COLOR ? UiWorldPalette.mark() : mark.color();
    }

    // WHY: метка забирается из известных до всех отказов: снятая с кадра обязана сбросить своё
    // WHY: присутствие в ноль, иначе вернувшаяся из-за края экрана вспыхнула бы сразу целиком.
    // WHY: Уход за дальность отказом не считается - он гасит метку присутствием, то есть плавно
    private static void project(String key, Component name, double x, double y, double z, int color,
                                double range, boolean wanted, Vec3 camera, Matrix4f view, Matrix4f projection,
                                float screenWidth, float screenHeight) {
        Marker marker = claim(key, name);
        double dx = x - camera.x;
        double dy = y - camera.y;
        double dz = z - camera.z;

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
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

        live.add(marker.place(screenX, screenY, (int) distance, color, wanted && distance <= range));
    }

    private static boolean offScreen(float x, float y, float width, float height) {
        return x < -MARGIN || y < -MARGIN || x > width + MARGIN || y > height + MARGIN;
    }

    private static double range(MarkerRange kind, int own) {
        return ClientMarkerRanges.blocks(kind, own);
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

    // WHY: сверка по одному лишь размеру пропускала подмену: снятая зона и добавленная метка
    // WHY: оставляли счёт прежним, и метка снятой зоны жила до следующего изменения набора
    private static void prune() {
        known.keySet().removeIf(ZoneMarkers::forgotten);
        markLabels.keySet().removeIf(ZoneMarkers::markGone);
        markKeys.keySet().removeIf(ZoneMarkers::markGone);
    }

    private static boolean markGone(String id) {
        return ClientMarkData.byId(id) == null;
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
            if (name == value || name.getString().equals(value.getString())) return;
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

        public float focus(float wanted, float delta) {
            return focus.to(wanted, delta);
        }

        public UiTagStack.Slot stack() { return stack; }
        public Component label() { return name; }
        public Component rangeLabel() { return rangeLabel; }
        public float screenX() { return screenX; }
        public float screenY() { return screenY; }
        public int color() { return color; }
    }
}
