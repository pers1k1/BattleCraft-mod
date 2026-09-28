package com.persiki84.battlecraft.client.wallpaper;

import com.mojang.blaze3d.systems.RenderSystem;
import com.persiki84.battlecraft.BattleCraftMod;

import java.util.concurrent.locks.LockSupport;

public final class WallpaperPlayer {
    private static final long WAIT_NAP_NANOS = 1_000_000L;

    private static WallpaperReel reel;
    private static WallpaperTexture texture;
    private static String shownId = "";
    private static boolean uploaded;
    private static long dueAt;

    private WallpaperPlayer() {}

    public static void show(String id) {
        RenderSystem.assertOnRenderThread();
        if (reel != null && shownId.equals(id)) return;

        hide();
        WallpaperLibrary.store().opened(id);
        WallpaperMeta meta = WallpaperLibrary.store().meta(id);
        if (meta == null) return;

        texture = WallpaperTexture.create(meta.width(), meta.height(), WallpaperReel.slotsFor(meta));
        if (!texture.mapped()) {
            BattleCraftMod.LOGGER.warn("[battlecraft] wallpaper {} not shown: pixel buffer did not map", id);
            hide();
            return;
        }
        reel = WallpaperReel.start(WallpaperLibrary.store().cache(id), meta, texture.targets());
        shownId = id;
    }

    public static void hide() {
        RenderSystem.assertOnRenderThread();
        if (reel != null) reel.stop();
        if (texture != null) texture.close();
        reel = null;
        texture = null;
        shownId = "";
        uploaded = false;
    }

    public static void awaitFrame(long limitNanos) {
        RenderSystem.assertOnRenderThread();
        if (reel == null) return;
        long deadline = System.nanoTime() + limitNanos;
        while (!reel.hasFrame() && System.nanoTime() - deadline < 0L) LockSupport.parkNanos(WAIT_NAP_NANOS);
    }

    static boolean showing(String id) {
        return reel != null && shownId.equals(id);
    }

    // WHY: другой файл под тем же именем пересобирает кеш показанных обоев, а кольцо держит старые
    // WHY: кадры и размеры: без повторного открытия стол стоял бы на старой картинке до перезапуска
    public static boolean outdated() {
        return reel != null && WallpaperLibrary.store().rebuilt(shownId);
    }

    public static int texture() {
        if (reel == null) return 0;

        long now = System.nanoTime();
        reel.touch(now);
        texture.remapFinished();
        if (due(now) && reel.hasFrame()) upload(now);
        return uploaded ? texture.id() : 0;
    }

    public static boolean ready() {
        return uploaded;
    }

    public static int width() {
        return reel == null ? 0 : reel.width();
    }

    public static int height() {
        return reel == null ? 0 : reel.height();
    }

    private static boolean due(long now) {
        return !uploaded || !reel.still() && now - dueAt >= 0L;
    }

    // WHY: следующий кадр ставится на сетку от прошлого срока, а не от текущего момента, иначе
    // WHY: частота плывёт вниз на джиттере кадров рендера. Отставание больше кадра (меню было
    // WHY: скрыто, декодер спал) начинает сетку заново, а не догоняет пропущенное
    private static void upload(long now) {
        texture.upload(reel.currentSlot(), !reel.still());
        reel.advance();

        long step = reel.frameNanos();
        dueAt = uploaded && now - dueAt < step ? dueAt + step : now + step;
        uploaded = true;
    }
}
