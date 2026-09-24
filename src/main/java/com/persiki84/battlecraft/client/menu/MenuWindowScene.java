package com.persiki84.battlecraft.client.menu;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.logging.LogUtils;
import com.persiki84.shared.client.ui.UiFrame;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

// WHY: стекло окна преломляет мир за собой, и снимок берётся до руки и интерфейса: снимок HUD
// WHY: держит кадр прошлого прохода вместе с самими окнами, и стекло преломляло бы само себя
public final class MenuWindowScene {
    private static final int SHRINK = 2;

    private static TextureTarget copy;
    private static long stamp = -1L;
    private static boolean failed;

    private MenuWindowScene() {}

    public static int capture() {
        if (failed) return 0;
        if (stamp == UiFrame.frame() && copy != null) return copy.getColorTextureId();

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        try {
            build(Math.max(1, main.width / SHRINK), Math.max(1, main.height / SHRINK));
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        } catch (Throwable error) {
            failed = true;
            LogUtils.getLogger().warn("[battlecraft] menu window scene off: {}", String.valueOf(error));
            return 0;
        } finally {
            main.bindWrite(true);
        }
        stamp = UiFrame.frame();
        return copy.getColorTextureId();
    }

    private static void build(int width, int height) {
        if (copy != null && copy.width == width && copy.height == height) return;

        if (copy != null) copy.destroyBuffers();
        copy = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        copy.setFilterMode(GL11.GL_LINEAR);
        GlStateManager._bindTexture(copy.getColorTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._bindTexture(0);
    }
}
