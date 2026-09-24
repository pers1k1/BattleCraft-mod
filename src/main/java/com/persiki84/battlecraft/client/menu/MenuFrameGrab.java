package com.persiki84.battlecraft.client.menu;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.persiki84.shared.client.menu.GlassScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

// WHY: окно ужимается до кадра половинными шагами: один линейный блит в несколько раз меньше
// WHY: берёт по четыре текселя на точку, и буквы окна рассыпались бы в шум
public final class MenuFrameGrab {
    public static final int THUMB_WIDTH = 48;
    public static final int THUMB_HEIGHT = 32;

    private static final int FRAME_WIDE = 384;
    private static final int FRAME_TALL = 288;
    private static final int SMALLEST = 16;
    private static final int KEPT_STEPS = 12;
    private static final int RGBA = 4;

    private static final Map<Long, TextureTarget> steps = new HashMap<>();
    private static final ByteBuffer thumbPixels = MemoryUtil.memAlloc(THUMB_WIDTH * THUMB_HEIGHT * RGBA);
    private static TextureTarget frame;
    private static TextureTarget thumb;

    private MenuFrameGrab() {}

    public static int[] sample(GlassScreen.Area area) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int[] box = deviceBox(area, main);
        if (box == null) return null;

        try {
            shrink(main.frameBufferId, box);
            thumb = sized(thumb, THUMB_WIDTH, THUMB_HEIGHT);
            blit(frame.frameBufferId, 0, 0, frame.width, frame.height, thumb);
            read(thumb, thumbPixels);
            return luminance(thumbPixels);
        } finally {
            main.bindWrite(true);
        }
    }

    public static ByteBuffer pixels() {
        ByteBuffer out = MemoryUtil.memAlloc(frame.width * frame.height * RGBA);
        try {
            read(frame, out);
        } finally {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
        return out;
    }

    public static int width() {
        return frame.width;
    }

    public static int height() {
        return frame.height;
    }

    private static int[] deviceBox(GlassScreen.Area area, RenderTarget main) {
        Window window = Minecraft.getInstance().getWindow();
        double gui = Math.max(1.0, window.getGuiScale());
        int left = Math.max(0, (int) Math.floor(area.x() * gui));
        int right = Math.min(main.width, (int) Math.ceil((area.x() + area.width()) * gui));
        int bottom = Math.max(0, main.height - (int) Math.ceil((area.y() + area.height()) * gui));
        int top = Math.min(main.height, main.height - (int) Math.floor(area.y() * gui));
        if (right - left < SMALLEST || top - bottom < SMALLEST) return null;
        return new int[]{left, bottom, right, top};
    }

    private static void shrink(int source, int[] box) {
        int width = box[2] - box[0];
        int height = box[3] - box[1];
        float scale = Math.min(1.0f, Math.min((float) FRAME_WIDE / width, (float) FRAME_TALL / height));
        int wantWidth = Math.max(1, Math.round(width * scale));
        int wantHeight = Math.max(1, Math.round(height * scale));

        int from = source;
        int x = box[0];
        int y = box[1];
        while (width > wantWidth * 2 || height > wantHeight * 2) {
            TextureTarget half = step((width + 1) / 2, (height + 1) / 2);
            blit(from, x, y, width, height, half);
            from = half.frameBufferId;
            x = 0;
            y = 0;
            width = half.width;
            height = half.height;
        }
        frame = sized(frame, wantWidth, wantHeight);
        blit(from, x, y, width, height, frame);
    }

    private static TextureTarget step(int width, int height) {
        long key = ((long) width << 32) | height;
        TextureTarget cached = steps.get(key);
        if (cached != null) return cached;

        if (steps.size() >= KEPT_STEPS) {
            for (TextureTarget target : steps.values()) target.destroyBuffers();
            steps.clear();
        }
        TextureTarget created = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        created.setFilterMode(GL11.GL_LINEAR);
        steps.put(key, created);
        return created;
    }

    private static TextureTarget sized(TextureTarget current, int width, int height) {
        if (current != null && current.width == width && current.height == height) return current;

        if (current != null) current.destroyBuffers();
        TextureTarget created = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        created.setFilterMode(GL11.GL_LINEAR);
        return created;
    }

    private static void blit(int from, int x, int y, int width, int height, TextureTarget to) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GL30.glBlitFramebuffer(x, y, x + width, y + height, 0, 0, to.width, to.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
    }

    private static void read(TextureTarget from, ByteBuffer into) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        into.clear();
        GL11.glReadPixels(0, 0, from.width, from.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, into);
    }

    private static int[] luminance(ByteBuffer pixels) {
        int[] values = new int[THUMB_WIDTH * THUMB_HEIGHT];
        for (int index = 0; index < values.length; index++) {
            int red = pixels.get(index * RGBA) & 0xFF;
            int green = pixels.get(index * RGBA + 1) & 0xFF;
            int blue = pixels.get(index * RGBA + 2) & 0xFF;
            values[index] = (red * 54 + green * 183 + blue * 19) >> 8;
        }
        return values;
    }
}
