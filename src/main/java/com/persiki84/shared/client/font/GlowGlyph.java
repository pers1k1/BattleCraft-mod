package com.persiki84.shared.client.font;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.persiki84.shared.client.ui.UiGlassStyle;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.joml.Matrix4f;

public final class GlowGlyph extends BakedGlyph {
    private static final float DIAGONAL_SHARE = 0.7071f;
    private static final float MIN_ALPHA = 0.02f;
    private static final float DENSE_SPAN = 9.0f;
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
            int limit = spreadWorthDiagonals(matrix) ? OFFSETS.length : CARDINAL_OFFSETS;
            for (int step = 0; step < limit; step += 2) {
                super.render(italic, x + OFFSETS[step], y + OFFSETS[step + 1], matrix, buffer,
                        haloRed, haloGreen, haloBlue, halo, packedLight);
            }
        }

        super.render(italic, x, y, matrix, buffer, red, green, blue, alpha, packedLight);
    }

    private boolean spreadWorthDiagonals(Matrix4f matrix) {
        float scale = Math.max(Math.abs(matrix.m00()), Math.abs(matrix.m11()));
        return span * scale >= DENSE_SPAN;
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
