package com.persiki84.shared.client.font.raster;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.awt.image.BufferedImage;

public final class RasterPage implements AutoCloseable {
    public static final int SIZE = 1024;

    private static final int GAP = 2;

    private final ResourceLocation location;
    private final NativeImage image;
    private final DynamicTexture texture;
    private final GlyphRenderTypes renderTypes;

    private int cursorX = GAP;
    private int cursorY = GAP;
    private int shelfHeight;
    private boolean dirty;

    RasterPage(ResourceLocation location, GlyphRenderTypes renderTypes) {
        this.location = location;
        this.image = new NativeImage(SIZE, SIZE, true);
        this.texture = new DynamicTexture(image);
        this.renderTypes = renderTypes;
        Minecraft.getInstance().getTextureManager().register(location, texture);
    }

    public GlyphRenderTypes renderTypes() {
        return renderTypes;
    }
    Slot allocate(int width, int height) {
        if (width + GAP * 2 > SIZE || height + GAP * 2 > SIZE) return null;

        if (cursorX + width + GAP > SIZE) {
            cursorX = GAP;
            cursorY += shelfHeight + GAP;
            shelfHeight = 0;
        }
        if (cursorY + height + GAP > SIZE) return null;

        Slot slot = new Slot(cursorX, cursorY, width, height);
        cursorX += width + GAP;
        shelfHeight = Math.max(shelfHeight, height);
        return slot;
    }

    void copy(BufferedImage source, Slot slot) {
        for (int y = 0; y < slot.height(); y++) {
            for (int x = 0; x < slot.width(); x++) {
                image.setPixelRGBA(slot.x() + x, slot.y() + y, toAbgr(source.getRGB(x, y)));
            }
        }
        dirty = true;
    }

    // WHY: страйк печётся с шестикратным запасом, а мелкий кегль ужимается до трети от него.
    // WHY: NEAREST при таком сжатии выбрасывает столбцы пикселей и превращает буквы в месиво,
    // WHY: поэтому фильтр ставится линейным заново после каждой заливки: upload его сбрасывает
    public void uploadIfDirty() {
        if (!dirty) return;

        dirty = false;
        texture.upload();
        texture.setFilter(true, false);
    }

    @Override
    public void close() {
        Minecraft.getInstance().getTextureManager().release(location);
    }

    private static int toAbgr(int argb) {
        int alpha = argb >>> 24 & 0xFF;
        int red = argb >>> 16 & 0xFF;
        int green = argb >>> 8 & 0xFF;
        int blue = argb & 0xFF;
        return alpha << 24 | blue << 16 | green << 8 | red;
    }

    record Slot(int x, int y, int width, int height) {

        float u0() {
            return (float) x / SIZE;
        }

        float v0() {
            return (float) y / SIZE;
        }

        float u1() {
            return (float) (x + width) / SIZE;
        }

        float v1() {
            return (float) (y + height) / SIZE;
        }
    }
}
