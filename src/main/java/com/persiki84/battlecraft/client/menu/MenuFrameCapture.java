package com.persiki84.battlecraft.client.menu;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.network.C2SMenuFramePacket;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.shared.client.menu.GlassScreen;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// WHY: кадр окна уходит не потоком, а по изменению: миниатюра сравнивается с прошлой пробой и с
// WHY: последним отправленным, и кадр шлётся, только когда окно поменялось и уже успокоилось, то есть
// WHY: прокрутка, наведение и въезд строк не уходят вовсе, уходит их итог
@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class MenuFrameCapture {
    private static final long SAMPLE_MS = 250L;
    private static final long GAP_MS = 700L;
    private static final int PIXEL_STEP = 18;
    private static final float STILL_SHARE = 0.01f;
    private static final float CHANGE_SHARE = 0.012f;
    private static final double AUDIENCE_REACH = 16.0;

    private static final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "battlecraft-menu-frame");
        thread.setDaemon(true);
        return thread;
    });

    private static Screen watched;
    private static int[] previous;
    private static int[] sent;
    private static long sampled;
    private static long lastSent;
    private static boolean encoding;
    private static boolean mirroredBefore;

    private MenuFrameCapture() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof GlassScreen glass) || !wanted(glass)) return;

        if (glass != watched) {
            watched = glass;
            previous = null;
            sent = null;
        }
        long now = Util.getMillis();
        if (encoding || now - sampled < SAMPLE_MS) return;

        sampled = now;
        boolean mirrored = mirrored();
        if (mirrored != mirroredBefore) sent = null;
        mirroredBefore = mirrored;
        if (!mirrored && !audience()) {
            sent = null;
            return;
        }
        sample(glass, now);
    }

    private static boolean wanted(GlassScreen glass) {
        Minecraft client = Minecraft.getInstance();
        return client.level != null && client.getConnection() != null && glass.broadcast() && glass.settled();
    }

    // WHY: от третьего лица игрок видит своё окно в мире, и там должен быть его кадр, а не каркас;
    // WHY: себе кадр ставится на месте, без сервера
    private static boolean mirrored() {
        return !Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    // WHY: кадр видят только тиммейты рядом, и без них отправка была бы трафиком в пустоту
    private static boolean audience() {
        LocalPlayer self = Minecraft.getInstance().player;
        if (self == null) return false;

        for (Player other : self.level().players()) {
            if (other != self && self.isAlliedTo(other)
                    && other.distanceToSqr(self) <= AUDIENCE_REACH * AUDIENCE_REACH) return true;
        }
        return false;
    }

    private static void sample(GlassScreen glass, long now) {
        int[] thumb = MenuFrameGrab.sample(glass.frame());
        if (thumb == null) return;

        boolean still = previous != null && share(thumb, previous) <= STILL_SHARE;
        previous = thumb;
        if (!still || now - lastSent < GAP_MS) return;
        if (sent != null && share(thumb, sent) <= CHANGE_SHARE) return;

        sent = thumb;
        lastSent = now;
        ship(MenuFrameGrab.pixels(), MenuFrameGrab.width(), MenuFrameGrab.height());
    }

    private static void ship(ByteBuffer pixels, int width, int height) {
        encoding = true;
        worker.execute(() -> {
            byte[] image = null;
            try {
                image = MenuFrameCodec.encode(pixels, width, height);
            } catch (Throwable ignored) {
            } finally {
                MemoryUtil.memFree(pixels);
            }
            byte[] encoded = image;
            Minecraft.getInstance().execute(() -> deliver(encoded));
        });
    }

    private static void deliver(byte[] image) {
        encoding = false;
        if (image == null) {
            sent = null;
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) return;
        if (!(client.screen instanceof GlassScreen glass) || glass != watched || client.player == null) return;

        if (mirrored()) MenuFrameClient.accept(client.player.getUUID(), image);
        if (audience()) PacketHandler.INSTANCE.sendToServer(new C2SMenuFramePacket(image));
    }

    private static float share(int[] first, int[] second) {
        int changed = 0;
        for (int index = 0; index < first.length; index++) {
            if (Math.abs(first[index] - second[index]) > PIXEL_STEP) changed++;
        }
        return (float) changed / first.length;
    }
}
