package com.persiki84.airdrop.client;

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

public final class CacheMarkers {
    private static final double MARKER_RANGE = 6.0;
    private static final float LABEL_LIFT = 1.25f;
    private static final float MARGIN = 48.0f;
    private static final float FOCUS_SPEED = 10.0f;
    private static final float PRESENCE_SPEED = 8.0f;

    private static final Map<Long, Marker> known = new HashMap<>();
    private static final List<Marker> live = new ArrayList<>();
    private static final Vector4f scratch = new Vector4f();

    private static long pass;

    private CacheMarkers() {}

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

        float width = minecraft.getWindow().getGuiScaledWidth();
        float height = minecraft.getWindow().getGuiScaledHeight();
        for (ClientCacheField.Cell cell : ClientCacheField.cells()) {
            if (!cell.missing()) place(minecraft.level, cell, camera, view, projection, width, height);
        }
        known.values().removeIf(marker -> marker.pass != pass);
    }

    private static void place(BlockGetter level, ClientCacheField.Cell cell, Vec3 camera, Matrix4f view,
                              Matrix4f projection, float width, float height) {
        Marker marker = known.computeIfAbsent(cell.pos().asLong(), key -> new Marker());
        marker.pass = pass;
        double distance = Math.sqrt(cell.pos().distToCenterSqr(camera));

        scratch.set((float) (cell.pos().getX() + 0.5 - camera.x), (float) (cell.pos().getY() + LABEL_LIFT - camera.y),
                (float) (cell.pos().getZ() + 0.5 - camera.z), 1.0f);
        scratch.mul(view);
        scratch.mul(projection);
        if (scratch.w() <= 0.0f) {
            marker.hide();
            return;
        }
        float screenX = (scratch.x() / scratch.w() + 1.0f) * 0.5f * width;
        float screenY = (1.0f - scratch.y() / scratch.w()) * 0.5f * height;
        if (screenX < -MARGIN || screenY < -MARGIN || screenX > width + MARGIN || screenY > height + MARGIN) {
            marker.hide();
            return;
        }
        marker.relabel(cell);
        boolean wanted = distance <= MARKER_RANGE && UiSight.clear(level, camera, cell.pos());
        live.add(marker.place(screenX, screenY, (int) distance, CacheTint.color(cell.tier()), wanted));
    }

    public static final class Marker {
        private final Smooth focus = new Smooth(0.0f, FOCUS_SPEED);
        private final Smooth presence = new Smooth(0.0f, PRESENCE_SPEED);
        private final UiTagStack.Slot stack = UiTagStack.slot();
        private Component name = Component.empty();
        private Component detail = Component.empty();
        private int labelledState = Integer.MIN_VALUE;
        private float screenX;
        private float screenY;
        private int color;
        private long pass;
        private boolean held;

        // WHY: подпись пересобирается только при смене того, что в ней написано: компонент в каждом
        // WHY: кадре это аллокация на каждую метку в поле зрения
        private void relabel(ClientCacheField.Cell cell) {
            int state = stateOf(cell);
            if (state == labelledState) return;

            labelledState = state;
            name = Component.translatable("airdrop.cache.marker", Component.translatable(cell.tier().label()));
            detail = detailOf(cell);
        }

        private static int stateOf(ClientCacheField.Cell cell) {
            if (!ClientCacheField.running()) return -3;
            if (!cell.empty()) return -2;
            return cell.waiting() ? cell.secondsLeft() : -1;
        }

        private static Component detailOf(ClientCacheField.Cell cell) {
            if (!ClientCacheField.running()) return Component.translatable("airdrop.cache.marker.locked");
            if (!cell.empty()) return Component.translatable("airdrop.cache.marker.full");
            if (!cell.waiting()) return Component.translatable("airdrop.cache.marker.empty");

            int seconds = cell.secondsLeft();
            return Component.translatable("airdrop.cache.marker.refill", seconds / 60, String.format("%02d", seconds % 60));
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
