package com.persiki84.shared.client.font;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiRestFrame;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.joml.Matrix4f;

public final class GlowGlyph extends BakedGlyph {
    private static final float DIAGONAL_SHARE = 0.7071f;
    private static final float MIN_ALPHA = 0.002f;
    private static final float DENSE_FROM = 8.0f;
    private static final float DENSE_FULL = 10.0f;
    private static final int CARDINAL_OFFSETS = 8;
    private static final float[] OFFSETS = new float[16];

    private final float span;

    public GlowGlyph(GlyphRenderTypes renderTypes, float u0, float u1, float v0, float v1,
                     float left, float right, float up, float down) {
        super(renderTypes, u0, u1, v0, v1, left, right, up, down);
        this.span = right - left;
    }

    @Override
    public void render(boolean italic, float x, float y, Matrix4f matrix, VertexConsumer buffer,
                       float red, float green, float blue, float alpha, int packedLight) {
        float halo = alpha * UiGlassStyle.glowAlpha();
        if (halo > MIN_ALPHA) {
            spread(UiGlassStyle.glowSpread());
            float haloRed = lift(red);
            float haloGreen = lift(green);
            float haloBlue = lift(blue);
            float diagonal = halo * density(matrix);
            for (int step = 0; step < OFFSETS.length; step += 2) {
                float copy = step < CARDINAL_OFFSETS ? halo : diagonal;
                if (copy <= MIN_ALPHA) continue;
                super.render(italic, x + OFFSETS[step], y + OFFSETS[step + 1], matrix, buffer,
                        haloRed, haloGreen, haloBlue, copy, packedLight);
            }
        }

        super.render(italic, x, y, matrix, buffer, red, green, blue, alpha, packedLight);
    }

    // WHY: диагонали ореола включаются долей, а не порогом, и по размеру покоя: порог по живому
    // WHY: масштабу щёлкал на широких буквах посреди нажатия, и ореол у них вспыхивал вдвое
    private float density(Matrix4f matrix) {
        float scale = Math.max(Math.abs(matrix.m00()), Math.abs(matrix.m11())) / UiRestFrame.stretchX();
        float share = (span * scale - DENSE_FROM) / (DENSE_FULL - DENSE_FROM);
        return Math.max(0.0f, Math.min(1.0f, share));
    }

    private static float lift(float channel) {
        return channel + (1.0f - channel) * UiGlassStyle.glowLift();
    }

    private static void spread(float reach) {
        float diagonal = reach * DIAGONAL_SHARE;
        OFFSETS[0] = reach;
        OFFSETS[2] = -reach;
        OFFSETS[5] = reach;
        OFFSETS[7] = -reach;
        OFFSETS[8] = diagonal;
        OFFSETS[9] = diagonal;
        OFFSETS[10] = -diagonal;
        OFFSETS[11] = diagonal;
        OFFSETS[12] = diagonal;
        OFFSETS[13] = -diagonal;
        OFFSETS[14] = -diagonal;
        OFFSETS[15] = -diagonal;
    }
}
