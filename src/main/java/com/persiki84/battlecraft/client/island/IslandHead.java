package com.persiki84.battlecraft.client.island;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.lwjgl.opengl.GL11;

public final class IslandHead {
    private static final ResourceLocation TARGET = new ResourceLocation("battlecraft", "island/head");

    private static final int SKIN = 64;
    private static final int FACE = 8;
    private static final int HEAD_LEFT = 8;
    private static final int HEAD_TOP = 8;
    private static final int HAT_LEFT = 40;
    private static final int MAX_EDGE = 256;

    private static ResourceLocation source;
    private static int[] face;
    private static int span;
    private static int edge;

    private IslandHead() {}

    // WHY: голова это восемь пикселей скина, и сторона снимка кратна восьми: тогда каждый пиксель
    // WHY: занимает целое число экранных, а не гуляет то в четыре, то в пять
    public static int fit(float physical) {
        int steps = Math.round(physical / FACE);
        return Math.max(FACE, Math.min(MAX_EDGE, steps * FACE));
    }

    public static boolean prepare(ResourceLocation skin, int wanted) {
        if (!skin.equals(source)) {
            face = compose(skin);
            source = face == null ? null : skin;
            edge = 0;
        }
        if (face == null) return false;
        if (wanted != edge) bake(wanted);
        return edge == wanted;
    }

    public static ResourceLocation texture() {
        return TARGET;
    }

    private static int[] compose(ResourceLocation skin) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(skin);
        texture.bind();

        int width = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (width < SKIN || height < SKIN || width % SKIN != 0) return null;

        int unit = width / SKIN;
        int side = FACE * unit;
        int[] pixels = new int[side * side];

        try (NativeImage sheet = new NativeImage(width, height, false)) {
            sheet.downloadTexture(0, false);
            for (int y = 0; y < side; y++) {
                for (int x = 0; x < side; x++) {
                    int base = sheet.getPixelRGBA(HEAD_LEFT * unit + x, HEAD_TOP * unit + y);
                    int hat = sheet.getPixelRGBA(HAT_LEFT * unit + x, HEAD_TOP * unit + y);
                    pixels[y * side + x] = over(hat, base);
                }
            }
        } catch (Exception error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] head skin rejected: {}", error.toString());
            return null;
        }

        span = side;
        return pixels;
    }

    private static int over(int top, int base) {
        int share = FastColor.ABGR32.alpha(top);
        if (share <= 0) return base;
        if (share >= 255) return top;

        int rest = 255 - share;
        return FastColor.ABGR32.color(
                Math.max(FastColor.ABGR32.alpha(base), share),
                (FastColor.ABGR32.blue(top) * share + FastColor.ABGR32.blue(base) * rest) / 255,
                (FastColor.ABGR32.green(top) * share + FastColor.ABGR32.green(base) * rest) / 255,
                (FastColor.ABGR32.red(top) * share + FastColor.ABGR32.red(base) * rest) / 255);
    }

    private static void bake(int wanted) {
        NativeImage image = new NativeImage(wanted, wanted, false);
        for (int y = 0; y < wanted; y++) {
            int sourceRow = y * span / wanted * span;
            for (int x = 0; x < wanted; x++) {
                image.setPixelRGBA(x, y, face[sourceRow + x * span / wanted]);
            }
        }

        Minecraft.getInstance().getTextureManager().register(TARGET, new DynamicTexture(image));
        edge = wanted;
    }
}
