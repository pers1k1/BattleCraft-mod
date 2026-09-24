package com.persiki84.quarrymod.client;

import com.persiki84.shared.Names;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiSight;
import com.persiki84.shared.client.ui.UiTagStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class QuarryMarkers {
    private static final double MARKER_RANGE = 5.0;
    private static final float LABEL_LIFT = 1.1f;
    private static final float MARGIN = 48.0f;
    private static final float FOCUS_SPEED = 10.0f;
    private static final float PRESENCE_SPEED = 8.0f;

    private static final Map<Long, Marker> known = new HashMap<>();
    private static final List<Marker> live = new ArrayList<>();
    private static final Vector4f scratch = new Vector4f();

    private static long pass;

    private QuarryMarkers() {}

    public static List<Marker> live() {
        return live;
    }

    public static void reset() {
        live.clear();
        known.clear();
    }

    public static void project(Vec3 camera, Matrix4f view, Matrix4f projection) {
        live.clear();
        pass++;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;

        float screenWidth = minecraft.getWindow().getGuiScaledWidth();
        float screenHeight = minecraft.getWindow().getGuiScaledHeight();

        for (ClientQuarryField.Cell cell : ClientQuarryField.cells()) {
            place(minecraft.level, cell, camera, view, projection, screenWidth, screenHeight);
        }
        prune();
    }

    private static void place(BlockGetter level, ClientQuarryField.Cell cell, Vec3 camera, Matrix4f view,
                              Matrix4f projection, float screenWidth, float screenHeight) {
        Marker marker = claim(cell);
        marker.pass = pass;
        double x = cell.pos().getX() + 0.5 - camera.x;
        double y = cell.pos().getY() + LABEL_LIFT - camera.y;
        double z = cell.pos().getZ() + 0.5 - camera.z;
        double distance = Math.sqrt(cell.pos().distToCenterSqr(camera));

        scratch.set((float) x, (float) y, (float) z, 1.0f);
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

        marker.relabel(cell);
        live.add(marker.place(screenX, screenY, (int) distance, cell.color(), wanted(level, cell, camera, distance)));
    }

    // WHY: метка висит только над клеткой вплотную и в прямой видимости: карьер это сотня блоков
    // WHY: рядом, и подписать их издалека значит закрыть плашками весь экран
    private static boolean wanted(BlockGetter level, ClientQuarryField.Cell cell, Vec3 camera, double distance) {
        return distance <= MARKER_RANGE && UiSight.clear(level, camera, cell.pos());
    }

    private static boolean offScreen(float x, float y, float width, float height) {
        return x < -MARGIN || y < -MARGIN || x > width + MARGIN || y > height + MARGIN;
    }

    private static Marker claim(ClientQuarryField.Cell cell) {
        long key = cell.pos().asLong();
        Marker marker = known.get(key);
        if (marker != null) return marker;

        Marker created = new Marker(Names.block(cell.ore()));
        known.put(key, created);
        return created;
    }

    // WHY: метка снимается по отметке прохода, а не сверкой со списком клеток: сверка списком
    // WHY: это перебор в переборе, и на поле в две сотни блоков она считается каждый кадр
    private static void prune() {
        known.values().removeIf(marker -> marker.pass != pass);
    }

    public static final class Marker {
        private final Smooth focus = new Smooth(0.0f, FOCUS_SPEED);
        private final Smooth presence = new Smooth(0.0f, PRESENCE_SPEED);
        private final UiTagStack.Slot stack = UiTagStack.slot();
        private Component name;
        private Component detail = Component.empty();
        private int labelledSeconds = -1;
        private int labelledMultiplier = -1;
        private float screenX;
        private float screenY;
        private int color;
        private long pass;
        private boolean held;

        private Marker(Component name) {
            this.name = name;
        }

        private void relabel(ClientQuarryField.Cell cell) {
            name = Names.block(cell.ore());
            if (cell.excavated()) {
                retime(cell.secondsLeft());
            } else {
                remultiply(cell.multiplier());
            }
        }

        private void retime(int seconds) {
            if (seconds == labelledSeconds) return;

            labelledSeconds = seconds;
            labelledMultiplier = -1;
            detail = Component.translatable("quarrymod.marker.timer", seconds / 60, String.format("%02d", seconds % 60));
        }

        private void remultiply(int multiplier) {
            if (multiplier == labelledMultiplier) return;

            labelledMultiplier = multiplier;
            labelledSeconds = -1;
            detail = multiplier <= 1
                    ? Component.translatable("quarrymod.marker.ready")
                    : Component.translatable("quarrymod.marker.multiplier", multiplier);
        }

        private Marker place(float x, float y, int away, int packedColor, boolean kept) {
            screenX = x;
            screenY = y;
            color = packedColor;
            held = kept;
            stack.depth(away);
            return this;
        }

        private void hide() {
            held = false;
            presence.snap(0.0f);
        }

        public float presence(float delta) {
            return UiAnim.easeOut(presence.to(held ? 1.0f : 0.0f, delta));
        }

        public float focus(float wanted, float delta) {
            return focus.to(wanted, delta);
        }

        public UiTagStack.Slot stack() { return stack; }
        public Component label() { return name; }
        public Component detail() { return detail; }
        public float screenX() { return screenX; }
        public float screenY() { return screenY; }
        public int color() { return color; }
    }
}
