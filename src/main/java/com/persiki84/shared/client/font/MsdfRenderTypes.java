package com.persiki84.shared.client.font;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public class MsdfRenderTypes extends RenderType {

    private MsdfRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                            boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    public static GlyphRenderTypes forTexture(FontShape shape, ResourceLocation texture) {
        String base = shape.id() + "_text";
        return new GlyphRenderTypes(
                build(base, shape, texture, false, false),
                build(base + "_see_through", shape, texture, true, false),
                build(base + "_offset", shape, texture, false, true));
    }

    private static RenderType build(String name, FontShape shape, ResourceLocation texture,
                                    boolean seeThrough, boolean offset) {
        CompositeState.CompositeStateBuilder state = CompositeState.builder()
                .setShaderState(new ShaderStateShard(MsdfShaders.prepared(shape)))
                .setTextureState(new TextureStateShard(texture, true, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setDepthTestState(seeThrough ? NO_DEPTH_TEST : LEQUAL_DEPTH_TEST)
                .setWriteMaskState(seeThrough ? COLOR_WRITE : COLOR_DEPTH_WRITE);

        if (offset) {
            state.setLayeringState(POLYGON_OFFSET_LAYERING);
        }

        return create(name, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS,
                256, false, true, state.createCompositeState(false));
    }
}
