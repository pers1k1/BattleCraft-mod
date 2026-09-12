package com.persiki84.shared.client.ui;

import com.persiki84.shared.client.font.MsdfFontSets;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.Mth;
import net.minecraftforge.client.ForgeRenderTypes;
import org.joml.Matrix4f;
import org.joml.Vector2f;

import java.util.List;

public final class UiRender {
    public static final ResourceLocation UI_FONT = new ResourceLocation("battlecraft", "ui");

    private static final ResourceLocation[] REGULAR = {
            new ResourceLocation("battlecraft", "ui1"),
            new ResourceLocation("battlecraft", "ui1_5"),
            UI_FONT,
            new ResourceLocation("battlecraft", "ui2_5"),
            new ResourceLocation("battlecraft", "ui3"),
            new ResourceLocation("battlecraft", "ui4"),
            new ResourceLocation("battlecraft", "ui6")
    };
    private static final float[] REGULAR_BAKE = {1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f, 6.0f};

    private static final ResourceLocation[] SEMIBOLD = {
            new ResourceLocation("battlecraft", "ui_semibold1"),
            new ResourceLocation("battlecraft", "ui_semibold1_5"),
            new ResourceLocation("battlecraft", "ui_semibold"),
            new ResourceLocation("battlecraft", "ui_semibold2_5"),
            new ResourceLocation("battlecraft", "ui_semibold3"),
            new ResourceLocation("battlecraft", "ui_semibold4"),
            new ResourceLocation("battlecraft", "ui_semibold6")
    };

    private static final ResourceLocation[] BOLD = {
            new ResourceLocation("battlecraft", "ui_bold1"),
            new ResourceLocation("battlecraft", "ui_bold1_5"),
            new ResourceLocation("battlecraft", "ui_bold"),
            new ResourceLocation("battlecraft", "ui_bold2_5"),
            new ResourceLocation("battlecraft", "ui_bold3"),
            new ResourceLocation("battlecraft", "ui_bold4"),
            new ResourceLocation("battlecraft", "ui_bold6")
    };
    private static final ResourceLocation[] TITLE = {
            new ResourceLocation("battlecraft", "title"),
            new ResourceLocation("battlecraft", "title3"),
            new ResourceLocation("battlecraft", "title4"),
            new ResourceLocation("battlecraft", "title6")
    };
    private static final ResourceLocation[] HERO = {
            new ResourceLocation("battlecraft", "hero"),
            new ResourceLocation("battlecraft", "hero3"),
            new ResourceLocation("battlecraft", "hero4"),
            new ResourceLocation("battlecraft", "hero6")
    };
    private static final float[] PLAIN_BAKE = {2.0f, 3.0f, 4.0f, 6.0f};

    private static final int ARC_TABLE = 24;
    private static final int SQUIRCLE_SAMPLES = 512;
    private static final float[] CIRCLE_X = new float[ARC_TABLE + 1];
    private static final float[] CIRCLE_Y = new float[ARC_TABLE + 1];
    private static final float[] SQUIRCLE_X = new float[ARC_TABLE + 1];
    private static final float[] SQUIRCLE_Y = new float[ARC_TABLE + 1];
    private static final float[] SWITCH_X = new float[ARC_TABLE + 1];
    private static final float[] SWITCH_Y = new float[ARC_TABLE + 1];

    private static final float TEXT_CENTER = 3.2f;
    private static final float MSDF_TEXT_CENTER = 4.4f;
    private static final int MAX_POINTS = 256;
    private static final int MAX_WEDGE_STEPS = 48;
    private static final float WEDGE_PIXELS = 1.6f;
    private static final float[] WEDGE_X = new float[MAX_WEDGE_STEPS * 2 + 4];
    private static final float[] WEDGE_Y = new float[MAX_WEDGE_STEPS * 2 + 4];
    private static final int SKIRT_RINGS = 5;
    private static final float FLARE_SHARE = 0.11f;
    private static final float BAND_SHARE = 0.26f;
    private static final float PULL_LIMIT = 0.34f;
    private static final float CORE_SHARE = 0.995f;
    private static final float CORNER_SHARE = 0.55f;
    private static final int FLAT_RINGS = 2;
    private static final int ARC_SEGMENTS = 44;
    private static final float ARC_PIXELS = 2.0f;
    private static final float TRACE_MIN_STEP = 1.0E-4f;
    private static final float TRACE_JOIN_COSINE = 0.985f;
    private static final int TRACE_JOIN_SEGMENTS = 10;
    private static final int SHACKLE_STEPS = 12;
    private static final float ALIGN_LEFT = 0.0f;
    private static final float ALIGN_CENTER = 0.5f;
    private static final float ALIGN_RIGHT = 1.0f;
    private static final float BAKE_SLACK = 0.02f;
    private static final float SUPERSAMPLE = 1.3f;
    private static final double SCROLL_SECONDS_PER_UNIT = 0.09;
    private static final double SCROLL_MIN_SECONDS = 3.0;
    private static final float FADE_UNITS = 5.0f;
    private static final int MAX_GLYPHS = 512;
    private static final int FULL_BRIGHT = 15728880;
    private static final int OPAQUE_FALLBACK_ALPHA = 4;

    private static final float[] PATH_X = new float[MAX_POINTS];
    private static final float[] PATH_Y = new float[MAX_POINTS];
    private static final float[] EDGE_X = new float[MAX_POINTS];
    private static final float[] EDGE_Y = new float[MAX_POINTS];
    private static final float[] MITER_X = new float[MAX_POINTS];
    private static final float[] MITER_Y = new float[MAX_POINTS];
    private static final float[] EDGE_HALF = new float[MAX_POINTS];
    private static final float[] SKIRT = new float[MAX_POINTS];
    private static final float[] RIM_WEIGHT = new float[MAX_POINTS];
    private static final float[] NORMAL_X = new float[MAX_POINTS];
    private static final float[] NORMAL_Y = new float[MAX_POINTS];
    private static final float[] RING_X = new float[MAX_POINTS];
    private static final float[] RING_Y = new float[MAX_POINTS];
    private static final float[] RING_U = new float[MAX_POINTS];
    private static final float[] RING_V = new float[MAX_POINTS];
    private static final float[] PREV_X = new float[MAX_POINTS];
    private static final float[] PREV_Y = new float[MAX_POINTS];
    private static final float[] PREV_U = new float[MAX_POINTS];
    private static final float[] PREV_V = new float[MAX_POINTS];


    private static final int[] GLYPH_CODE = new int[MAX_GLYPHS];
    private static final Style[] GLYPH_STYLE = new Style[MAX_GLYPHS];
    private static final Cursor CURSOR = new Cursor();
    private static final Lens LENS = new Lens();

    private static int points;
    private static int glyphs;
    private static boolean smoothing;
    private static boolean rawScale;
    private static boolean floating;
    private static boolean dissolving;
    private static boolean fading;
    private static float fadeFrom;
    private static float fadeTo;
    private static float fadeWidth;

    private static boolean shaded;
    private static int shadeTop;
    private static int shadeBottom;
    private static float shadeOrigin;
    private static float shadeSpan;

    static {
        for (int i = 0; i <= ARC_TABLE; i++) {
            double theta = Math.toRadians(i * (90.0 / ARC_TABLE));
            CIRCLE_X[i] = (float) Math.cos(theta);
            CIRCLE_Y[i] = (float) Math.sin(theta);
        }
        rebakeSquircle();
        rebakeSwitchShape();
    }

    static void rebakeSquircle() {
        bakeCorner(UiGlassStyle.squircle(), SQUIRCLE_X, SQUIRCLE_Y);
    }

    static void rebakeSwitchShape() {
        bakeCorner(UiGlassStyle.switchSquircle(), SWITCH_X, SWITCH_Y);
    }

    private static void bakeCorner(float shape, float[] outX, float[] outY) {
        double[] curveX = new double[SQUIRCLE_SAMPLES + 1];
        double[] curveY = new double[SQUIRCLE_SAMPLES + 1];
        double[] travelled = new double[SQUIRCLE_SAMPLES + 1];
        double power = 2.0 / shape;

        for (int i = 0; i <= SQUIRCLE_SAMPLES; i++) {
            double theta = Math.PI / 2.0 * i / SQUIRCLE_SAMPLES;
            curveX[i] = Math.pow(Math.cos(theta), power);
            curveY[i] = Math.pow(Math.sin(theta), power);
            travelled[i] = i == 0 ? 0.0
                    : travelled[i - 1] + Math.hypot(curveX[i] - curveX[i - 1], curveY[i] - curveY[i - 1]);
        }

        double length = travelled[SQUIRCLE_SAMPLES];
        int sample = 0;
        for (int i = 0; i <= ARC_TABLE; i++) {
            double target = length * i / ARC_TABLE;
            while (sample < SQUIRCLE_SAMPLES - 1 && travelled[sample + 1] < target) sample++;
            double span = travelled[sample + 1] - travelled[sample];
            double t = span <= 0.0 ? 0.0 : (target - travelled[sample]) / span;
            outX[i] = (float) (curveX[sample] + (curveX[sample + 1] - curveX[sample]) * t);
            outY[i] = (float) (curveY[sample] + (curveY[sample + 1] - curveY[sample]) * t);
        }
        outX[0] = 1.0f;
        outY[0] = 0.0f;
        outX[ARC_TABLE] = 0.0f;
        outY[ARC_TABLE] = 1.0f;
    }

    private UiRender() {}

    public static void panel(GuiGraphics graphics, float x, float y, float width, float height, float radius, int color) {
        if (width <= 0.0f || height <= 0.0f || (color >>> 24) == 0) return;
        float pixels = pixelsPerUnit(graphics);
        roundedRect(x, y, width, height, radius, pixels);
        fillPath(graphics, color, feather(pixels));
    }

    public static void panelShaded(GuiGraphics graphics, float x, float y, float width, float height, float radius, int top, int bottom) {
        if (width <= 0.0f || height <= 0.0f || ((top >>> 24) == 0 && (bottom >>> 24) == 0)) return;
        shaded = true;
        shadeTop = top;
        shadeBottom = bottom;
        shadeOrigin = y;
        shadeSpan = height;
        try {
            panel(graphics, x, y, width, height, radius, top);
        } finally {
            shaded = false;
        }
    }

    public static void refracted(GuiGraphics graphics, float x, float y, float width, float height, float radius,
                                 int blurTexture, int sharpTexture, float alpha) {
        if (width <= 0.0f || height <= 0.0f || blurTexture == 0) return;

        float pixels = pixelsPerUnit(graphics);
        if (!LENS.prepare(graphics, x, y, width, height, radius, pixels, alpha)) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(UiLens.splits() && UiQuality.refracting()
                ? UiLens.prepared() : GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, blurTexture);

        lensPass();

        RenderSystem.enableCull();
        standardBlend();
    }

    private static void lensPass() {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);

        LENS.ring(0);
        LENS.keep();
        lensFeather(builder);

        for (int ring = 1; ring <= LENS.rings; ring++) {
            LENS.ring(ring);
            lensStrip(builder);
            LENS.keep();
        }
        lensFan(builder);

        Tesselator.getInstance().end();
    }

    private static void lensFeather(BufferBuilder builder) {
        float weight = LENS.prevAlpha;
        float reach = skirtReach();
        int rings = skirtRings();

        for (int i = 0; i < LENS.count; i++) {
            int j = i + 1 == LENS.count ? 0 : i + 1;
            float spanI = LENS.edge + reach * chaosAt(i, LENS.count);
            float spanJ = LENS.edge + reach * chaosAt(j, LENS.count);
            lensSkirt(builder, i, j, spanI, spanJ, weight, rings);
        }
    }

    private static void lensSkirt(BufferBuilder builder, int i, int j, float spanI, float spanJ,
                                  float weight, int rings) {
        float backI = 0.0f;
        float backJ = 0.0f;
        float backAlpha = weight;

        for (int ring = 1; ring <= rings; ring++) {
            float travel = ring / (float) rings;
            float frontI = spanI * travel;
            float frontJ = spanJ * travel;
            float frontAlpha = weight * skirtAlpha(travel, rings);

            float bix = PREV_X[i] + NORMAL_X[i] * backI;
            float biy = PREV_Y[i] + NORMAL_Y[i] * backI;
            float bjx = PREV_X[j] + NORMAL_X[j] * backJ;
            float bjy = PREV_Y[j] + NORMAL_Y[j] * backJ;
            float fix = PREV_X[i] + NORMAL_X[i] * frontI;
            float fiy = PREV_Y[i] + NORMAL_Y[i] * frontI;
            float fjx = PREV_X[j] + NORMAL_X[j] * frontJ;
            float fjy = PREV_Y[j] + NORMAL_Y[j] * frontJ;

            LENS.vertex(builder, fix, fiy, PREV_U[i], PREV_V[i], frontAlpha);
            LENS.vertex(builder, fjx, fjy, PREV_U[j], PREV_V[j], frontAlpha);
            LENS.vertex(builder, bjx, bjy, PREV_U[j], PREV_V[j], backAlpha);

            LENS.vertex(builder, fix, fiy, PREV_U[i], PREV_V[i], frontAlpha);
            LENS.vertex(builder, bjx, bjy, PREV_U[j], PREV_V[j], backAlpha);
            LENS.vertex(builder, bix, biy, PREV_U[i], PREV_V[i], backAlpha);

            backI = frontI;
            backJ = frontJ;
            backAlpha = frontAlpha;
        }
    }

    private static void lensStrip(BufferBuilder builder) {
        float back = LENS.prevAlpha;
        float front = LENS.ringAlpha;
        for (int i = 0; i < LENS.count; i++) {
            int j = i + 1 == LENS.count ? 0 : i + 1;

            LENS.vertex(builder, PREV_X[i], PREV_Y[i], PREV_U[i], PREV_V[i], back);
            LENS.vertex(builder, PREV_X[j], PREV_Y[j], PREV_U[j], PREV_V[j], back);
            LENS.vertex(builder, RING_X[j], RING_Y[j], RING_U[j], RING_V[j], front);

            LENS.vertex(builder, PREV_X[i], PREV_Y[i], PREV_U[i], PREV_V[i], back);
            LENS.vertex(builder, RING_X[j], RING_Y[j], RING_U[j], RING_V[j], front);
            LENS.vertex(builder, RING_X[i], RING_Y[i], RING_U[i], RING_V[i], front);
        }
    }

    private static void lensFan(BufferBuilder builder) {
        float weight = LENS.prevAlpha;
        for (int i = 0; i < LENS.count; i++) {
            int j = i + 1 == LENS.count ? 0 : i + 1;
            LENS.vertex(builder, LENS.centerX, LENS.centerY, LENS.centerU, LENS.centerV, weight);
            LENS.vertex(builder, PREV_X[j], PREV_Y[j], PREV_U[j], PREV_V[j], weight);
            LENS.vertex(builder, PREV_X[i], PREV_Y[i], PREV_U[i], PREV_V[i], weight);
        }
    }

    private static float lensDepth(int ring, int edgeRings, int rings, float band, float reach) {
        if (ring <= edgeRings) {
            float t = ring / (float) edgeRings;
            return Math.min(reach, band * t * t * (3.0f - 2.0f * t));
        }
        float t = (ring - edgeRings) / (float) Math.max(1, rings - edgeRings);
        return Math.min(reach, band + (reach - band) * t);
    }

    private static float lensPull(float depth, float band, float strength) {
        if (!UiQuality.refracting() || depth >= band || band <= 0.01f) return 0.0f;
        float t = depth / band;
        return strength * (float) Math.pow(1.0f - t, UiGlassStyle.falloff()) * UiGlassStyle.bendScale();
    }

    private static float lensZoom(float depth, float reach) {
        if (!UiQuality.refracting() || reach <= 0.01f) return 0.0f;
        float t = Mth.clamp(depth / reach, 0.0f, 1.0f);
        return UiGlassStyle.zoom() * t * t * (3.0f - 2.0f * t);
    }

    private static int lensRingsFor(float smallestPixels) {
        if (!UiQuality.refracting()) return FLAT_RINGS;
        int wanted = UiGlassStyle.rings();
        if (smallestPixels >= 110.0f) return wanted;
        if (smallestPixels >= 55.0f) return Math.max(3, wanted - 1);
        return Math.max(3, wanted - 2);
    }

    private static void unitNormals(int count) {
        for (int i = 0; i < count; i++) {
            float mx = MITER_X[i];
            float my = MITER_Y[i];
            float len = (float) Math.sqrt(mx * mx + my * my);
            if (len < 1.0E-5f) {
                NORMAL_X[i] = 0.0f;
                NORMAL_Y[i] = 0.0f;
            } else {
                NORMAL_X[i] = mx / len;
                NORMAL_Y[i] = my / len;
            }
        }
    }

    public static void rim(GuiGraphics graphics, float x, float y, float width, float height, float radius, float thickness, int color) {
        if (width <= 0.0f || height <= 0.0f || (color >>> 24) == 0) return;

        int count = contour(graphics, x, y, width, height, radius);
        if (count == 0) return;

        specularWeights(count);
        strokeContour(graphics, count, Math.max(thickness, feather(pixelsPerUnit(graphics))) * 0.5f, color);
    }

    public static void sheen(GuiGraphics graphics, float x, float y, float width, float height, float radius,
                             float depth, int color, float dirX, float dirY, float power) {
        if (width <= 0.0f || height <= 0.0f || (color >>> 24) == 0 || depth <= 0.0f) return;

        int count = contour(graphics, x, y, width, height, radius);
        if (count == 0) return;

        directionalWeights(count, dirX, dirY, power);
        glazeContour(graphics, count, glazeDepth(depth, width, height, radius), color, feather(pixelsPerUnit(graphics)));
    }

    private static float glazeDepth(float depth, float width, float height, float radius) {
        float corner = Math.max(1.0f, Math.min(radius, Math.min(width, height) / 2.0f));
        return Math.min(depth, Math.min(corner * CORNER_SHARE, Math.min(width, height) * 0.28f));
    }

    public static void sheenSweep(GuiGraphics graphics, float x, float y, float width, float height, float radius,
                                  float depth, int color, float angle, float power) {
        sheen(graphics, x, y, width, height, radius, depth, color,
                (float) Math.cos(angle), (float) Math.sin(angle), power);
    }

    private static int contour(GuiGraphics graphics, float x, float y, float width, float height, float radius) {
        roundedRect(x, y, width, height, radius, pixelsPerUnit(graphics));

        int count = closedCount();
        if (count < 3) return 0;

        miters(count, x + width / 2.0f, y + height / 2.0f);
        unitNormals(count);
        return count;
    }

    private static void specularWeights(int count) {
        for (int i = 0; i < count; i++) {
            RIM_WEIGHT[i] = 1.0f;
        }
    }

    private static void directionalWeights(int count, float dirX, float dirY, float power) {
        float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (length < 1.0E-5f) return;

        for (int i = 0; i < count; i++) {
            float facing = (NORMAL_X[i] * dirX + NORMAL_Y[i] * dirY) / length;
            RIM_WEIGHT[i] = facing <= 0.0f ? 0.0f : (float) Math.pow(facing, power);
        }
    }

    private static void strokeContour(GuiGraphics graphics, int count, float half, int color) {
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();

        beginBatch();
        for (int i = 0; i < count; i++) {
            strokeSegment(builder, matrix, i, i + 1 == count ? 0 : i + 1, half, color);
        }
        endBatch();
    }

    private static void strokeSegment(BufferBuilder builder, Matrix4f matrix, int i, int j, float half, int color) {
        float ox = PATH_X[i] + MITER_X[i] * half;
        float oy = PATH_Y[i] + MITER_Y[i] * half;
        float oxj = PATH_X[j] + MITER_X[j] * half;
        float oyj = PATH_Y[j] + MITER_Y[j] * half;
        float ix = PATH_X[i] - MITER_X[i] * half;
        float iy = PATH_Y[i] - MITER_Y[i] * half;
        float ixj = PATH_X[j] - MITER_X[j] * half;
        float iyj = PATH_Y[j] - MITER_Y[j] * half;

        strokeFlanks(builder, matrix, i, j, ox, oy, oxj, oyj, ix, iy, ixj, iyj, color);
    }

    private static void strokeFlanks(BufferBuilder builder, Matrix4f matrix, int i, int j,
                                     float ox, float oy, float oxj, float oyj,
                                     float ix, float iy, float ixj, float iyj, int color) {
        int ci = UiTheme.alpha(color, RIM_WEIGHT[i]);
        int cj = UiTheme.alpha(color, RIM_WEIGHT[j]);
        int fade = color & 0x00FFFFFF;

        colored(builder, matrix, ox, oy, fade);
        colored(builder, matrix, oxj, oyj, fade);
        colored(builder, matrix, PATH_X[j], PATH_Y[j], cj);

        colored(builder, matrix, ox, oy, fade);
        colored(builder, matrix, PATH_X[j], PATH_Y[j], cj);
        colored(builder, matrix, PATH_X[i], PATH_Y[i], ci);

        colored(builder, matrix, PATH_X[i], PATH_Y[i], ci);
        colored(builder, matrix, PATH_X[j], PATH_Y[j], cj);
        colored(builder, matrix, ixj, iyj, fade);

        colored(builder, matrix, PATH_X[i], PATH_Y[i], ci);
        colored(builder, matrix, ixj, iyj, fade);
        colored(builder, matrix, ix, iy, fade);
    }

    private static void glazeContour(GuiGraphics graphics, int count, float depth, int color, float inset) {
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();

        beginBatch();
        for (int i = 0; i < count; i++) {
            glazeSegment(builder, matrix, i, i + 1 == count ? 0 : i + 1, depth, color, inset);
        }
        endBatch();
    }

    private static void glazeSegment(BufferBuilder builder, Matrix4f matrix, int i, int j, float depth, int color, float inset) {
        if (RIM_WEIGHT[i] <= 0.002f && RIM_WEIGHT[j] <= 0.002f) return;

        float ox = PATH_X[i] - MITER_X[i] * inset;
        float oy = PATH_Y[i] - MITER_Y[i] * inset;
        float oxj = PATH_X[j] - MITER_X[j] * inset;
        float oyj = PATH_Y[j] - MITER_Y[j] * inset;
        float ix = ox - MITER_X[i] * depth;
        float iy = oy - MITER_Y[i] * depth;
        float ixj = oxj - MITER_X[j] * depth;
        float iyj = oyj - MITER_Y[j] * depth;

        int ci = UiTheme.alpha(color, RIM_WEIGHT[i]);
        int cj = UiTheme.alpha(color, RIM_WEIGHT[j]);
        int fade = color & 0x00FFFFFF;

        colored(builder, matrix, ox, oy, ci);
        colored(builder, matrix, oxj, oyj, cj);
        colored(builder, matrix, ixj, iyj, fade);

        colored(builder, matrix, ox, oy, ci);
        colored(builder, matrix, ixj, iyj, fade);
        colored(builder, matrix, ix, iy, fade);
    }

    public static void rect(GuiGraphics graphics, float x, float y, float width, float height, int color) {
        panel(graphics, x, y, width, height, 0.0f, color);
    }

    public static void dot(GuiGraphics graphics, float centerX, float centerY, float radius, int color) {
        panel(graphics, centerX - radius, centerY - radius, radius * 2.0f, radius * 2.0f, radius, color);
    }

    // WHY: кисть кладёт картинку на физическую сетку экрана. Ванильный blit берёт целые единицы,
    // WHY: а под своим масштабом единица не равна пикселю, и пиксельная графика едет рваными рядами
    public static void image(GuiGraphics graphics, ResourceLocation texture, float x, float y,
                             float width, float height, float alpha) {
        if (width <= 0.0f || height <= 0.0f || alpha <= 0.0f) return;

        Matrix4f matrix = graphics.pose().last().pose();
        boolean upright = Math.abs(matrix.m01()) < 1.0E-4f && matrix.m00() > 1.0E-4f;
        double gui = Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());

        float left = upright ? snap(x, matrix.m00(), matrix.m30(), gui) : x;
        float top = upright ? snap(y, matrix.m11(), matrix.m31(), gui) : y;
        float right = upright ? snap(x + width, matrix.m00(), matrix.m30(), gui) : x + width;
        float bottom = upright ? snap(y + height, matrix.m11(), matrix.m31(), gui) : y + height;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        textured(builder, matrix, left, bottom, 0.0f, 1.0f, alpha);
        textured(builder, matrix, right, bottom, 1.0f, 1.0f, alpha);
        textured(builder, matrix, right, top, 1.0f, 0.0f, alpha);
        textured(builder, matrix, left, top, 0.0f, 0.0f, alpha);
        Tesselator.getInstance().end();

        RenderSystem.enableCull();
        standardBlend();
    }

    private static void textured(BufferBuilder builder, Matrix4f matrix, float x, float y,
                                 float u, float v, float alpha) {
        builder.vertex(matrix, x, y, 0.0f).uv(u, v).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
    }

    public static void triangle(GuiGraphics graphics, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        if ((color >>> 24) == 0) return;
        float pixels = pixelsPerUnit(graphics);
        reset();
        push(x1, y1);
        push(x2, y2);
        push(x3, y3);
        fillPath(graphics, color, feather(pixels));
    }

    public static void polygon(GuiGraphics graphics, float[] xs, float[] ys, int count,
                               float pivotX, float pivotY, int color) {
        if (count < 3 || (color >>> 24) == 0) return;

        reset();
        for (int point = 0; point < count; point++) {
            push(xs[point], ys[point]);
        }
        fillPathAround(graphics, color, feather(pixelsPerUnit(graphics)), pivotX, pivotY);
    }

    public static void arrow(GuiGraphics graphics, float centerX, float centerY, float yRot, float size, int color, int outline) {
        float pixels = pixelsPerUnit(graphics);
        float edge = feather(pixels);
        double angle = Math.toRadians(yRot);
        float dirX = (float) -Math.sin(angle);
        float dirY = (float) Math.cos(angle);

        if ((outline >>> 24) != 0) {
            dart(centerX, centerY, dirX, dirY, size + Math.max(0.5f, 1.5f / Math.max(pixels, 0.05f)));
            fillPath(graphics, outline, edge);
        }
        dart(centerX, centerY, dirX, dirY, size);
        fillPath(graphics, color, edge);
    }

    public static void line(GuiGraphics graphics, float x1, float y1, float x2, float y2, float width, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001f) return;

        float nx = -dy / length * width * 0.5f;
        float ny = dx / length * width * 0.5f;

        reset();
        push(x1 + nx, y1 + ny);
        push(x2 + nx, y2 + ny);
        push(x2 - nx, y2 - ny);
        push(x1 - nx, y1 - ny);
        fillPath(graphics, color, feather(pixelsPerUnit(graphics)));
    }

    public static float screenX(GuiGraphics graphics, float x, float y) {
        Matrix4f matrix = graphics.pose().last().pose();
        return matrix.m00() * x + matrix.m10() * y + matrix.m30();
    }

    public static float screenY(GuiGraphics graphics, float x, float y) {
        Matrix4f matrix = graphics.pose().last().pose();
        return matrix.m01() * x + matrix.m11() * y + matrix.m31();
    }

    public static void beginBatch() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        Tesselator.getInstance().getBuilder().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    public static void endBatch() {
        Tesselator.getInstance().end();
        RenderSystem.enableCull();
        standardBlend();
    }

    // WHY: голый disableBlend расходится с кешем BlendMode, и следующий шейдер с тем же узлом blend
    // WHY: не применяет ничего и рисуется без блендинга, выбивая нулевой альфой дырку в покрытии
    public static void standardBlend() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    // WHY: композит интерфейса это не геометрия мира: глубину он не пишет и по чужой не режется.
    // WHY: мировой квад ухода лежит в перспективе своей плоскости, его глубина не сравнима ни с кадром,
    // WHY: ни с соседним уходом, и нарисованный раньше вырезал из следующего всю свою область.
    // WHY: снимать глубину до GuiGraphics.flush бесполезно: ваниль включает тест обратно в нём
    public static void ignoreDepth() {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
    }

    public static void resumeDepth() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    public static void gradient(GuiGraphics graphics, float x, float y, float width, float height, int top, int bottom) {
        gradientQuad(graphics, x, y, width, height, top, top, bottom, bottom);
    }

    public static void gradientAcross(GuiGraphics graphics, float x, float y, float width, float height,
                                      int left, int right) {
        gradientQuad(graphics, x, y, width, height, left, right, right, left);
    }

    public static void iconHeart(GuiGraphics graphics, float centerX, float centerY, float size, int color) {
        float scaleX = size * 0.5f / 16.0f;
        float scaleY = size * 0.47f / 14.4f;

        reset();
        int segments = 30;
        for (int i = 0; i < segments; i++) {
            double t = Math.PI * 2.0 * i / segments;
            double sin = Math.sin(t);
            double curveX = sin * sin * sin * 16.0;
            double curveY = 13.0 * Math.cos(t) - 5.0 * Math.cos(2.0 * t) - 2.0 * Math.cos(3.0 * t) - Math.cos(4.0 * t) - 2.6;
            push(centerX + (float) curveX * scaleX, centerY - (float) curveY * scaleY);
        }
        fillPathAround(graphics, color, feather(pixelsPerUnit(graphics)), centerX, centerY + size * 0.14f);
    }

    public static void iconShield(GuiGraphics graphics, float centerX, float centerY, float size, int color) {
        float half = size * 0.42f;
        float top = -size * 0.44f;
        float corner = size * 0.15f;
        float waist = size * 0.02f;
        float tip = size * 0.48f;

        reset();
        push(centerX, centerY + top);
        push(centerX + half - corner, centerY + top);
        bezier(centerX + half - corner, centerY + top, centerX + half, centerY + top, centerX + half, centerY + top + corner, 4);
        push(centerX + half, centerY + waist);
        bezier(centerX + half, centerY + waist, centerX + half * 0.94f, centerY + tip * 0.66f, centerX, centerY + tip, 8);
        bezier(centerX, centerY + tip, centerX - half * 0.94f, centerY + tip * 0.66f, centerX - half, centerY + waist, 8);
        push(centerX - half, centerY + top + corner);
        bezier(centerX - half, centerY + top + corner, centerX - half, centerY + top, centerX - half + corner, centerY + top, 4);
        fillPath(graphics, color, feather(pixelsPerUnit(graphics)));
    }

    public static void iconFood(GuiGraphics graphics, float centerX, float centerY, float size, int color) {
        capsule(graphics,
                centerX - size * 0.19f, centerY + size * 0.19f, size * 0.33f,
                centerX + size * 0.19f, centerY - size * 0.19f, size * 0.085f,
                color);
        dot(graphics, centerX + size * 0.29f, centerY - size * 0.29f, size * 0.155f, color);
    }

    public static void iconBubble(GuiGraphics graphics, float centerX, float centerY, float size, int color, int inner) {
        dot(graphics, centerX, centerY, size * 0.44f, color);
        dot(graphics, centerX, centerY, size * 0.23f, inner);
    }

    public static void check(GuiGraphics graphics, float centerX, float centerY, float size, int color) {
        float thickness = Math.max(size * 0.16f, 0.6f);
        float kneeX = centerX - size * 0.10f;
        float kneeY = centerY + size * 0.34f;
        capsule(graphics, centerX - size * 0.46f, centerY + size * 0.02f, thickness, kneeX, kneeY, thickness, color);
        capsule(graphics, kneeX, kneeY, thickness, centerX + size * 0.48f, centerY - size * 0.36f, thickness, color);
    }

    public static void iconLock(GuiGraphics graphics, float centerX, float centerY, float size, boolean locked, int color) {
        float bodyWidth = size * 0.62f;
        float bodyHeight = size * 0.46f;
        float shackle = size * 0.21f;
        float gap = locked ? size * 0.02f : size * 0.10f;
        float shift = locked ? 0.0f : size * 0.16f;

        float left = Math.min(-bodyWidth / 2.0f, shift - shackle);
        float right = Math.max(bodyWidth / 2.0f, shift + shackle);
        float originX = centerX - (left + right) / 2.0f;
        float bodyTop = centerY - (bodyHeight - gap - shackle) / 2.0f;

        panel(graphics, originX - bodyWidth / 2.0f, bodyTop, bodyWidth, bodyHeight, size * 0.12f, color);
        lockShackle(graphics, originX + shift, bodyTop - gap, shackle,
                Math.max(size * 0.11f, 0.7f), color);
    }

    private static void lockShackle(GuiGraphics graphics, float centerX, float centerY, float radius,
                                    float thickness, int color) {
        reset();
        for (int i = 0; i <= SHACKLE_STEPS; i++) {
            double angle = Math.PI * i / SHACKLE_STEPS;
            push(centerX - (float) Math.cos(angle) * radius, centerY - (float) Math.sin(angle) * radius);
        }
        for (int i = SHACKLE_STEPS; i >= 0; i--) {
            double angle = Math.PI * i / SHACKLE_STEPS;
            float inner = radius - thickness;
            push(centerX - (float) Math.cos(angle) * inner, centerY - (float) Math.sin(angle) * inner);
        }
        fillPath(graphics, color, feather(pixelsPerUnit(graphics)));
    }

    public static void iconMic(GuiGraphics graphics, float centerX, float centerY, float size, int color) {
        float capsuleWidth = size * 0.17f;
        float top = centerY - size * 0.40f;
        float bottom = centerY + size * 0.02f;
        capsule(graphics, centerX, top, capsuleWidth, centerX, bottom, capsuleWidth, color);

        float cradle = size * 0.30f;
        float thickness = Math.max(size * 0.075f, 0.6f);
        reset();
        for (int i = 0; i <= 12; i++) {
            double angle = Math.PI * i / 12.0;
            push(centerX - (float) Math.cos(angle) * cradle, centerY + size * 0.06f + (float) Math.sin(angle) * cradle);
        }
        for (int i = 12; i >= 0; i--) {
            double angle = Math.PI * i / 12.0;
            float inner = cradle - thickness;
            push(centerX - (float) Math.cos(angle) * inner, centerY + size * 0.06f + (float) Math.sin(angle) * inner);
        }
        fillPath(graphics, color, feather(pixelsPerUnit(graphics)));

        capsule(graphics, centerX, centerY + size * 0.36f, thickness * 0.5f,
                centerX, centerY + size * 0.46f, thickness * 0.5f, color);
    }

    private static final int[] PALETTE_HUES = {
            0xFFE0524A, 0xFFE8A33D, 0xFFE6D34A, 0xFF4FC97A, 0xFF4F9BE8, 0xFFA96BE0
    };

    public static void iconPalette(GuiGraphics graphics, float centerX, float centerY, float size, float alpha) {
        float outer = size * 0.5f;
        float inner = size * 0.22f;
        float gap = 0.010f;
        float slice = 1.0f / PALETTE_HUES.length;

        for (int hue = 0; hue < PALETTE_HUES.length; hue++) {
            wedge(graphics, centerX, centerY, inner, outer, hue * slice + gap, (hue + 1) * slice - gap,
                    UiTheme.withAlpha(PALETTE_HUES[hue], alpha));
        }
        dot(graphics, centerX, centerY, inner * 0.62f, UiTheme.withAlpha(UiAccent.color(), alpha));
    }

    public static void iconBolt(GuiGraphics graphics, float centerX, float centerY, float size, int color) {
        float half = size * 0.46f;
        float wide = size * 0.26f;
        float waist = size * 0.06f;

        reset();
        push(centerX + wide * 0.55f, centerY - half);
        push(centerX - wide, centerY + waist);
        push(centerX - waist * 0.2f, centerY + waist);
        push(centerX - wide * 0.55f, centerY + half);
        push(centerX + wide, centerY - waist);
        push(centerX + waist * 0.2f, centerY - waist);
        fillPath(graphics, color, feather(pixelsPerUnit(graphics)));
    }

    private static void capsulePath(float ax, float ay, float ar, float bx, float by, float br, float distance) {
        double axis = Math.atan2(by - ay, bx - ax);
        double spread = Math.acos(Mth.clamp((ar - br) / distance, -1.0f, 1.0f));
        int segments = 12;

        reset();
        for (int i = 0; i <= segments; i++) {
            double angle = axis + spread + (Math.PI * 2.0 - spread * 2.0) * i / segments;
            push(ax + (float) Math.cos(angle) * ar, ay + (float) Math.sin(angle) * ar);
        }
        for (int i = 0; i <= segments; i++) {
            double angle = axis - spread + spread * 2.0 * i / segments;
            push(bx + (float) Math.cos(angle) * br, by + (float) Math.sin(angle) * br);
        }
    }

    public static void ring(GuiGraphics graphics, float centerX, float centerY, float radius, float thickness,
                            float progress, int color) {
        float swept = UiAnim.clamp01(progress);
        if (swept <= 0.0f || radius <= 0.0f || thickness <= 0.0f || (color >>> 24) == 0) return;

        float pixels = pixelsPerUnit(graphics);
        float edge = feather(pixels);
        float inner = Math.max(0.0f, radius - thickness * 0.5f);
        float outer = radius + thickness * 0.5f;
        int segments = arcSegments(radius * pixels, swept);

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();

        beginBatch();
        arcStrip(builder, matrix, centerX, centerY, inner, outer, swept, segments, color, edge);
        endBatch();
    }

    public static void wedge(GuiGraphics graphics, float centerX, float centerY, float inner, float outer,
                             float from, float to, int color) {
        if (outer <= inner || (color >>> 24) == 0) return;

        int steps = wedgeSteps(outer * pixelsPerUnit(graphics), to - from);
        int count = 0;
        for (int step = 0; step <= steps; step++) {
            double angle = arcAngle(from, to, step / (float) steps);
            WEDGE_X[count] = centerX + (float) Math.cos(angle) * outer;
            WEDGE_Y[count] = centerY + (float) Math.sin(angle) * outer;
            count++;
        }
        for (int step = steps; step >= 0; step--) {
            double angle = arcAngle(from, to, step / (float) steps);
            WEDGE_X[count] = centerX + (float) Math.cos(angle) * inner;
            WEDGE_Y[count] = centerY + (float) Math.sin(angle) * inner;
            count++;
        }

        double middle = arcAngle(from, to, 0.5f);
        float pivot = (inner + outer) * 0.5f;
        polygon(graphics, WEDGE_X, WEDGE_Y, count,
                centerX + (float) Math.cos(middle) * pivot, centerY + (float) Math.sin(middle) * pivot, color);
    }

    private static double arcAngle(float from, float to, float travel) {
        return -Math.PI / 2.0 + (from + (to - from) * travel) * Math.PI * 2.0;
    }

    private static int wedgeSteps(float outerPixels, float swept) {
        int wanted = Math.round(Math.abs(swept) * (float) Math.PI * 2.0f * outerPixels / WEDGE_PIXELS);
        return Mth.clamp(wanted, 6, MAX_WEDGE_STEPS);
    }

    private static int arcSegments(float radiusPixels, float swept) {
        int wanted = Math.round((float) (Math.PI * 2.0 * radiusPixels * swept) / ARC_PIXELS);
        return Mth.clamp(wanted, 4, Math.max(4, Math.round(ARC_SEGMENTS * swept)));
    }

    private static void arcStrip(BufferBuilder builder, Matrix4f matrix, float centerX, float centerY,
                                 float inner, float outer, float swept, int segments, int color, float edge) {
        int fade = color & 0x00FFFFFF;
        double sweep = Math.PI * 2.0 * swept;

        for (int i = 0; i < segments; i++) {
            double from = -Math.PI / 2.0 + sweep * i / segments;
            double to = -Math.PI / 2.0 + sweep * (i + 1) / segments;
            float cosFrom = (float) Math.cos(from);
            float sinFrom = (float) Math.sin(from);
            float cosTo = (float) Math.cos(to);
            float sinTo = (float) Math.sin(to);

            arcQuad(builder, matrix, centerX, centerY, cosFrom, sinFrom, cosTo, sinTo, inner, outer, color, color);
            arcQuad(builder, matrix, centerX, centerY, cosFrom, sinFrom, cosTo, sinTo, outer, outer + edge, color, fade);
            arcQuad(builder, matrix, centerX, centerY, cosFrom, sinFrom, cosTo, sinTo, Math.max(0.0f, inner - edge), inner, fade, color);
        }
    }

    private static void arcQuad(BufferBuilder builder, Matrix4f matrix, float centerX, float centerY,
                                float cosFrom, float sinFrom, float cosTo, float sinTo,
                                float near, float far, int nearColor, int farColor) {
        colored(builder, matrix, centerX + cosFrom * near, centerY + sinFrom * near, nearColor);
        colored(builder, matrix, centerX + cosTo * near, centerY + sinTo * near, nearColor);
        colored(builder, matrix, centerX + cosTo * far, centerY + sinTo * far, farColor);

        colored(builder, matrix, centerX + cosFrom * near, centerY + sinFrom * near, nearColor);
        colored(builder, matrix, centerX + cosTo * far, centerY + sinTo * far, farColor);
        colored(builder, matrix, centerX + cosFrom * far, centerY + sinFrom * far, farColor);
    }

    private static void capsule(GuiGraphics graphics, float x1, float y1, float r1, float x2, float y2, float r2, int color) {
        if ((color >>> 24) == 0) return;

        boolean swap = r2 > r1;
        float ax = swap ? x2 : x1;
        float ay = swap ? y2 : y1;
        float ar = swap ? r2 : r1;
        float bx = swap ? x1 : x2;
        float by = swap ? y1 : y2;
        float br = swap ? r1 : r2;

        float distance = (float) Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay));
        if (distance <= ar - br) {
            dot(graphics, ax, ay, ar, color);
            return;
        }

        capsulePath(ax, ay, ar, bx, by, br, distance);
        fillPath(graphics, color, feather(pixelsPerUnit(graphics)));
    }

    public static void trace(GuiGraphics graphics, float[] xs, float[] ys, float[] weights, int count,
                             float width, int color) {
        if (count < 2 || width <= 0.0f || (color >>> 24) == 0) return;

        float edge = feather(pixelsPerUnit(graphics));
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();

        beginBatch();
        for (int i = 0; i + 1 < count; i++) {
            traceSegment(builder, matrix, xs, ys, weights, i, width * 0.5f, edge, color);
        }
        for (int i = 1; i + 1 < count; i++) {
            if (!bends(xs, ys, i)) continue;

            traceJoin(builder, matrix, xs[i], ys[i], width * 0.5f, edge, UiTheme.alpha(color, weights[i]));
        }
        endBatch();
    }

    private static boolean bends(float[] xs, float[] ys, int index) {
        float backX = xs[index] - xs[index - 1];
        float backY = ys[index] - ys[index - 1];
        float aheadX = xs[index + 1] - xs[index];
        float aheadY = ys[index + 1] - ys[index];

        float backRun = (float) Math.sqrt(backX * backX + backY * backY);
        float aheadRun = (float) Math.sqrt(aheadX * aheadX + aheadY * aheadY);
        if (backRun < TRACE_MIN_STEP || aheadRun < TRACE_MIN_STEP) return false;

        return (backX * aheadX + backY * aheadY) / (backRun * aheadRun) < TRACE_JOIN_COSINE;
    }

    private static void traceSegment(BufferBuilder builder, Matrix4f matrix, float[] xs, float[] ys, float[] weights,
                                     int index, float half, float edge, int color) {
        int next = index + 1;
        float runX = xs[next] - xs[index];
        float runY = ys[next] - ys[index];
        float run = (float) Math.sqrt(runX * runX + runY * runY);
        if (run < TRACE_MIN_STEP) return;

        float nx = -runY / run;
        float ny = runX / run;

        int from = UiTheme.alpha(color, weights[index]);
        int to = UiTheme.alpha(color, weights[next]);
        int fadeFrom = from & 0x00FFFFFF;
        int fadeTo = to & 0x00FFFFFF;

        traceQuad(builder, matrix, xs[index], ys[index], xs[next], ys[next], nx, ny, nx, ny,
                -half, half, from, to);
        traceQuad(builder, matrix, xs[index], ys[index], xs[next], ys[next], nx, ny, nx, ny,
                half, half + edge, from, fadeTo, to, fadeFrom);
        traceQuad(builder, matrix, xs[index], ys[index], xs[next], ys[next], nx, ny, nx, ny,
                -half - edge, -half, fadeFrom, to, fadeTo, from);
    }

    private static void traceJoin(BufferBuilder builder, Matrix4f matrix, float centerX, float centerY,
                                  float half, float edge, int color) {
        int fade = color & 0x00FFFFFF;
        for (int i = 0; i < TRACE_JOIN_SEGMENTS; i++) {
            double from = Math.PI * 2.0 * i / TRACE_JOIN_SEGMENTS;
            double to = Math.PI * 2.0 * (i + 1) / TRACE_JOIN_SEGMENTS;
            float cosFrom = (float) Math.cos(from);
            float sinFrom = (float) Math.sin(from);
            float cosTo = (float) Math.cos(to);
            float sinTo = (float) Math.sin(to);

            arcQuad(builder, matrix, centerX, centerY, cosFrom, sinFrom, cosTo, sinTo, 0.0f, half, color, color);
            arcQuad(builder, matrix, centerX, centerY, cosFrom, sinFrom, cosTo, sinTo, half, half + edge, color, fade);
        }
    }

    private static void traceQuad(BufferBuilder builder, Matrix4f matrix, float x0, float y0, float x1, float y1,
                                  float nx0, float ny0, float nx1, float ny1, float near, float far,
                                  int colorFrom, int colorTo) {
        traceQuad(builder, matrix, x0, y0, x1, y1, nx0, ny0, nx1, ny1, near, far, colorFrom, colorTo, colorTo, colorFrom);
    }

    private static void traceQuad(BufferBuilder builder, Matrix4f matrix, float x0, float y0, float x1, float y1,
                                  float nx0, float ny0, float nx1, float ny1, float near, float far,
                                  int nearFrom, int farTo, int nearTo, int farFrom) {
        colored(builder, matrix, x0 + nx0 * near, y0 + ny0 * near, nearFrom);
        colored(builder, matrix, x1 + nx1 * near, y1 + ny1 * near, nearTo);
        colored(builder, matrix, x1 + nx1 * far, y1 + ny1 * far, farTo);

        colored(builder, matrix, x0 + nx0 * near, y0 + ny0 * near, nearFrom);
        colored(builder, matrix, x1 + nx1 * far, y1 + ny1 * far, farTo);
        colored(builder, matrix, x0 + nx0 * far, y0 + ny0 * far, farFrom);
    }

    public static Component styled(Component value) {
        return restyle(value, Style.EMPTY, BOLD[2], BOLD[2]);
    }

    public static float centerY(float boxY, float boxHeight, float scale) {
        return boxY + boxHeight / 2.0f - textCenter() * scale;
    }

    private static float textCenter() {
        return MsdfFontSets.ready() ? MSDF_TEXT_CENTER : TEXT_CENTER;
    }

    public static Component styledLabel(Component value) {
        return restyle(value, Style.EMPTY, BOLD[2], BOLD[2]);
    }

    public static float width(Font font, Component value) {
        UiFont.push(screenFace());
        try {
            return font.getSplitter().stringWidth(styled(value));
        } finally {
            UiFont.pop();
        }
    }

    public static float width(Font font, String value) {
        return width(font, Component.literal(value));
    }

    public static float widthLabel(Font font, Component value) {
        UiFont.push(screenFace());
        try {
            return font.getSplitter().stringWidth(styledLabel(value));
        } finally {
            UiFont.pop();
        }
    }

    public static float widthLabel(Font font, String value) {
        return widthLabel(font, Component.literal(value));
    }

    public static float crisp(GuiGraphics graphics, float scale) {
        return crisp(REGULAR_BAKE, pixelsPerUnit(graphics), scale);
    }

    public static float measure(GuiGraphics graphics, Font font, Component value, float scale) {
        float pixels = pixelsPerUnit(graphics);
        float snapped = crisp(REGULAR_BAKE, pixels, scale);
        layout(value, BOLD, BOLD, REGULAR_BAKE, pixels * snapped);
        return span(font, pixels, snapped, 0.0f);
    }

    public static float measureTracked(GuiGraphics graphics, Font font, Component value, float scale, float tracking) {
        return measured(graphics, font, value, BOLD, REGULAR_BAKE, scale, tracking);
    }

    public static float measureTitle(GuiGraphics graphics, Font font, Component value, float scale, float tracking) {
        return measured(graphics, font, value, TITLE, PLAIN_BAKE, scale, tracking);
    }

    // WHY: ширину строки считает то же перо, что и рисует её, иначе плашка под заголовком разъезжается с текстом
    private static float measured(GuiGraphics graphics, Font font, Component value, ResourceLocation[] faces,
                                  float[] bakes, float scale, float tracking) {
        if (value.getString().isEmpty()) return 0.0f;

        float pixels = pixelsPerUnit(graphics);
        float snapped = crisp(bakes, pixels, scale);
        layout(value, faces, faces, bakes, pixels * snapped);
        return span(font, pixels, snapped, tracking);
    }

    // WHY: перо кладёт каждый глиф на целый пиксель, поэтому измеренная ширина куска строки не совпадает
    // WHY: с местом букв в ней: подчёркивание слова берёт границы тем же ходом пера, что и отрисовка
    public static float[] trackedStops(GuiGraphics graphics, Font font, Component value, float scale, float tracking) {
        float pixels = pixelsPerUnit(graphics);
        if (pixels <= 0.01f || value.getString().isEmpty()) return new float[]{0.0f};

        float snapped = crisp(REGULAR_BAKE, pixels, scale);
        layout(value, BOLD, BOLD, REGULAR_BAKE, pixels * snapped);
        return stops(font, pixels * snapped, pixels, tracking);
    }

    // WHY: перо ставит после каждой буквы округлённый межбуквенный зазор, и без его вычета
    // WHY: подчёркивание слова заезжает в пробел за ним, что особенно заметно на коротких словах
    public static float trackedGap(GuiGraphics graphics, float scale, float tracking) {
        float pixels = pixelsPerUnit(graphics);
        if (pixels <= 0.01f) return 0.0f;

        return Math.round(tracking * pixels * crisp(REGULAR_BAKE, pixels, scale)) / pixels;
    }

    public static float along(float[] stops, float position) {
        float clamped = Math.max(0.0f, Math.min(stops.length - 1.0f, position));
        int low = (int) clamped;
        int high = Math.min(stops.length - 1, low + 1);
        return stops[low] + (stops[high] - stops[low]) * (clamped - low);
    }

    private static float[] stops(Font font, float em, float pixels, float tracking) {
        if (glyphs == 0) return new float[]{0.0f};

        UiFont.push(screenFace());
        try {
            float[] marks = new float[glyphs + 1];
            int pen = 0;
            for (int index = 0; index < glyphs; index++) {
                marks[index] = pen / pixels;
                pen += Math.max(0, Math.round((advance(font, index) + tracking) * em));
            }
            marks[glyphs] = (pen - Math.round(tracking * em)) / pixels;
            return marks;
        } finally {
            UiFont.pop();
        }
    }

    public static void textScaled(GuiGraphics graphics, Font font, Component value, float x, float y, float scale, int color, boolean shadow) {
        aligned(graphics, font, value, BOLD, x, y, scale, color, shadow, ALIGN_LEFT);
    }

    public static void textCentered(GuiGraphics graphics, Font font, Component value, float centerX, float y, float scale, int color, boolean shadow) {
        aligned(graphics, font, value, BOLD, centerX, y, scale, color, shadow, ALIGN_CENTER);
    }

    public static void textRight(GuiGraphics graphics, Font font, Component value, float rightX, float y, float scale, int color, boolean shadow) {
        aligned(graphics, font, value, BOLD, rightX, y, scale, color, shadow, ALIGN_RIGHT);
    }

    public static void textCentered(GuiGraphics graphics, Font font, String value, float centerX, float y, float scale, int color, boolean shadow) {
        textCentered(graphics, font, Component.literal(value), centerX, y, scale, color, shadow);
    }

    public static void textRight(GuiGraphics graphics, Font font, String value, float rightX, float y, float scale, int color, boolean shadow) {
        textRight(graphics, font, Component.literal(value), rightX, y, scale, color, shadow);
    }

    public static void labelScaled(GuiGraphics graphics, Font font, Component value, float x, float y, float scale, int color) {
        aligned(graphics, font, value, BOLD, x, y, scale, color, false, ALIGN_LEFT);
    }

    public static void labelCentered(GuiGraphics graphics, Font font, Component value, float centerX, float y, float scale, int color) {
        aligned(graphics, font, value, BOLD, centerX, y, scale, color, false, ALIGN_CENTER);
    }

    public static void labelCentered(GuiGraphics graphics, Font font, String value, float centerX, float y, float scale, int color) {
        labelCentered(graphics, font, Component.literal(value), centerX, y, scale, color);
    }

    public static void labelRight(GuiGraphics graphics, Font font, Component value, float rightX, float y, float scale, int color) {
        aligned(graphics, font, value, BOLD, rightX, y, scale, color, false, ALIGN_RIGHT);
    }

    public static void labelScaled(GuiGraphics graphics, Font font, String value, float x, float y, float scale, int color) {
        labelScaled(graphics, font, Component.literal(value), x, y, scale, color);
    }

    public static void labelRight(GuiGraphics graphics, Font font, String value, float rightX, float y, float scale, int color) {
        labelRight(graphics, font, Component.literal(value), rightX, y, scale, color);
    }

    public static void emphasisCentered(GuiGraphics graphics, Font font, Component value, float centerX, float y, float scale, int color) {
        aligned(graphics, font, value, SEMIBOLD, centerX, y, scale, color, false, ALIGN_CENTER);
    }

    private static void aligned(GuiGraphics graphics, Font font, Component value, ResourceLocation[] faces,
                                float anchorX, float y, float scale, int color, boolean shadow, float align) {
        float pixels = pixelsPerUnit(graphics);
        float snapped = crisp(REGULAR_BAKE, pixels, scale);
        layout(value, faces, faces == REGULAR ? BOLD : faces, REGULAR_BAKE, pixels * snapped);

        float span = align == ALIGN_LEFT ? 0.0f : span(font, pixels, snapped, 0.0f);
        drawGlyphs(graphics, font, anchorX - span * align, y, snapped, 0.0f, color, shadow);
    }

    public static void textTitle(GuiGraphics graphics, Font font, Component value, float centerX, float y, float scale, float tracking, int color) {
        tracked(graphics, font, value, TITLE, PLAIN_BAKE, centerX, y, scale, tracking, color);
    }

    public static void textTracked(GuiGraphics graphics, Font font, Component value, float centerX, float y, float scale, float tracking, int color) {
        tracked(graphics, font, value, BOLD, REGULAR_BAKE, centerX, y, scale, tracking, color);
    }

    public interface GlyphTone {
        int tint(int index, int count, int base);

        float rise(int index, int count);
    }

    public static void textHeroToned(GuiGraphics graphics, Font font, Component value, float centerX, float y,
                                     float scale, float tracking, int color, GlyphTone tone) {
        toned(graphics, font, value, HERO, PLAIN_BAKE, centerX, y, scale, tracking, color, tone);
    }

    public static void textTitleToned(GuiGraphics graphics, Font font, Component value, float centerX, float y,
                                      float scale, float tracking, int color, GlyphTone tone) {
        toned(graphics, font, value, TITLE, PLAIN_BAKE, centerX, y, scale, tracking, color, tone);
    }

    public static void textTrackedToned(GuiGraphics graphics, Font font, Component value, float centerX, float y,
                                        float scale, float tracking, int color, GlyphTone tone) {
        toned(graphics, font, value, BOLD, REGULAR_BAKE, centerX, y, scale, tracking, color, tone);
    }

    public static void textTrackedFit(GuiGraphics graphics, Font font, Component value, float centerX, float boxY,
                                      float boxHeight, float boxWidth, float scale, float tracking, int color, boolean bold) {
        textTrackedBox(graphics, font, value, centerX - boxWidth / 2.0f, boxY, boxHeight, boxWidth,
                scale, tracking, color, bold, ALIGN_CENTER);
    }

    public static void textTrackedBox(GuiGraphics graphics, Font font, Component value, float boxX, float boxY,
                                      float boxHeight, float boxWidth, float scale, float tracking, int color,
                                      boolean bold, float align) {
        if (value.getString().isEmpty()) return;

        float pixels = pixelsPerUnit(graphics);
        scale = crisp(REGULAR_BAKE, pixels, scale);
        layout(value, BOLD, BOLD, REGULAR_BAKE, pixels * scale);

        float span = span(font, pixels, scale, tracking);
        float y = centerY(boxY, boxHeight, scale);
        if (span <= boxWidth) {
            drawGlyphs(graphics, font, boxX + (boxWidth - span) * align, y, scale, tracking, color, false);
            return;
        }

        float left = boxX;
        float travel = span - boxWidth;
        double seconds = Util.getMillis() / 1000.0;
        double period = Math.max(travel * SCROLL_SECONDS_PER_UNIT, SCROLL_MIN_SECONDS);
        double sway = Math.sin(Math.PI / 2.0 * Math.cos(Math.PI * 2.0 * seconds / period)) / 2.0 + 0.5;
        float start = left - (float) (sway * travel);

        // WHY: ножницы режут глиф пополам, и строка выглядит вылезшей за плитку; кромки гасятся
        // WHY: по букве, поэтому обрез приходится на уже прозрачную часть строки
        scissor(graphics, left, boxY, boxWidth, boxHeight);
        fadeFrom = (left - start) / scale;
        fadeTo = (left + boxWidth - start) / scale;
        fadeWidth = FADE_UNITS / scale;
        fading = true;
        try {
            drawGlyphs(graphics, font, start, y, scale, tracking, color, false);
        } finally {
            fading = false;
            graphics.disableScissor();
        }
    }

    private static int faded(int color, float x) {
        if (!fading || fadeWidth <= 0.001f) return color;

        float alpha = UiAnim.clamp01(Math.min((x - fadeFrom) / fadeWidth, (fadeTo - x) / fadeWidth));
        return alpha >= 0.999f ? color : UiTheme.alpha(color, alpha);
    }

    public static void textTrackedLeft(GuiGraphics graphics, Font font, Component value, float x, float y, float scale, float tracking, int color) {
        if (value.getString().isEmpty()) return;
        float pixels = pixelsPerUnit(graphics);
        float snapped = crisp(REGULAR_BAKE, pixels, scale);
        layout(value, REGULAR, REGULAR, REGULAR_BAKE, pixels * snapped);
        drawGlyphs(graphics, font, x, y, snapped, tracking, color, false);
    }

    public static ResourceLocation faceFor(float emScale) {
        return REGULAR[step(REGULAR_BAKE, emScale)];
    }

    public static ResourceLocation boldFaceFor(float emScale) {
        return BOLD[step(REGULAR_BAKE, emScale)];
    }

    public static ResourceLocation screenFace() {
        return faceFor((float) Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale()));
    }

    public static float pixels(GuiGraphics graphics) {
        return pixelsPerUnit(graphics);
    }

    private static void tracked(GuiGraphics graphics, Font font, Component value, ResourceLocation[] faces,
                                float[] bakes, float centerX, float y, float scale, float tracking, int color) {
        toned(graphics, font, value, faces, bakes, centerX, y, scale, tracking, color, null);
    }

    private static void toned(GuiGraphics graphics, Font font, Component value, ResourceLocation[] faces,
                              float[] bakes, float centerX, float y, float scale, float tracking, int color,
                              GlyphTone tone) {
        if (value.getString().isEmpty()) return;

        float pixels = pixelsPerUnit(graphics);
        float snapped = crisp(bakes, pixels, scale);
        layout(value, faces, faces, bakes, pixels * snapped);
        float span = span(font, pixels, snapped, tracking);
        drawGlyphs(graphics, font, centerX - span / 2.0f, y, snapped, tracking, color, false, tone);
    }

    // WHY: перо ставит строку на целый пиксель ради чёткости, и медленное движение текста идёт
    // WHY: ступенями по четверти единицы. Тому, что плывёт постоянно, привязка к сетке вредна
    public static boolean floating(boolean enabled) {
        boolean previous = floating;
        floating = enabled;
        return previous;
    }

    public static boolean rawScale(boolean enabled) {
        boolean previous = rawScale;
        rawScale = enabled;
        return previous;
    }

    private static float crisp(float[] bakes, float pixels, float desired) {
        if (rawScale || MsdfFontSets.ready() || pixels <= 0.01f) return desired;
        float best = 0.0f;
        for (float bake : bakes) {
            float candidate = bake / pixels;
            if (candidate <= desired + 0.002f && candidate > best) best = candidate;
        }
        return best > 0.0f ? best : desired;
    }

    public static void clip(GuiGraphics graphics, float x, float y, float width, float height) {
        scissor(graphics, x, y, width, height);
    }

    private static void scissor(GuiGraphics graphics, float x, float y, float width, float height) {
        Matrix4f matrix = graphics.pose().last().pose();
        int left = Math.round(x * matrix.m00() + matrix.m30());
        int top = Math.round(y * matrix.m11() + matrix.m31());
        int right = Math.round((x + width) * matrix.m00() + matrix.m30());
        int bottom = Math.round((y + height) * matrix.m11() + matrix.m31());
        graphics.enableScissor(left, top, right, bottom);
    }

    public static float measureLine(GuiGraphics graphics, Font font, FormattedCharSequence value, float scale) {
        float pixels = pixelsPerUnit(graphics);
        float snapped = crisp(REGULAR_BAKE, pixels, scale);
        collect(value, REGULAR_BAKE[step(REGULAR_BAKE, pixels * snapped * SUPERSAMPLE)]);
        return span(font, pixels, snapped, 0.0f);
    }

    public static void textLine(GuiGraphics graphics, Font font, FormattedCharSequence value, float x, float y, float scale, int color, boolean shadow) {
        float pixels = pixelsPerUnit(graphics);
        float snapped = crisp(REGULAR_BAKE, pixels, scale);
        collect(value, REGULAR_BAKE[step(REGULAR_BAKE, pixels * snapped * SUPERSAMPLE)]);
        drawGlyphs(graphics, font, x, y, snapped, 0.0f, color, shadow);
    }

    public static List<FormattedCharSequence> split(GuiGraphics graphics, Font font, Component value, float scale, int width) {
        float pixels = pixelsPerUnit(graphics);
        int variant = step(REGULAR_BAKE, pixels * crisp(REGULAR_BAKE, pixels, scale) * SUPERSAMPLE);
        UiFont.push(screenFace());
        try {
            return font.split(restyle(value, Style.EMPTY, BOLD[variant], BOLD[variant]), width);
        } finally {
            UiFont.pop();
        }
    }

    public static String flatten(FormattedCharSequence sequence) {
        StringBuilder text = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            text.appendCodePoint(codePoint);
            return true;
        });
        return text.toString();
    }

    private static MutableComponent restyle(Component value, Style parent, ResourceLocation regular, ResourceLocation bold) {
        Style style = value.getStyle().applyTo(parent);
        MutableComponent result = MutableComponent.create(value.getContents())
                .setStyle(style.withFont(style.isBold() ? bold : regular).withBold(false));
        for (Component sibling : value.getSiblings()) {
            result.append(restyle(sibling, style, regular, bold));
        }
        return result;
    }

    private static int step(float[] bakes, float emScale) {
        for (int i = 0; i < bakes.length; i++) {
            if (bakes[i] >= emScale - BAKE_SLACK) return i;
        }
        return bakes.length - 1;
    }

    private static void layout(Component value, ResourceLocation[] faces, ResourceLocation[] bolds, float[] bakes, float em) {
        int variant = step(bakes, em * SUPERSAMPLE);
        collect(restyle(value, Style.EMPTY, faces[variant], bolds[variant]).getVisualOrderText(), bakes[variant]);
    }

    private static void collect(FormattedCharSequence sequence, float bake) {
        glyphs = 0;
        sequence.accept((index, style, code) -> {
            if (glyphs >= MAX_GLYPHS) return false;
            GLYPH_CODE[glyphs] = code;
            GLYPH_STYLE[glyphs] = style;
            glyphs++;
            return true;
        });
    }

    private static float advance(Font font, int index) {
        CURSOR.code = GLYPH_CODE[index];
        CURSOR.style = GLYPH_STYLE[index];
        return font.getSplitter().stringWidth(CURSOR);
    }

    private static int pen(Font font, float em, float tracking) {
        UiFont.push(screenFace());
        try {
            int pen = 0;
            for (int i = 0; i < glyphs; i++) {
                pen += Math.max(0, Math.round((advance(font, i) + tracking) * em));
            }
            return glyphs == 0 ? 0 : pen - Math.round(tracking * em);
        } finally {
            UiFont.pop();
        }
    }

    private static float span(Font font, float pixels, float scale, float tracking) {
        if (pixels <= 0.01f) return 0.0f;
        return pen(font, pixels * scale, tracking) / pixels;
    }

    private static void drawGlyphs(GuiGraphics graphics, Font font, float x, float y, float scale,
                                   float tracking, int color, boolean shadow) {
        drawGlyphs(graphics, font, x, y, scale, tracking, color, shadow, null);
    }

    private static void drawGlyphs(GuiGraphics graphics, Font font, float x, float y, float scale,
                                   float tracking, int color, boolean shadow, GlyphTone tone) {
        if (glyphs == 0 || tone == null && vanishing(color)) return;

        float em = pixelsPerUnit(graphics) * scale;
        graphics.pose().pushPose();
        translate(graphics, x, y, scale);
        UiFont.push(screenFace());
        beginText(graphics, false);

        // WHY: набор идёт чужим пером и чужим атласом, а между push и pop лежит целая строка:
        // WHY: одна осечка на глифе оставляла бы поднятую позу и сбитый шрифт на весь кадр
        try {
            paintGlyphs(graphics, font, em, scale, tracking, color, tone);
        } finally {
            endText(graphics);
            UiFont.pop();
            graphics.pose().popPose();
        }
    }

    private static void paintGlyphs(GuiGraphics graphics, Font font, float em, float scale,
                                    float tracking, int color, GlyphTone tone) {
        Matrix4f pose = graphics.pose().last().pose();
        MultiBufferSource.BufferSource buffer = graphics.bufferSource();
        float unit = em > 0.01f ? 1.0f / em : 1.0f;
        int pen = 0;
        float lift = scale > 0.001f ? 1.0f / scale : 1.0f;
        for (int i = 0; i < glyphs; i++) {
            float step = advance(font, i);
            int tint = faded(tone == null ? color : tone.tint(i, glyphs, color), pen * unit + step / 2.0f);
            float rise = tone == null ? 0.0f : tone.rise(i, glyphs) * lift;
            if (!vanishing(tint)) {
                font.drawInBatch(CURSOR, pen * unit, -rise, tint, false, pose, buffer,
                        Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
            }
            pen += Math.max(0, Math.round((step + tracking) * em));
        }
    }

    public static boolean vanishing(int color) {
        return ((color >>> 24) & 0xFF) < OPAQUE_FALLBACK_ALPHA;
    }

    private static void translate(GuiGraphics graphics, float x, float y, float scale) {
        Matrix4f matrix = graphics.pose().last().pose();
        float unit = matrix.m00();
        if (!floating && Math.abs(matrix.m01()) < 1.0E-4f && unit > 1.0E-4f) {
            double gui = Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
            x = snap(x, unit, matrix.m30(), gui);
            y = snap(y, matrix.m11(), matrix.m31(), gui);
        }
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
    }

    private static float snap(float value, float unit, float offset, double gui) {
        if (unit <= 1.0E-4f) return value;
        double pixel = (value * unit + offset) * gui;
        return (float) ((Math.round(pixel) / gui - offset) / unit);
    }

    private static void beginText(GuiGraphics graphics, boolean exact) {
        graphics.bufferSource().endBatch();
        if (exact) return;
        ForgeRenderTypes.enableTextTextureLinearFiltering = true;
        smoothing = true;
    }

    private static void endText(GuiGraphics graphics) {
        graphics.bufferSource().endBatch();
        if (!smoothing) return;
        smoothing = false;
        ForgeRenderTypes.enableTextTextureLinearFiltering = false;
    }

    private static void dart(float centerX, float centerY, float dirX, float dirY, float size) {
        float sideX = -dirY;
        float sideY = dirX;
        reset();
        push(centerX + dirX * size * 1.05f, centerY + dirY * size * 1.05f);
        push(centerX - dirX * size * 0.74f + sideX * size * 0.58f, centerY - dirY * size * 0.74f + sideY * size * 0.58f);
        push(centerX - dirX * size * 0.26f, centerY - dirY * size * 0.26f);
        push(centerX - dirX * size * 0.74f - sideX * size * 0.58f, centerY - dirY * size * 0.74f - sideY * size * 0.58f);
    }

    private static void bezier(float x0, float y0, float cx, float cy, float x1, float y1, int segments) {
        for (int i = 1; i <= segments; i++) {
            float t = (float) i / segments;
            float inv = 1.0f - t;
            float x = inv * inv * x0 + 2.0f * inv * t * cx + t * t * x1;
            float y = inv * inv * y0 + 2.0f * inv * t * cy + t * t * y1;
            push(x, y);
        }
    }

    private static int ringShape(float x, float y, float width, float height, float radius, int step,
                                 boolean round, float[] outX, float[] outY) {
        if (width <= 0.0f || height <= 0.0f) return 0;

        float r = Mth.clamp(radius, 0.0f, Math.min(width, height) / 2.0f);
        float[] ax = cornerX(round);
        float[] ay = cornerY(round);

        float left = x + r;
        float right = x + width - r;
        float top = y + r;
        float bottom = y + height - r;

        int written = 0;
        for (int i = 0; i <= ARC_TABLE; i += step) written = put(outX, outY, written, right + ay[i] * r, top - ax[i] * r);
        for (int i = 0; i <= ARC_TABLE; i += step) written = put(outX, outY, written, right + ax[i] * r, bottom + ay[i] * r);
        for (int i = 0; i <= ARC_TABLE; i += step) written = put(outX, outY, written, left - ay[i] * r, bottom + ax[i] * r);
        for (int i = 0; i <= ARC_TABLE; i += step) written = put(outX, outY, written, left - ax[i] * r, top - ay[i] * r);
        return written;
    }

    private static int put(float[] outX, float[] outY, int index, float x, float y) {
        if (index >= MAX_POINTS) return index;
        outX[index] = x;
        outY[index] = y;
        return index + 1;
    }

    private static void roundedRect(float x, float y, float width, float height, float radius, float pixels) {
        reset();
        float r = Math.min(radius, Math.min(width, height) / 2.0f);
        if (r <= 0.05f) {
            push(x, y);
            push(x + width, y);
            push(x + width, y + height);
            push(x, y + height);
            return;
        }

        boolean round = UiGlassStyle.circular(width, height, r);
        float[] ax = cornerX(round);
        float[] ay = cornerY(round);
        int step = arcStep(r * pixels);

        float left = x + r;
        float right = x + width - r;
        float top = y + r;
        float bottom = y + height - r;

        for (int i = 0; i <= ARC_TABLE; i += step) push(right + ay[i] * r, top - ax[i] * r);
        for (int i = 0; i <= ARC_TABLE; i += step) push(right + ax[i] * r, bottom + ay[i] * r);
        for (int i = 0; i <= ARC_TABLE; i += step) push(left - ay[i] * r, bottom + ax[i] * r);
        for (int i = 0; i <= ARC_TABLE; i += step) push(left - ax[i] * r, top - ay[i] * r);
    }

    private static int arcStep(float radiusPixels) {
        if (radiusPixels >= 22.0f) return 1;
        if (radiusPixels >= 10.0f) return 2;
        if (radiusPixels >= 4.0f) return 3;
        return 6;
    }

    private static void reset() {
        points = 0;
    }

    private static void push(float x, float y) {
        if (points >= MAX_POINTS) return;
        if (points > 0) {
            float dx = x - PATH_X[points - 1];
            float dy = y - PATH_Y[points - 1];
            if (dx * dx + dy * dy < 1.0E-6f) return;
        }
        PATH_X[points] = x;
        PATH_Y[points] = y;
        points++;
    }

    private static void fillPath(GuiGraphics graphics, int color, float edge) {
        int count = closedCount();
        if (count < 3) return;

        float centerX = 0.0f;
        float centerY = 0.0f;
        for (int i = 0; i < count; i++) {
            centerX += PATH_X[i];
            centerY += PATH_Y[i];
        }
        fillPathAround(graphics, color, edge, centerX / count, centerY / count);
    }

    private static void fillPathAround(GuiGraphics graphics, int color, float edge, float centerX, float centerY) {
        int count = closedCount();
        if (count < 3 || (!shaded && (color >>> 24) == 0)) return;

        float half = Math.min(edge * 0.5f, narrowSpan(count) * 0.22f);
        miters(count, centerX, centerY);
        unitNormals(count);
        limits(count, half);

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();

        beginBatch();
        for (int i = 0; i < count; i++) {
            fillSegment(builder, matrix, i, i + 1 == count ? 0 : i + 1, centerX, centerY, color, half);
        }
        endBatch();
    }

    private static float narrowSpan(int count) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            minX = Math.min(minX, PATH_X[i]);
            minY = Math.min(minY, PATH_Y[i]);
            maxX = Math.max(maxX, PATH_X[i]);
            maxY = Math.max(maxY, PATH_Y[i]);
        }
        return Math.min(maxX - minX, maxY - minY);
    }

    private static void fillSegment(BufferBuilder builder, Matrix4f matrix, int i, int j,
                                    float centerX, float centerY, int color, float half) {
        float ix = PATH_X[i] - MITER_X[i] * EDGE_HALF[i];
        float iy = PATH_Y[i] - MITER_Y[i] * EDGE_HALF[i];
        float jx = PATH_X[j] - MITER_X[j] * EDGE_HALF[j];
        float jy = PATH_Y[j] - MITER_Y[j] * EDGE_HALF[j];

        shade(builder, matrix, centerX, centerY, color, 1.0f);
        shade(builder, matrix, jx, jy, color, 1.0f);
        shade(builder, matrix, ix, iy, color, 1.0f);

        if (half <= 0.0f) return;
        skirt(builder, matrix, i, j, color);
    }

    private static void skirt(BufferBuilder builder, Matrix4f matrix, int i, int j, int color) {
        int rings = skirtRings();
        float backAlpha = 1.0f;
        float backTravel = 0.0f;

        for (int ring = 1; ring <= rings; ring++) {
            float travel = ring / (float) rings;
            float frontAlpha = skirtAlpha(travel, rings);
            skirtBand(builder, matrix, i, j, color, backTravel, backAlpha, travel, frontAlpha);
            backTravel = travel;
            backAlpha = frontAlpha;
        }
    }

    private static float edgeAt(int index, float travel) {
        return -EDGE_HALF[index] + EDGE_HALF[index] * 2.0f * travel;
    }

    private static float flareAt(int index, float travel) {
        return SKIRT[index] * travel;
    }

    private static float skirtX(int index, float travel) {
        return PATH_X[index] + MITER_X[index] * edgeAt(index, travel)
                + NORMAL_X[index] * flareAt(index, travel);
    }

    private static float skirtY(int index, float travel) {
        return PATH_Y[index] + MITER_Y[index] * edgeAt(index, travel)
                + NORMAL_Y[index] * flareAt(index, travel);
    }

    private static void skirtBand(BufferBuilder builder, Matrix4f matrix, int i, int j, int color,
                                  float backTravel, float backAlpha, float frontTravel, float frontAlpha) {
        float bix = skirtX(i, backTravel);
        float biy = skirtY(i, backTravel);
        float bjx = skirtX(j, backTravel);
        float bjy = skirtY(j, backTravel);
        float fix = skirtX(i, frontTravel);
        float fiy = skirtY(i, frontTravel);
        float fjx = skirtX(j, frontTravel);
        float fjy = skirtY(j, frontTravel);

        shade(builder, matrix, bix, biy, color, backAlpha);
        shade(builder, matrix, bjx, bjy, color, backAlpha);
        shade(builder, matrix, fjx, fjy, color, frontAlpha);

        shade(builder, matrix, bix, biy, color, backAlpha);
        shade(builder, matrix, fjx, fjy, color, frontAlpha);
        shade(builder, matrix, fix, fiy, color, frontAlpha);
    }

    private static void shade(BufferBuilder builder, Matrix4f matrix, float x, float y, int base, float alphaMod) {
        int color = base;
        if (shaded) {
            float t = shadeSpan <= 1.0E-4f ? 0.0f : (y - shadeOrigin) / shadeSpan;
            color = UiTheme.mix(shadeTop, shadeBottom, t);
        }
        builder.vertex(matrix, x, y, 0.0f)
                .color((color >> 16 & 255) / 255.0f, (color >> 8 & 255) / 255.0f, (color & 255) / 255.0f,
                        (color >>> 24) / 255.0f * alphaMod)
                .endVertex();
    }

    private static int closedCount() {
        int count = points;
        if (count > 2) {
            float dx = PATH_X[count - 1] - PATH_X[0];
            float dy = PATH_Y[count - 1] - PATH_Y[0];
            if (dx * dx + dy * dy < 1.0E-6f) count--;
        }
        return count;
    }

    private static void limits(int count, float half) {
        float reach = Math.min(skirtReach(), narrowSpan(count) * FLARE_SHARE);
        for (int i = 0; i < count; i++) {
            int prev = i == 0 ? count - 1 : i - 1;
            int next = i + 1 == count ? 0 : i + 1;
            float back = distance(i, prev);
            float ahead = distance(i, next);
            EDGE_HALF[i] = Math.min(half, Math.min(back, ahead) * 0.45f);
            SKIRT[i] = reach <= 0.0f ? 0.0f : reach * chaosAt(i, count);
        }
    }

    public static boolean dissolving(boolean value) {
        boolean previous = dissolving;
        dissolving = value;
        return previous;
    }

    // WHY: форму угла переключателя берёт даже шайба, у которой стороны равны: общая проверка
    // WHY: увела бы её в окружность мимо показателя, выбранного игроком
    private static float[] cornerX(boolean round) {
        if (UiGlassStyle.switching()) return SWITCH_X;
        return round ? CIRCLE_X : SQUIRCLE_X;
    }

    private static float[] cornerY(boolean round) {
        if (UiGlassStyle.switching()) return SWITCH_Y;
        return round ? CIRCLE_Y : SQUIRCLE_Y;
    }

    private static float skirtReach() {
        return dissolving ? Math.max(0.0f, UiGlassStyle.dissolve()) : 0.0f;
    }

    private static float chaosAt(int index, int count) {
        float chaos = UiAnim.clamp01(UiGlassStyle.dissolveChaos());
        if (chaos <= 0.001f) return 1.0f;

        double turn = index / (double) Math.max(1, count) * Math.PI * 2.0;
        double drift = Util.getMillis() / 1000.0 * UiGlassStyle.dissolveDrift();
        double wobble = Math.sin(turn * 3.0 + drift) * 0.62 + Math.sin(turn * 7.0 - drift * 0.7) * 0.38;
        return 1.0f - chaos * 0.5f + chaos * 0.5f * (float) wobble;
    }

    private static int skirtRings() {
        return skirtReach() <= 0.01f ? 1 : SKIRT_RINGS;
    }

    private static float skirtAlpha(float travel, int rings) {
        if (rings == 1) return 1.0f - travel;
        return 1.0f - travel * travel * (3.0f - 2.0f * travel);
    }

    private static float distance(int from, int to) {
        float dx = PATH_X[to] - PATH_X[from];
        float dy = PATH_Y[to] - PATH_Y[from];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static void miters(int count, float centerX, float centerY) {
        edgeNormals(count, centerX, centerY);
        cornerMiters(count, centerX, centerY);
    }

    private static void edgeNormals(int count, float centerX, float centerY) {
        for (int i = 0; i < count; i++) {
            int j = i + 1 == count ? 0 : i + 1;
            float dx = PATH_X[j] - PATH_X[i];
            float dy = PATH_Y[j] - PATH_Y[i];
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 1.0E-5f) {
                EDGE_X[i] = 0.0f;
                EDGE_Y[i] = 0.0f;
                continue;
            }
            float nx = dy / length;
            float ny = -dx / length;
            float towardX = (PATH_X[i] + PATH_X[j]) * 0.5f - centerX;
            float towardY = (PATH_Y[i] + PATH_Y[j]) * 0.5f - centerY;
            boolean inward = nx * towardX + ny * towardY < 0.0f;
            EDGE_X[i] = inward ? -nx : nx;
            EDGE_Y[i] = inward ? -ny : ny;
        }
    }

    private static void cornerMiters(int count, float centerX, float centerY) {
        for (int i = 0; i < count; i++) {
            int p = i == 0 ? count - 1 : i - 1;
            float nx = EDGE_X[p] + EDGE_X[i];
            float ny = EDGE_Y[p] + EDGE_Y[i];
            float length = (float) Math.sqrt(nx * nx + ny * ny);
            if (length < 1.0E-5f) {
                radialFallback(i, centerX, centerY);
                continue;
            }
            nx /= length;
            ny /= length;
            float projection = Math.max(0.35f, Math.abs(nx * EDGE_X[i] + ny * EDGE_Y[i]));
            MITER_X[i] = nx / projection;
            MITER_Y[i] = ny / projection;
        }
    }

    private static void radialFallback(int i, float centerX, float centerY) {
        float dx = PATH_X[i] - centerX;
        float dy = PATH_Y[i] - centerY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        MITER_X[i] = length < 1.0E-5f ? 0.0f : dx / length;
        MITER_Y[i] = length < 1.0E-5f ? 0.0f : dy / length;
    }

    static float pixelsPerUnit(GuiGraphics graphics) {
        Matrix4f matrix = graphics.pose().last().pose();
        float scale = (float) Math.sqrt(matrix.m00() * matrix.m00() + matrix.m01() * matrix.m01());
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        return (float) (scale * Math.max(1.0, gui));
    }

    private static float feather(float pixels) {
        if (pixels <= 0.05f) return 0.0f;
        return Mth.clamp(UiGlassStyle.edgePixels() / pixels, 0.02f, 2.0f);
    }

    private static void gradientQuad(GuiGraphics graphics, float x, float y, float width, float height,
                                     int topLeft, int topRight, int bottomRight, int bottomLeft) {
        if (width <= 0.0f || height <= 0.0f) return;

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();

        beginBatch();

        colored(builder, matrix, x, y, topLeft);
        colored(builder, matrix, x, y + height, bottomLeft);
        colored(builder, matrix, x + width, y + height, bottomRight);

        colored(builder, matrix, x, y, topLeft);
        colored(builder, matrix, x + width, y + height, bottomRight);
        colored(builder, matrix, x + width, y, topRight);

        endBatch();
    }

    private static void colored(BufferBuilder builder, Matrix4f matrix, float x, float y, int color) {
        builder.vertex(matrix, x, y, 0.0f)
                .color((color >> 16 & 255) / 255.0f, (color >> 8 & 255) / 255.0f, (color & 255) / 255.0f, (color >>> 24) / 255.0f)
                .endVertex();
    }

    private static final class Lens {
        private final Vector2f spot = new Vector2f();

        private Matrix4f matrix;
        private UiPlane plane;

        private int count;
        private int step;
        private float left;
        private float top;
        private float width;
        private float height;
        private float radius;
        private float centerX;
        private float centerY;
        private float band;
        private float reach;
        private float strength;
        private float edge;
        private float alpha;
        private float invWidth;
        private float invHeight;
        private float centerU;
        private float centerV;
        private float sampleU;
        private float sampleV;
        private boolean round;
        private float ringAlpha;
        private float prevAlpha;
        private int rings;
        private int edgeRings;

        private boolean prepare(GuiGraphics graphics, float x, float y, float width, float height,
                                float radius, float pixels, float alpha) {
            this.left = x;
            this.top = y;
            this.width = width;
            this.height = height;
            this.radius = Math.min(radius, Math.min(width, height) / 2.0f);
            this.centerX = x + width / 2.0f;
            this.centerY = y + height / 2.0f;
            this.edge = feather(pixels);
            this.alpha = alpha;
            this.round = UiGlassStyle.circular(width, height, this.radius);
            this.step = arcStep(Math.max(this.radius, 2.0f) * pixels);
            this.count = outline();
            if (count < 3) return false;

            float smallest = Math.min(width, height);
            reach = smallest / 2.0f * CORE_SHARE;
            band = Math.min(UiGlassStyle.band(), smallest * BAND_SHARE);
            strength = Math.min(band * UiGlassStyle.pull(), smallest * PULL_LIMIT);
            rings = lensRingsFor(smallest * pixels);
            edgeRings = Math.min(rings, Math.max(2, rings * UiGlassStyle.edgeRings() / Math.max(1, UiGlassStyle.rings())));

            Minecraft mc = Minecraft.getInstance();
            invWidth = 1.0f / Math.max(1, mc.getWindow().getGuiScaledWidth());
            invHeight = 1.0f / Math.max(1, mc.getWindow().getGuiScaledHeight());
            matrix = graphics.pose().last().pose();
            plane = UiBackdrop.carrier();

            sample(centerX, centerY, lensZoom(reach, reach));
            centerU = sampleU;
            centerV = sampleV;
            return true;
        }

        private int outline() {
            int written = ringShape(left, top, width, height, radius, step, round, PATH_X, PATH_Y);
            if (written < 3) return written;

            miters(written, centerX, centerY);
            unitNormals(written);
            return written;
        }

        private void ring(int index) {
            float depth = lensDepth(index, edgeRings, rings, band, reach);
            float pull = lensPull(depth, band, strength);
            float zoom = lensZoom(depth, reach);
            ringAlpha = alpha;
            ringShape(left + depth, top + depth, width - depth * 2.0f, height - depth * 2.0f,
                    ringRadius(depth), step, round, RING_X, RING_Y);

            for (int i = 0; i < count; i++) {
                sample(RING_X[i] + NORMAL_X[i] * pull, RING_Y[i] + NORMAL_Y[i] * pull, zoom);
                RING_U[i] = sampleU;
                RING_V[i] = sampleV;
            }
        }

        private float ringRadius(float depth) {
            float half = Math.min(width, height) / 2.0f;
            if (half <= 0.01f) return 0.0f;
            return radius * Math.max(0.0f, 1.0f - depth / half);
        }

        private void keep() {
            System.arraycopy(RING_X, 0, PREV_X, 0, count);
            System.arraycopy(RING_Y, 0, PREV_Y, 0, count);
            System.arraycopy(RING_U, 0, PREV_U, 0, count);
            System.arraycopy(RING_V, 0, PREV_V, 0, count);
            prevAlpha = ringAlpha;
        }

        private void sample(float x, float y, float zoom) {
            float sx = centerX + (x - centerX) * (1.0f - zoom);
            float sy = centerY + (y - centerY) * (1.0f - zoom);
            float screenX = matrix.m00() * sx + matrix.m10() * sy + matrix.m30();
            float screenY = matrix.m01() * sx + matrix.m11() * sy + matrix.m31();
            if (plane != null && plane.sample(screenX, screenY, spot)) {
                sampleU = spot.x;
                sampleV = spot.y;
                return;
            }
            sampleU = screenX * invWidth;
            sampleV = 1.0f - screenY * invHeight;
        }

        private void vertex(BufferBuilder builder, float x, float y, float u, float v, float weight) {
            builder.vertex(matrix, x, y, 0.0f).uv(u, v).color(1.0f, 1.0f, 1.0f, weight).endVertex();
        }
    }

    private static final class Cursor implements FormattedCharSequence {
        private Style style = Style.EMPTY;
        private int code;

        @Override
        public boolean accept(FormattedCharSink sink) {
            return sink.accept(0, this.style, this.code);
        }
    }
}
