package com.persiki84.shared.client.font.raster;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;

import javax.annotation.Nullable;
import java.util.function.Function;

public final class RasterGlyphProvider implements GlyphProvider {
    private static final int RANGE_LIMIT = 0x2200;

    private final RasterStrike strike;

    public RasterGlyphProvider(RasterStrike strike) {
        this.strike = strike;
    }

    @Override
    public void close() {
        strike.close();
    }

    @Nullable
    @Override
    public GlyphInfo getGlyph(int codepoint) {
        return strike.has(codepoint) ? new StrikeGlyph(codepoint) : null;
    }

    @Override
    public IntSet getSupportedGlyphs() {
        IntSet supported = new IntOpenHashSet();
        for (int codepoint = 0; codepoint < RANGE_LIMIT; codepoint++) {
            if (strike.has(codepoint)) supported.add(codepoint);
        }
        return supported;
    }

    private final class StrikeGlyph implements GlyphInfo {
        private final int codepoint;

        private StrikeGlyph(int codepoint) {
            this.codepoint = codepoint;
        }

        @Override
        public float getAdvance() {
            return strike.advance(codepoint);
        }

        @Override
        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> unused) {
            return strike.glyph(codepoint);
        }
    }
}
