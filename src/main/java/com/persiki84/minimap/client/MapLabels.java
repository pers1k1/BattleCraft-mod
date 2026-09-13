package com.persiki84.minimap.client;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.zones.client.ClientMarkData;
import com.persiki84.zones.mark.MapMark;
import com.persiki84.zones.mark.MarkKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

// WHY: надпись на карте это не метка: у неё нет ни точки, ни плашки, только текст и свечение
// WHY: своим цветом, поэтому она рисуется отдельным слоем, а не через renderMarker
public final class MapLabels {
    public static final float SCALE = 1.1f;

    private static final float LINE_GAP = 1.0f;
    private static final float GRAB_PAD = 4.0f;
    private static final float HOVER_SPEED = 9.0f;
    private static final float HOVER_LIFT = 0.22f;
    private static final float AURA_LAYERS = 3.0f;
    private static final float AURA_SPREAD = 3.4f;
    private static final float AURA_ALPHA = 0.16f;
    private static final float ARMED_PERIOD_MS = 1100.0f;
    private static final float ARMED_LOW = 0.55f;
    private static final float ARMED_HIGH = 1.0f;
    private static final long PENDING_LIFE_MS = 3000L;
    private static final int PENDING_REACH = 1;

    private static final float PRESENCE_SPEED = 6.5f;
    private static final float TRAVEL_SPEED = 11.0f;
    private static final float SPAWN_RISE = 5.0f;

    private static final Map<String, Glow> glows = new HashMap<>();
    private static final List<MapMark> visible = new ArrayList<>();

    private static String pendingId;
    private static int pendingX;
    private static int pendingZ;
    private static long pendingSince;

    private MapLabels() {}

    // WHY: список собирается в своё поле, а не заводится заново: он спрашивается каждый кадр
    // WHY: отрисовки карты, а в кадре у нас нет аллокаций
    public static List<MapMark> all() {
        Minecraft minecraft = Minecraft.getInstance();
        visible.clear();
        for (MapMark mark : ClientMarkData.all()) {
            if (mark.kind() != MarkKind.TEXT) continue;
            if (minecraft.level == null || !minecraft.level.dimension().location().equals(mark.dimension())) continue;
            visible.add(mark);
        }
        return visible;
    }

    public static boolean isLabel(MapMark mark) {
        return mark.kind() == MarkKind.TEXT;
    }

    public static void hold(String id, int x, int z) {
        pendingId = id;
        pendingX = x;
        pendingZ = z;
        pendingSince = System.currentTimeMillis();
    }

    public static double worldX(MapMark mark) {
        return held(mark) ? pendingX + 0.5 : mark.position().getX() + 0.5;
    }

    public static double worldZ(MapMark mark) {
        return held(mark) ? pendingZ + 0.5 : mark.position().getZ() + 0.5;
    }

    // WHY: сервер отвечает на перенос не в тот же кадр, поэтому надпись держится на месте,
    // WHY: куда её отпустили, пока не приедет тот же ответ: иначе она прыгает назад и обратно
    private static boolean held(MapMark mark) {
        if (pendingId == null || !pendingId.equals(mark.id())) return false;
        if (System.currentTimeMillis() - pendingSince > PENDING_LIFE_MS) {
            pendingId = null;
            return false;
        }
        if (Math.abs(mark.position().getX() - pendingX) <= PENDING_REACH
                && Math.abs(mark.position().getZ() - pendingZ) <= PENDING_REACH) {
            pendingId = null;
            return false;
        }
        return true;
    }

    public static String under(Font font, double mouseX, double mouseY,
                              double mapX, double mapZ, float zoom, int centerX, int centerY) {
        for (MapMark mark : all()) {
            float screenX = (float) (centerX + (worldX(mark) - mapX) * zoom);
            float screenY = (float) (centerY + (worldZ(mark) - mapZ) * zoom);
            float half = blockWidth(font, mark) / 2.0f + GRAB_PAD;
            float reach = blockHeight(font, mark) / 2.0f + GRAB_PAD;

            if (Math.abs(mouseX - screenX) <= half && Math.abs(mouseY - screenY) <= reach) return mark.id();
        }
        return null;
    }

    // WHY: место надписи ведётся сглаживанием в мировых единицах, а не в экранных: экранная точка
    // WHY: зависит ещё и от протяжки карты, и надпись тянулась бы за каждым движением фона
    public static void render(GuiGraphics graphics, Font font, double mapX, double mapZ, float zoom,
                              int centerX, int centerY, String hovered, String armed) {
        float delta = UiFrame.delta();
        for (MapMark mark : all()) {
            Glow state = glowOf(mark);
            boolean lit = mark.id().equals(hovered) || mark.id().equals(armed);
            float glow = state.highlight.to(lit ? 1.0f : 0.0f, HOVER_SPEED, delta);
            if (mark.id().equals(armed)) {
                glow *= UiAnim.pulse(ARMED_PERIOD_MS, ARMED_LOW, ARMED_HIGH);
            }

            float shown = state.presence.to(1.0f, PRESENCE_SPEED, delta);
            float worldX = state.travelX.to((float) worldX(mark), TRAVEL_SPEED, delta);
            float worldZ = state.travelZ.to((float) worldZ(mark), TRAVEL_SPEED, delta);
            float screenX = (float) (centerX + (worldX - mapX) * zoom);
            float screenY = (float) (centerY + (worldZ - mapZ) * zoom) + (1.0f - shown) * SPAWN_RISE;
            paint(graphics, font, mark, screenX, screenY, glow, shown);
        }
        forgetGone(visible.size());
    }

    private static void paint(GuiGraphics graphics, Font font, MapMark mark,
                              float screenX, float screenY, float glow, float shown) {
        if (shown <= 0.01f) return;

        float width = blockWidth(font, mark);
        float height = blockHeight(font, mark);
        int color = UiTheme.alpha(UiTheme.lighten(mark.color(), HOVER_LIFT * glow), shown);

        if (glow > 0.01f) paintAura(graphics, mark.color(), screenX, screenY, width, height, glow * shown);

        float scale = UiRender.crisp(graphics, SCALE);
        float lineHeight = font.lineHeight * scale + LINE_GAP;
        float y = screenY - height / 2.0f;
        for (String line : mark.lines()) {
            if (!line.isEmpty()) UiRender.labelCentered(graphics, font, line, screenX, y, scale, color);
            y += lineHeight;
        }
    }

    // WHY: подсветка это мягкий ореол цветом надписи, а не подложка: плашка под текстом сделала бы
    // WHY: из надписи обычную метку, чего в ней как раз и не должно быть
    private static void paintAura(GuiGraphics graphics, int color, float screenX, float screenY,
                                  float width, float height, float glow) {
        for (int layer = 1; layer <= (int) AURA_LAYERS; layer++) {
            float spread = AURA_SPREAD * layer * glow;
            float alpha = AURA_ALPHA * glow / layer;
            UiRender.panel(graphics, screenX - width / 2.0f - spread, screenY - height / 2.0f - spread,
                    width + spread * 2.0f, height + spread * 2.0f,
                    UiMetrics.radius(height + spread * 2.0f), UiTheme.withAlpha(color, alpha));
        }
    }

    public static float blockWidth(Font font, MapMark mark) {
        float widest = 0.0f;
        for (String line : mark.lines()) {
            widest = Math.max(widest, UiRender.widthLabel(font, line) * SCALE);
        }
        return Math.max(widest, UiMetrics.GAP_WIDE);
    }

    public static float blockHeight(Font font, MapMark mark) {
        int count = Math.max(1, mark.lines().size());
        return count * (font.lineHeight * SCALE + LINE_GAP) - LINE_GAP;
    }

    private static Glow glowOf(MapMark mark) {
        Glow known = glows.get(mark.id());
        if (known != null) return known;

        Glow fresh = new Glow((float) worldX(mark), (float) worldZ(mark));
        glows.put(mark.id(), fresh);
        return fresh;
    }

    private static void forgetGone(int alive) {
        if (glows.size() <= alive) return;

        Iterator<Map.Entry<String, Glow>> cursor = glows.entrySet().iterator();
        while (cursor.hasNext()) {
            if (ClientMarkData.byId(cursor.next().getKey()) == null) cursor.remove();
        }
    }

    private static final class Glow {
        private final Smooth highlight = new Smooth(0.0f, HOVER_SPEED);
        private final Smooth presence = new Smooth(0.0f, PRESENCE_SPEED);
        private final Smooth travelX;
        private final Smooth travelZ;

        private Glow(float worldX, float worldZ) {
            this.travelX = new Smooth(worldX, TRAVEL_SPEED);
            this.travelZ = new Smooth(worldZ, TRAVEL_SPEED);
        }
    }
}
