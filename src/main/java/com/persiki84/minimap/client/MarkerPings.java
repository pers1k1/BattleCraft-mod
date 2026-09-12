package com.persiki84.minimap.client;

import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

// WHY: метка появлялась на карте одним кадром, и было не понять, поставил её ты, тиммейт или она
// WHY: висела там всё время; ход волной живёт на клиенте, потому что сервер шлёт только координату
public final class MarkerPings {
    private static final float LIFE_MS = 900.0f;
    private static final double MOVED = 0.75;
    private static final float RINGS = 2.0f;
    private static final float RING_DELAY = 0.22f;
    private static final float RING_REACH = 4.6f;
    private static final float RING_THICKNESS = 1.2f;
    private static final float POP = 1.5f;

    private static final Map<String, Ping> pings = new HashMap<>();

    private MarkerPings() {}

    public static void reset() {
        pings.clear();
    }

    public static float age(String key, double x, double z) {
        Ping ping = pings.get(key);
        long now = System.currentTimeMillis();
        if (ping == null) {
            ping = new Ping(x, z, now);
            pings.put(key, ping);
        } else {
            ping.follow(x, z, now);
        }
        ping.seen = UiFrame.frame();
        return UiAnim.clamp01((now - ping.bornAt) / LIFE_MS);
    }

    public static void sweep(int live) {
        if (pings.size() <= live) return;

        long frame = UiFrame.frame();
        Iterator<Ping> known = pings.values().iterator();
        while (known.hasNext()) {
            if (known.next().seen != frame) known.remove();
        }
    }

    // WHY: волна ставится по вспышке, а не по времени постановки: два круга подряд читаются как
    // WHY: сигнал, один такой же тихий, как и просто появившаяся точка
    public static void ripple(GuiGraphics graphics, float centerX, float centerY, float radius,
                              int color, float age) {
        if (age >= 1.0f) return;

        for (int index = 0; index < RINGS; index++) {
            float phase = (age - index * RING_DELAY) / (1.0f - RING_DELAY * (RINGS - 1.0f));
            if (phase <= 0.0f || phase >= 1.0f) continue;

            float eased = UiAnim.easeOut(phase);
            UiRender.ring(graphics, centerX, centerY, radius + eased * radius * RING_REACH,
                    RING_THICKNESS, 1.0f, UiTheme.alpha(color, (1.0f - phase) * (1.0f - phase)));
        }
    }

    public static float pop(float age) {
        return age >= 1.0f ? 1.0f : 1.0f + POP * (1.0f - UiAnim.easeOutBack(age));
    }

    private static final class Ping {
        private double x;
        private double z;
        private long bornAt;
        private long seen;

        private Ping(double x, double z, long now) {
            this.x = x;
            this.z = z;
            this.bornAt = now;
        }

        private void follow(double newX, double newZ, long now) {
            if (Math.abs(newX - x) < MOVED && Math.abs(newZ - z) < MOVED) return;

            x = newX;
            z = newZ;
            bornAt = now;
        }
    }
}
