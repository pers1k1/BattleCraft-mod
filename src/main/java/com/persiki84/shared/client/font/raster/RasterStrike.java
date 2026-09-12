package com.persiki84.shared.client.font.raster;

import com.persiki84.shared.client.font.GlowGlyph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class RasterStrike {
    // WHY: BakedGlyph.render вычитает 3 из up/down, поэтому базовая линия на 7 пикселей от верха строки задаётся десяткой
    private static final float BASELINE = 10.0f;

    private static final float BAKE_SCALE = 6.0f;
    private static final int BAKE_LIMIT = 128;
    private static final float PADDING_TEXELS = 1.5f;

    private final String name;
    private final float dilation;
    private final Font font;
    private final FontRenderContext context;
    private final float oversample;
    private final int padding;
    private final Function<ResourceLocation, GlyphRenderTypes> renderTypes;

    private final List<RasterPage> pages = new ArrayList<>();
    private final Map<Integer, BakedGlyph> baked = new HashMap<>();
    private final Map<Integer, Float> advances = new HashMap<>();

    private RasterStrike(String name, Font source, float pixelsPerEm, float dilation,
                         Function<ResourceLocation, GlyphRenderTypes> renderTypes) {
        this.name = name;
        this.dilation = dilation;
        this.renderTypes = renderTypes;

        int bakePixels = Math.min(Math.round(pixelsPerEm * BAKE_SCALE), BAKE_LIMIT);
        this.font = source.deriveFont(Font.PLAIN, bakePixels);
        this.oversample = bakePixels / pixelsPerEm;
        this.padding = Math.max(2, Math.round(oversample * PADDING_TEXELS));
        this.context = new FontRenderContext(new AffineTransform(),
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    public static Optional<RasterStrike> load(ResourceLocation typeface, String name, float pixelsPerEm,
                                              float dilation,
                                              Function<ResourceLocation, GlyphRenderTypes> renderTypes) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(typeface);
        if (resource.isEmpty()) return Optional.empty();

        try (InputStream stream = resource.get().open()) {
            Font source = Font.createFont(Font.TRUETYPE_FONT, stream);
            return Optional.of(new RasterStrike(name, source, pixelsPerEm, dilation, renderTypes));
        } catch (Exception error) {
            return Optional.empty();
        }
    }

    public boolean has(int codepoint) {
        return font.canDisplay(codepoint);
    }

    public float advance(int codepoint) {
        Float stored = advances.get(codepoint);
        if (stored != null) return stored;

        float measured = vectorFor(codepoint).getGlyphMetrics(0).getAdvance() / oversample;
        advances.put(codepoint, measured);
        return measured;
    }

    public BakedGlyph glyph(int codepoint) {
        BakedGlyph stored = baked.get(codepoint);
        if (stored != null) return stored;

        BakedGlyph made = bake(codepoint);
        baked.put(codepoint, made);
        return made;
    }

    public void close() {
        for (RasterPage page : pages) {
            page.close();
        }
        pages.clear();
        baked.clear();
        advances.clear();
    }

    private GlyphVector vectorFor(int codepoint) {
        return font.createGlyphVector(context, new String(Character.toChars(codepoint)));
    }

    private BakedGlyph bake(int codepoint) {
        GlyphVector vector = vectorFor(codepoint);
        Rectangle ink = vector.getPixelBounds(context, 0.0f, 0.0f);
        if (ink.width <= 0 || ink.height <= 0) return empty();

        int width = ink.width + padding * 2;
        int height = ink.height + padding * 2;
        RasterPage.Slot slot = claim(width, height);
        if (slot == null) return empty();

        RasterPage page = pages.get(pages.size() - 1);
        page.copy(paint(vector, ink, width, height), slot);
        page.uploadIfDirty();

        float left = (ink.x - padding) / oversample;
        float top = (ink.y - padding) / oversample;
        return new GlowGlyph(page.renderTypes(),
                slot.u0(), slot.u1(), slot.v0(), slot.v1(),
                left, left + width / oversample,
                BASELINE + top, BASELINE + top + height / oversample);
    }

    private BufferedImage paint(GlyphVector vector, Rectangle ink, int width, int height) {
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D brush = canvas.createGraphics();
        brush.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        brush.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        brush.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        brush.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        brush.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        brush.setColor(Color.WHITE);
        brush.drawGlyphVector(vector, padding - ink.x, padding - ink.y);
        brush.dispose();
        strengthen(canvas);
        return canvas;
    }

    // WHY: доля от максимума соседей вместо самого максимума: чистая дилатация съедает просветы
    // WHY: внутри букв, а доля утолщает штрих и оставляет контрформы на месте
    private void strengthen(BufferedImage canvas) {
        if (dilation <= 0.0f) return;

        int width = canvas.getWidth();
        int height = canvas.getHeight();
        int[] read = canvas.getRGB(0, 0, width, height, null, 0, width);
        int[] written = read.clone();
        for (int row = 1; row < height - 1; row++) {
            for (int column = 1; column < width - 1; column++) {
                int at = row * width + column;
                int own = read[at] >>> 24;
                int lifted = Math.round(own + (neighbourPeak(read, width, at) - own) * dilation);
                written[at] = Math.min(255, lifted) << 24 | 0xFFFFFF;
            }
        }
        canvas.setRGB(0, 0, width, height, written, 0, width);
    }

    private static int neighbourPeak(int[] pixels, int width, int at) {
        int peak = pixels[at] >>> 24;
        peak = Math.max(peak, pixels[at - 1] >>> 24);
        peak = Math.max(peak, pixels[at + 1] >>> 24);
        peak = Math.max(peak, pixels[at - width] >>> 24);
        peak = Math.max(peak, pixels[at + width] >>> 24);
        peak = Math.max(peak, pixels[at - width - 1] >>> 24);
        peak = Math.max(peak, pixels[at - width + 1] >>> 24);
        peak = Math.max(peak, pixels[at + width - 1] >>> 24);
        return Math.max(peak, pixels[at + width + 1] >>> 24);
    }

    private RasterPage.Slot claim(int width, int height) {
        if (!pages.isEmpty()) {
            RasterPage.Slot slot = pages.get(pages.size() - 1).allocate(width, height);
            if (slot != null) return slot;
        }
        ResourceLocation location = pageLocation(pages.size());
        RasterPage page = new RasterPage(location, renderTypes.apply(location));
        pages.add(page);
        return page.allocate(width, height);
    }

    private ResourceLocation pageLocation(int index) {
        return new ResourceLocation("battlecraft", "raster/" + name + "_" + index);
    }

    private BakedGlyph empty() {
        GlyphRenderTypes types = pages.isEmpty()
                ? renderTypes.apply(pageLocation(0))
                : pages.get(pages.size() - 1).renderTypes();
        return new BakedGlyph(types, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    }
}
