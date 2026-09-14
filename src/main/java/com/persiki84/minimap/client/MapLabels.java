package com.persiki84.minimap.client;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
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

    private static final float HANDLE_SIZE = 5.0f;
    private static final float HANDLE_RADIUS = 1.6f;
    private static final float HANDLE_REACH = 7.0f;

    private static String pendingId;
    private static int pendingX;
    private static int pendingZ;
    private static long pendingSince;
    private static String sizedId;
    private static int sizedPercent;
    private static float mapGrown = 1.0f;

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

    // WHY: размер держится локально до ответа сервера ровно как место: иначе взятая за угол
    // WHY: надпись прыгает к прежнему кеглю между кадром отпускания и приходом снимка
    public static void holdScale(String id, int percent) {
        sizedId = id;
        sizedPercent = percent;
    }

    public static int percentOf(MapMark mark) {
        if (!mark.id().equals(sizedId)) return mark.scalePercent();
        if (mark.scalePercent() == sizedPercent) {
            sizedId = null;
            return sizedPercent;
        }
        return sizedPercent;
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
            if (mark.id().equals(armed)) paintHandle(graphics, font, mark, screenX, screenY, glow);
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

        float scale = UiRender.crisp(graphics, scaleOf(mark));
        float lineHeight = font.lineHeight * scale + LINE_GAP;
        float y = screenY - height / 2.0f;
        for (String line : mark.lines()) {
            if (!line.isEmpty()) UiRender.labelCentered(graphics, font, line, screenX, y, scale, color);
            y += lineHeight;
        }
    }

    // WHY: ручка размера повторяет редактор HUD: взведённый элемент показывает угол, за который
    // WHY: его тянут, и другого способа поменять кегль мышью на карте нет
    private static void paintHandle(GuiGraphics graphics, Font font, MapMark mark,
                                    float screenX, float screenY, float glow) {
        float cornerX = screenX + blockWidth(font, mark) / 2.0f + HANDLE_REACH / 2.0f;
        float cornerY = screenY + blockHeight(font, mark) / 2.0f + HANDLE_REACH / 2.0f;
        float span = HANDLE_SIZE * (0.7f + 0.3f * glow);

        UiRender.panel(graphics, cornerX - span / 2.0f, cornerY - span / 2.0f, span, span,
                HANDLE_RADIUS, UiTheme.withAlpha(mark.color(), 1.0f));
        UiRender.panel(graphics, cornerX - span / 4.0f, cornerY - span / 4.0f, span / 2.0f, span / 2.0f,
                HANDLE_RADIUS / 2.0f, UiTheme.withAlpha(UiPalette.panelDeep(), 0.9f));
    }

    public static boolean onHandle(Font font, MapMark mark, double mouseX, double mouseY,
                                   float screenX, float screenY) {
        float cornerX = screenX + blockWidth(font, mark) / 2.0f + HANDLE_REACH / 2.0f;
        float cornerY = screenY + blockHeight(font, mark) / 2.0f + HANDLE_REACH / 2.0f;
        return Math.abs(mouseX - cornerX) <= HANDLE_REACH && Math.abs(mouseY - cornerY) <= HANDLE_REACH;
    }

    public static float screenX(MapMark mark, double mapX, float zoom, int centerX) {
        return (float) (centerX + (worldX(mark) - mapX) * zoom);
    }

    public static float screenY(MapMark mark, double mapZ, float zoom, int centerY) {
        return (float) (centerY + (worldZ(mark) - mapZ) * zoom);
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

    // WHY: ширина меряется перебором глифов, а спрашивают её дважды за кадр на каждую надпись -
    // WHY: на попадание курсора и на ауру; текст меняется правкой, а не кадром, поэтому промер кешируется
    // WHY: размер надписи принадлежит ей самой, а не карте: она не растёт от приближения, иначе
    // WHY: подпись области то закрывала бы половину экрана, то пропадала бы в точку
    // WHY: надпись держит размер относительно карты, как остальные значки: при постоянном экранном
    // WHY: кегле приближение делает её визуально мельче местности, и это читается как подстройка
    public static void zoom(float grown) {
        mapGrown = grown;
    }

    public static float scaleOf(MapMark mark) {
        return SCALE * mapGrown * percentOf(mark) / (float) MapMark.SCALE_FULL;
    }

    public static float blockWidth(Font font, MapMark mark) {
        Glow state = glowOf(mark);
        int stamp = mark.lines().hashCode() * 31 + percentOf(mark) + Math.round(mapGrown * 100.0f) * 7919;
        if (state.measured == stamp) return state.width;

        float scale = scaleOf(mark);
        float widest = 0.0f;
        for (String line : mark.lines()) {
            widest = Math.max(widest, UiRender.widthLabel(font, line) * scale);
        }
        state.width = Math.max(widest, UiMetrics.GAP_WIDE);
        state.measured = stamp;
        return state.width;
    }

    public static float blockHeight(Font font, MapMark mark) {
        int count = Math.max(1, mark.lines().size());
        return count * (font.lineHeight * scaleOf(mark) + LINE_GAP) - LINE_GAP;
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
        private float width;
        private int measured = -1;

        private Glow(float worldX, float worldZ) {
            this.travelX = new Smooth(worldX, TRAVEL_SPEED);
            this.travelZ = new Smooth(worldZ, TRAVEL_SPEED);
        }
    }
}
