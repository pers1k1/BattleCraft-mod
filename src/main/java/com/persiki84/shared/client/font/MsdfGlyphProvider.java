package com.persiki84.shared.client.font;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;

import javax.annotation.Nullable;
import java.util.function.Function;

public final class MsdfGlyphProvider implements GlyphProvider {
    // WHY: BakedGlyph.render вычитает 3 из up/down, поэтому базовая линия на 7 пикселей от верха строки задаётся десяткой
    private static final float BASELINE = 10.0f;

    private final MsdfAtlas atlas;
    private final GlyphRenderTypes renderTypes;
    private final float pixelsPerEm;

    public MsdfGlyphProvider(MsdfAtlas atlas, GlyphRenderTypes renderTypes, float pixelsPerEm) {
        this.atlas = atlas;
        this.renderTypes = renderTypes;
        this.pixelsPerEm = pixelsPerEm;
    }

    @Nullable
    @Override
    public GlyphInfo getGlyph(int codepoint) {
        MsdfAtlas.Glyph glyph = atlas.glyph(codepoint);
        if (glyph == null) return null;

        return new AtlasGlyph(glyph);
    }

    @Override
    public IntSet getSupportedGlyphs() {
        IntSet supported = new IntOpenHashSet();
        for (int codepoint = 0; codepoint < 0x2200; codepoint++) {
            if (atlas.has(codepoint)) supported.add(codepoint);
        }
        return supported;
    }

    private final class AtlasGlyph implements GlyphInfo {
        private final MsdfAtlas.Glyph glyph;

        private AtlasGlyph(MsdfAtlas.Glyph glyph) {
            this.glyph = glyph;
        }

        @Override
        public float getAdvance() {
            return glyph.advance() * pixelsPerEm;
        }

        @Override
        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> unused) {
            if (glyph.empty()) {
                return new BakedGlyph(renderTypes, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
            }

            float left = glyph.planeLeft() * pixelsPerEm;
            float right = glyph.planeRight() * pixelsPerEm;
            float up = BASELINE - glyph.planeTop() * pixelsPerEm;
            float down = BASELINE - glyph.planeBottom() * pixelsPerEm;

            return new GlowGlyph(renderTypes,
                    glyph.u0(), glyph.u1(), glyph.v0(), glyph.v1(),
                    left, right, up, down);
        }
    }
}
