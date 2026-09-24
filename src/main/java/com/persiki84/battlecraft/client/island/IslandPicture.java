package com.persiki84.battlecraft.client.island;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

public final class IslandPicture extends AbstractTexture {
    private final int edge;

    private IslandPicture(NativeImage[] levels) {
        RenderSystem.assertOnRenderThread();
        edge = levels[0].getWidth();
        TextureUtil.prepareImage(getId(), levels.length - 1, edge, edge);
        for (int level = 0; level < levels.length; level++) {
            NativeImage image = levels[level];
            image.upload(level, 0, 0, 0, 0, image.getWidth(), image.getHeight(), true, true, true, true);
        }
    }

    public static IslandPicture upload(NativeImage[] levels) {
        return new IslandPicture(levels);
    }

    public static void discard(NativeImage[] levels) {
        if (levels == null) return;
        for (NativeImage level : levels) {
            if (level != null) level.close();
        }
    }

    public int edge() {
        return edge;
    }

    @Override
    public void load(ResourceManager manager) {
    }

    @Override
    public void close() {
        releaseId();
    }
}
