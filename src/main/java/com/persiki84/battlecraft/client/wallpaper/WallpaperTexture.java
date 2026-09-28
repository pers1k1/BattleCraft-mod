package com.persiki84.battlecraft.client.wallpaper;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;

import java.util.concurrent.atomic.AtomicLongArray;

final class WallpaperTexture {
    private static final long NOT_MAPPED = 0L;
    private static final long NO_FENCE = 0L;
    private static final int REFILL_ACCESS = GL30C.GL_MAP_WRITE_BIT | GL30C.GL_MAP_UNSYNCHRONIZED_BIT;

    private final int id;
    private final int width;
    private final int height;
    private final long bytes;
    private final int[] buffers;
    private final long[] fences;
    private final AtomicLongArray targets;

    private WallpaperTexture(int id, int width, int height, int slots) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.bytes = (long) width * height * 4L;
        this.buffers = new int[slots];
        this.fences = new long[slots];
        this.targets = new AtomicLongArray(slots);
    }

    static WallpaperTexture create(int width, int height, int slots) {
        RenderSystem.assertOnRenderThread();
        int id = TextureUtil.generateTextureId();
        TextureUtil.prepareImage(id, 0, width, height);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        WallpaperTexture texture = new WallpaperTexture(id, width, height, slots);
        try {
            for (int slot = 0; slot < slots; slot++) texture.allocate(slot);
        } finally {
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        }
        return texture;
    }

    private void allocate(int slot) {
        buffers[slot] = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffers[slot]);
        GlStateManager._glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER, bytes, GL15.GL_STREAM_DRAW);
        targets.set(slot, GL30C.nglMapBufferRange(GL21.GL_PIXEL_UNPACK_BUFFER, 0L, bytes, GL30C.GL_MAP_WRITE_BIT));
    }

    int id() {
        return id;
    }

    AtomicLongArray targets() {
        return targets;
    }

    boolean mapped() {
        for (int slot = 0; slot < targets.length(); slot++) {
            if (targets.get(slot) == NOT_MAPPED) return false;
        }
        return true;
    }

    // WHY: заливка идёт из буфера распаковки: glTexSubImage2D из памяти клиента копирует 33 МБ
    // WHY: кадра 4K прямо в вызове, это 11-15 мс рендер-потока. Из PBO копия уходит драйверу
    // WHY: асинхронно, но отображение буфера сразу после неё ждёт эту копию ещё 7 мс, поэтому
    // WHY: буфер отображается заново только когда его fence просигналил, см. remapFinished
    void upload(int slot, boolean again) {
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffers[slot]);
        try {
            GlStateManager._glUnmapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER);
            targets.set(slot, NOT_MAPPED);
            GlStateManager._bindTexture(id);
            unpackTightly();
            GlStateManager._texSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, 0L);
        } finally {
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        }
        if (again) fences[slot] = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        else deleteBuffer(slot);
    }

    // WHY: копия из буфера закончилась, значит его можно отображать без синхронизации: драйвер
    // WHY: ничего не ждёт, а поток декода пишет следующий кадр в ту же память
    void remapFinished() {
        for (int slot = 0; slot < fences.length; slot++) {
            if (fences[slot] == NO_FENCE || !signaled(fences[slot])) continue;

            GL32C.glDeleteSync(fences[slot]);
            fences[slot] = NO_FENCE;
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffers[slot]);
            targets.set(slot, GL30C.nglMapBufferRange(GL21.GL_PIXEL_UNPACK_BUFFER, 0L, bytes, REFILL_ACCESS));
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        }
    }

    private static boolean signaled(long fence) {
        int status = GL32C.glClientWaitSync(fence, 0, 0L);
        return status == GL32C.GL_ALREADY_SIGNALED || status == GL32C.GL_CONDITION_SATISFIED;
    }

    // WHY: NativeImage.upload выставляет длину строки и пропуски под себя и не возвращает их
    private static void unpackTightly() {
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
    }

    private void deleteBuffer(int slot) {
        if (fences[slot] != NO_FENCE) GL32C.glDeleteSync(fences[slot]);
        fences[slot] = NO_FENCE;
        if (buffers[slot] == 0) return;

        if (targets.get(slot) != NOT_MAPPED) {
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffers[slot]);
            GlStateManager._glUnmapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER);
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            targets.set(slot, NOT_MAPPED);
        }
        GlStateManager._glDeleteBuffers(buffers[slot]);
        buffers[slot] = 0;
    }

    void close() {
        for (int slot = 0; slot < buffers.length; slot++) deleteBuffer(slot);
        TextureUtil.releaseTextureId(id);
    }
}
