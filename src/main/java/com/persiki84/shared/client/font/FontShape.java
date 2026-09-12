package com.persiki84.shared.client.font;

import net.minecraft.resources.ResourceLocation;

public enum FontShape {
    CRISP("crisp", "msdf_text", Source.FIELD, 0.0f),
    SMOOTH("smooth", "soft_text", Source.FIELD, 0.0f),
    RASTER("raster", "raster_text", Source.RASTER, 0.0f),
    SHARP("sharp", "sharp_text", Source.RASTER, 0.0f),
    VELVET("velvet", "velvet_text", Source.RASTER, 0.30f);

    public enum Source {
        FIELD,
        RASTER
    }

    public static final FontShape FALLBACK = SHARP;

    private static final String FIELD_FOLDER = "msdf";

    private final String id;
    private final String shader;
    private final Source source;
    private final float dilation;

    FontShape(String id, String shader, Source source, float dilation) {
        this.id = id;
        this.shader = shader;
        this.source = source;
        this.dilation = dilation;
    }

    public String id() {
        return id;
    }
    public boolean rasterized() {
        return source == Source.RASTER;
    }

    // WHY: усиление штриха максимум-фильтром делается на бейке, а не в шейдере: там оно
    // WHY: стоит одного прохода по глифу за всю игру вместо девяти тапов на каждый пиксель
    public float dilation() {
        return dilation;
    }

    public String field() {
        return FIELD_FOLDER;
    }

    public ResourceLocation metadata(String face) {
        return new ResourceLocation("battlecraft", FIELD_FOLDER + "/" + FIELD_FOLDER + "_" + face + ".json");
    }

    public ResourceLocation texture(String face) {
        return new ResourceLocation("battlecraft", FIELD_FOLDER + "/" + FIELD_FOLDER + "_" + face + ".png");
    }

    public ResourceLocation shader() {
        return new ResourceLocation("battlecraft", shader);
    }

    public String translationKey() {
        return "battlecraft.font." + id;
    }

    public String noteKey() {
        return "battlecraft.font." + id + ".note";
    }

    public static FontShape byId(String id) {
        for (FontShape shape : values()) {
            if (shape.id.equals(id)) return shape;
        }
        return FALLBACK;
    }
}
