package com.persiki84.shared.client.font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.persiki84.shared.client.font.raster.RasterGlyphProvider;
import com.persiki84.shared.client.font.raster.RasterStrike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class MsdfFontSets {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final float BODY_EM = 7.57f;
    private static final float TITLE_EM = 25.25f;
    private static final float HERO_EM = 46.0f;

    private enum Face {
        REGULAR("regular", "regular", "ui", BODY_EM),
        SEMIBOLD("semibold", "semibold", "ui_semibold", BODY_EM),
        BOLD("bold", "bold", "ui_bold", BODY_EM),
        TITLE("title", "title", "title", TITLE_EM),
        HERO("hero", "hero", "hero", HERO_EM);

        private final String id;
        private final String atlas;
        private final String typeface;
        private final float pixelsPerEm;

        Face(String id, String atlas, String typeface, float pixelsPerEm) {
            this.id = id;
            this.atlas = atlas;
            this.typeface = typeface;
            this.pixelsPerEm = pixelsPerEm;
        }

        ResourceLocation typeface() {
            return new ResourceLocation("battlecraft", "font/" + typeface + ".ttf");
        }
    }

    private static final FontSet[][] sets = new FontSet[FontShape.values().length][Face.values().length];
    private static final boolean[] refused = new boolean[FontShape.values().length];

    private static Supplier<FontShape> chooser = () -> FontShape.FALLBACK;
    private static FontShape shown;

    private MsdfFontSets() {}

    public static void chooser(Supplier<FontShape> source) {
        if (source != null) chooser = source;
    }

    public static void retry() {
        Arrays.fill(refused, false);
    }

    public static boolean ready() {
        return available(active());
    }

    public static FontShape active() {
        return shown != null ? shown : chosen();
    }

    public static boolean available(FontShape shape) {
        if (refused[shape.ordinal()]) return false;

        ensure(shape);
        if (built(shape)) return true;

        refused[shape.ordinal()] = true;
        return false;
    }

    public static void push(FontShape shape) {
        if (available(shape)) shown = shape;
    }

    public static void pop() {
        shown = null;
    }

    public static void drop(FontShape shape) {
        if (shape != chosen()) release(shape);
    }

    private static FontShape chosen() {
        FontShape picked = chooser.get();
        return picked == null ? FontShape.FALLBACK : picked;
    }

    public static FontSet regular() {
        return face(Face.REGULAR);
    }

    public static FontSet semibold() {
        return face(Face.SEMIBOLD);
    }

    public static FontSet bold() {
        return face(Face.BOLD);
    }

    public static FontSet title() {
        return face(Face.TITLE);
    }

    public static FontSet hero() {
        return face(Face.HERO);
    }

    private static FontSet face(Face face) {
        return sets[active().ordinal()][face.ordinal()];
    }

    private static boolean built(FontShape shape) {
        return sets[shape.ordinal()][Face.REGULAR.ordinal()] != null;
    }

    private static void ensure(FontShape shape) {
        if (built(shape)) return;

        FontSet[] prepared = new FontSet[Face.values().length];
        for (Face face : Face.values()) {
            FontSet set = buildSet(shape, face);
            if (set == null) {
                closeAll(prepared);
                LOGGER.warn("[battlecraft] font shape {} unavailable", shape.id());
                return;
            }
            prepared[face.ordinal()] = set;
        }
        System.arraycopy(prepared, 0, sets[shape.ordinal()], 0, prepared.length);
        LOGGER.info("[battlecraft] font shape {} ready", shape.id());
    }

    private static FontSet buildSet(FontShape shape, Face face) {
        GlyphProvider provider = shape.rasterized() ? rasterProvider(shape, face) : fieldProvider(shape, face);
        if (provider == null) return null;

        FontSet set = new FontSet(Minecraft.getInstance().getTextureManager(),
                new ResourceLocation("battlecraft", shape.id() + "_" + face.id));
        set.reload(List.of(provider));
        return set;
    }

    private static GlyphProvider fieldProvider(FontShape shape, Face face) {
        Optional<MsdfAtlas> loaded = MsdfAtlas.load(shape.metadata(face.atlas));
        if (loaded.isEmpty()) return null;

        MsdfAtlas atlas = loaded.get();
        if (!shape.field().equals(atlas.field())) {
            LOGGER.warn("[battlecraft] atlas {} holds {} field", face.atlas, atlas.field());
            return null;
        }
        MsdfShaders.useDistanceRange(shape, atlas.distanceRange());
        return new MsdfGlyphProvider(atlas,
                MsdfRenderTypes.forTexture(shape, shape.texture(face.atlas)), face.pixelsPerEm);
    }

    private static GlyphProvider rasterProvider(FontShape shape, Face face) {
        Optional<RasterStrike> strike = RasterStrike.load(face.typeface(), shape.id() + "_" + face.id,
                face.pixelsPerEm, shape.dilation(), page -> MsdfRenderTypes.forTexture(shape, page));
        return strike.map(RasterGlyphProvider::new).orElse(null);
    }

    private static void release(FontShape shape) {
        if (!built(shape)) return;

        FontSet[] stored = sets[shape.ordinal()];
        closeAll(stored);
        Arrays.fill(stored, null);
    }

    private static void closeAll(FontSet[] built) {
        for (FontSet set : built) {
            if (set != null) set.close();
        }
    }
}
