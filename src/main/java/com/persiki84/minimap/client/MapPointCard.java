package com.persiki84.minimap.client;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiWindowMotion;
import com.persiki84.capturepoints.client.ClientCaptureData;
import com.persiki84.capturepoints.network.PointPerks;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

// WHY: окно точки стоит у её значка, а не у курсора: карта под ним ездит, и окно едет вместе с
// WHY: точкой. Приходит и уходит набором движения игрока, как окна экранов
final class MapPointCard {
    private static final float ENTER_SECONDS = 0.35f;
    private static final float BURN_SECONDS = 0.26f;
    private static final float PAD = 9.0f;
    private static final float TITLE_ROW = 15.0f;
    private static final float ROW = 11.0f;
    private static final float COLUMN_GAP = 12.0f;
    private static final float TITLE_SCALE = 0.95f;
    private static final float TEXT_SCALE = 0.8f;
    private static final float RADIUS = 8.0f;
    private static final float LIFT = 0.2f;
    private static final float OFFSET = 10.0f;
    private static final float EDGE = 4.0f;

    private final String name;
    private final boolean last;
    private final Font font;
    private final PointPerks perks;
    private final String owner;
    private final UiWindowMotion.Painter painter = this::paint;
    private final Component title;
    private final List<PointCardLines.Line> lines;
    private final UiWindowMotion motion = new UiWindowMotion(ENTER_SECONDS, BURN_SECONDS);
    private final float width;
    private final float height;
    private final double worldX;
    private final double worldZ;
    private float anchorX;
    private float anchorY;

    MapPointCard(Font font, String name, boolean last, double worldX, double worldZ) {
        this.name = name;
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.last = last;
        this.font = font;
        this.perks = ClientCaptureData.getPerks(name);
        this.owner = ownerOf(name, last);
        this.title = Component.literal(name);
        this.lines = PointCardLines.of(name, last);
        float labels = 0.0f;
        float values = 0.0f;
        for (PointCardLines.Line line : lines) {
            labels = Math.max(labels, UiRender.width(font, line.label()) * TEXT_SCALE);
            values = Math.max(values, UiRender.width(font, line.value()) * TEXT_SCALE);
        }
        float body = labels + COLUMN_GAP + values;
        this.width = Math.max(body, UiRender.width(font, title) * TITLE_SCALE) + PAD * 2.0f;
        this.height = TITLE_ROW + lines.size() * ROW + PAD;
    }

    String name() {
        return name;
    }

    // WHY: окно собирается один раз на показ; захват точки или правка её бонусов под курсором
    // WHY: пересобирает его, иначе окно показывало бы прежнего владельца
    boolean stale() {
        PointPerks now = ClientCaptureData.getPerks(name);
        boolean changed = perks == null ? now != null : !perks.sameAs(now);
        return changed || !Objects.equals(ownerOf(name, last), owner);
    }

    private static String ownerOf(String name, boolean last) {
        return last ? ClientCaptureData.getAllFinalPointOwners().get(name)
                : ClientCaptureData.getAllPointOwners().get(name);
    }

    // WHY: раскол рисует осколки по полю, замощённому на месте окна в начале ухода, поэтому
    // WHY: раскалывающееся окно остаётся там, где треснуло
    void follow(double mapX, double mapZ, double zoom, int centerX, int centerY) {
        if (motion.splitting()) return;
        anchorX = (float) (centerX + (worldX - mapX) * zoom);
        anchorY = (float) (centerY + (worldZ - mapZ) * zoom);
    }

    void beginClose() {
        motion.beginClose();
    }

    boolean closing() {
        return motion.closing();
    }

    boolean gone() {
        return motion.finished();
    }

    // WHY: окно едет за картой, поэтому встаёт на целый пиксель: иначе буквы дрожат при сдвиге
    void render(GuiGraphics graphics, float screenWidth, float screenHeight) {
        float left = UiRender.snapX(graphics, clamp(beside(anchorX, width, screenWidth), screenWidth - width - EDGE));
        float top = clamp(beside(anchorY, height, screenHeight), screenHeight - height - EDGE);
        motion.render(graphics, screenWidth, screenHeight, left, top, width, height, RADIUS, painter);
    }

    private void paint(GuiGraphics graphics, float left, float top) {
        UiGlass.window(graphics, left, top, width, height, RADIUS, 1.0f, LIFT);
        UiRender.textScaled(graphics, font, title, left + PAD, top + PAD - 2.0f, TITLE_SCALE, UiAccent.text(), false);
        float y = top + TITLE_ROW + PAD / 2.0f;
        float valueRight = left + width - PAD;
        for (int index = 0; index < lines.size(); index++) {
            PointCardLines.Line line = lines.get(index);
            UiRender.textScaled(graphics, font, line.label(), left + PAD, y, TEXT_SCALE, UiAccent.textDim(), false);
            int tint = line.tint() == 0 ? UiAccent.text() : UiTheme.muted(line.tint(), 0.15f);
            UiRender.textRight(graphics, font, line.value(), valueRight, y, TEXT_SCALE, tint, false);
            y += ROW;
        }
    }

    // WHY: у края экрана окно перекидывается на другую сторону значка, а не наезжает на него
    private static float beside(float anchor, float size, float limit) {
        float after = anchor + OFFSET;
        return after + size > limit - EDGE ? anchor - OFFSET - size : after;
    }

    private static float clamp(float value, float limit) {
        return Math.max(EDGE, Math.min(value, Math.max(EDGE, limit)));
    }
}
