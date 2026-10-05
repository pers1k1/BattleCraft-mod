package com.persiki84.minimap.client;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiClip;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.zone.ZoneShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;

// WHY: редакторы точек, зон и меток показывают свои вещи там, где они стоят в мире: местность
// WHY: берётся из той же карты, что у миникарты, а поверх неё экран рисует свои области. Карта
// WHY: держит только вид (центр, зум, плавный переезд), выбор и правку решает экран
public final class MapCanvas {
    public static final float GRAB_PIXELS = 7.0f;

    private static final float MIN_ZOOM = 0.125f;
    private static final float MAX_ZOOM = 8.0f;
    private static final float ZOOM_STEP = 1.25f;
    private static final float ZOOM_SPEED = 14.0f;
    private static final float GLIDE_SPEED = 9.0f;
    private static final float LIT_SPEED = 12.0f;
    private static final float RADIUS = 7.0f;
    private static final float GRID_MIN_PIXELS = 28.0f;
    private static final int GRID_COLOR = 0x14FFFFFF;
    private static final float FRAME_SHARE = 0.32f;
    private static final float PIN = 3.2f;
    private static final float PIN_RISE = 2.0f;
    private static final float PIN_GROW = 0.3f;
    private static final float PIN_SHADOW = 0.45f;
    private static final float LABEL_SCALE = 0.62f;
    private static final float PLAYER_ARROW = 4.2f;
    private static final float REVEAL_ZOOM = 0.82f;

    private final Map<String, Smooth> lit = new HashMap<>();
    private double centerX;
    private double centerZ;
    private double goalX;
    private double goalZ;
    private boolean gliding;
    private boolean centered;
    private float zoom = 1.0f;
    private float targetZoom = 1.0f;
    private double anchorX = Double.NaN;
    private double anchorY;
    private float left;
    private float top;
    private float width;
    private float height;

    public interface Painter {
        void paint(GuiGraphics graphics);
    }

    public void place(float canvasLeft, float canvasTop, float canvasWidth, float canvasHeight) {
        left = canvasLeft;
        top = canvasTop;
        width = canvasWidth;
        height = canvasHeight;
    }

    public boolean over(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    public boolean centered() {
        return centered;
    }

    public void centerOnPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        centerX = goalX = player.getX();
        centerZ = goalZ = player.getZ();
        gliding = false;
        centered = true;
    }

    // WHY: выбор вещи в списке слева переводит карту к ней, а не перескакивает: глаз видит, откуда
    // WHY: и куда уехал вид, и понимает, где вещь стоит относительно прошлой
    public void glideTo(double worldX, double worldZ, double reach) {
        if (!centered) {
            centerX = worldX;
            centerZ = worldZ;
            centered = true;
        }
        goalX = worldX;
        goalZ = worldZ;
        gliding = true;
        anchorX = Double.NaN;
        if (reach > 0.0 && width > 0.0f) {
            targetZoom = clampZoom((float) (Math.min(width, height) * FRAME_SHARE / reach));
        }
    }

    // WHY: карта, показанная заново, наезжает к своему масштабу, а не встаёт кадром, тем же ходом,
    // WHY: что и зум колесом
    public void reveal() {
        zoom = targetZoom * REVEAL_ZOOM;
        anchorX = Double.NaN;
    }

    public void pan(double dragX, double dragY) {
        centerX -= dragX / zoom;
        centerZ -= dragY / zoom;
        goalX = centerX;
        goalZ = centerZ;
        gliding = false;
    }

    public void zoomAt(double mouseX, double mouseY, double amount) {
        float factor = amount > 0 ? ZOOM_STEP : 1.0f / ZOOM_STEP;
        targetZoom = clampZoom(targetZoom * factor);
        anchorX = mouseX;
        anchorY = mouseY;
        gliding = false;
    }

    private static float clampZoom(float value) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
    }

    public float screenX(double worldX) {
        return (float) (left + width / 2.0f + (worldX - centerX) * zoom);
    }

    public float screenY(double worldZ) {
        return (float) (top + height / 2.0f + (worldZ - centerZ) * zoom);
    }

    public double worldX(double mouseX) {
        return centerX + (mouseX - left - width / 2.0f) / zoom;
    }

    public double worldZ(double mouseY) {
        return centerZ + (mouseY - top - height / 2.0f) / zoom;
    }

    public float lit(String key, boolean chosen) {
        return lit.computeIfAbsent(key, unused -> new Smooth(0.0f, LIT_SPEED)).to(chosen ? 1.0f : 0.0f, UiFrame.delta());
    }

    // WHY: поверхность под точкой щелчка известна, только если клиент держит этот чанк; иначе
    // WHY: высота берётся с игрока, а не с нуля: созданное в воздухе или под землёй потом не найти
    public static int surfaceY(int blockX, int blockZ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return 64;
        if (!minecraft.level.hasChunk(blockX >> 4, blockZ >> 4)) return minecraft.player.getBlockY();
        return minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
    }

    public void render(GuiGraphics graphics, Painter shapes, Painter labels) {
        advance();
        UiRender.panel(graphics, left, top, width, height, RADIUS, UiPalette.panelDeep());
        if (UiClip.begin(graphics)) {
            try {
                paintInside(graphics, shapes);
            } finally {
                UiClip.end(graphics, left, top, width, height, RADIUS);
            }
        } else {
            UiRender.clip(graphics, left, top, width, height);
            try {
                paintInside(graphics, shapes);
            } finally {
                graphics.disableScissor();
            }
        }
        UiRender.clip(graphics, left, top, width, height);
        try {
            labels.paint(graphics);
        } finally {
            graphics.disableScissor();
        }
    }

    private void paintInside(GuiGraphics graphics, Painter shapes) {
        float middleX = left + width / 2.0f;
        float middleY = top + height / 2.0f;
        MapTextureManager.renderMap(graphics, centerX, centerZ, zoom, middleX, middleY,
                left, top, left + width, top + height);
        paintGrid(graphics);
        shapes.paint(graphics);
        paintPlayer(graphics);
    }

    private void advance() {
        float delta = UiFrame.delta();
        if (gliding) {
            float share = 1.0f - (float) Math.exp(-GLIDE_SPEED * delta);
            centerX += (goalX - centerX) * share;
            centerZ += (goalZ - centerZ) * share;
            if (Math.abs(goalX - centerX) < 0.05 && Math.abs(goalZ - centerZ) < 0.05) gliding = false;
        }
        if (Math.abs(targetZoom - zoom) <= 0.001f) {
            zoom = targetZoom;
            return;
        }
        if (Double.isNaN(anchorX)) {
            zoom = UiAnim.approach(zoom, targetZoom, ZOOM_SPEED, delta);
            return;
        }
        double heldX = worldX(anchorX);
        double heldZ = worldZ(anchorY);
        zoom = UiAnim.approach(zoom, targetZoom, ZOOM_SPEED, delta);
        centerX = heldX - (anchorX - left - width / 2.0f) / zoom;
        centerZ = heldZ - (anchorY - top - height / 2.0f) / zoom;
        goalX = centerX;
        goalZ = centerZ;
    }

    // WHY: шаг сетки растёт степенями двойки вместе с отдалением: линия через каждые 16 блоков на
    // WHY: мелком зуме слилась бы в серую заливку, а через 512 на крупном пропала бы совсем
    private void paintGrid(GuiGraphics graphics) {
        int step = 16;
        while (step * zoom < GRID_MIN_PIXELS && step < 4096) {
            step *= 2;
        }
        double firstX = Math.ceil(worldX(left) / step) * step;
        for (double worldX = firstX; worldX <= worldX(left + width); worldX += step) {
            UiRender.rect(graphics, screenX(worldX), top, 1.0f, height, GRID_COLOR);
        }
        double firstZ = Math.ceil(worldZ(top) / step) * step;
        for (double worldZ = firstZ; worldZ <= worldZ(top + height); worldZ += step) {
            UiRender.rect(graphics, left, screenY(worldZ), width, 1.0f, GRID_COLOR);
        }
    }

    private void paintPlayer(GuiGraphics graphics) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        UiRender.arrow(graphics, screenX(player.getX()), screenY(player.getZ()), player.getYRot(), PLAYER_ARROW,
                UiAccent.color(), UiTheme.alpha(UiPalette.panelDeep(), 0.75f));
    }

    public void area(GuiGraphics graphics, ZoneShape shape, double worldX, double worldZ, double size, int color,
                     float glow, float presence) {
        float x = screenX(worldX);
        float y = screenY(worldZ);
        float reach = (float) Math.max(1.5, size * zoom) * UiAnim.easeOut(presence);
        int fill = UiTheme.alpha(color, (0.16f + 0.14f * glow) * presence);
        int rim = UiTheme.alpha(color, (0.55f + 0.45f * glow) * presence);
        float thickness = 1.0f + glow;
        if (shape == ZoneShape.SQUARE) {
            UiRender.rect(graphics, x - reach, y - reach, reach * 2.0f, reach * 2.0f, fill);
            UiRender.rim(graphics, x - reach, y - reach, reach * 2.0f, reach * 2.0f, 0.0f, thickness, rim);
            return;
        }
        UiRender.dot(graphics, x, y, reach, fill);
        UiRender.ring(graphics, x, y, reach, thickness, 1.0f, rim);
    }

    public void pin(GuiGraphics graphics, double worldX, double worldZ, int color, float glow, float presence) {
        pin(graphics, worldX, worldZ, color, glow, presence, 0.0f);
    }

    // WHY: взятая булавка приподнимается над картой: подрастает, отходит вверх от своей тени и
    // WHY: садится обратно, когда её отпускают
    public void pin(GuiGraphics graphics, double worldX, double worldZ, int color, float glow, float presence,
                    float lift) {
        float x = screenX(worldX);
        float y = screenY(worldZ) - PIN_RISE * lift;
        float size = PIN * (1.0f + 0.35f * glow + PIN_GROW * lift) * UiAnim.easeOut(presence);
        if (size <= 0.05f) return;
        if (lift > 0.01f) UiRender.dot(graphics, x, y + PIN_RISE * lift + 1.0f, size + 1.5f,
                UiTheme.alpha(UiPalette.panelDeep(), PIN_SHADOW * lift * presence));
        UiRender.dot(graphics, x, y, size + 0.8f, UiTheme.alpha(UiPalette.panelDeep(), presence));
        UiRender.dot(graphics, x, y, size, UiTheme.alpha(color, presence));
        if (glow > 0.01f) {
            UiRender.ring(graphics, x, y, size + 2.6f, 1.0f, 1.0f, UiTheme.alpha(color, 0.7f * glow * presence));
        }
    }

    public void label(GuiGraphics graphics, Font font, Component text, double worldX, double worldZ, float glow,
                      float presence) {
        if (presence <= 0.02f) return;
        float x = screenX(worldX);
        float y = screenY(worldZ) - PIN * 2.0f - 9.0f + (1.0f - presence) * 4.0f;
        int color = UiTheme.alpha(UiAccent.text(), (0.7f + 0.3f * glow) * presence);
        UiRender.textCentered(graphics, font, text, x, y, LABEL_SCALE + 0.12f * glow, color, false);
    }

    public boolean near(double mouseX, double mouseY, double worldX, double worldZ) {
        float dx = (float) (mouseX - screenX(worldX));
        float dy = (float) (mouseY - screenY(worldZ));
        return dx * dx + dy * dy <= GRAB_PIXELS * GRAB_PIXELS;
    }
}
