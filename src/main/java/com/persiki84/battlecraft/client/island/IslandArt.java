package com.persiki84.battlecraft.client.island;

import com.mojang.blaze3d.platform.NativeImage;
import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IslandArt {
    private static final ResourceLocation[] SLOTS = {
            new ResourceLocation("battlecraft", "island/art_a"),
            new ResourceLocation("battlecraft", "island/art_b")
    };

    private static final long NOTHING = Long.MIN_VALUE;
    private static final long RETRY_MS = 1000L;

    private static volatile boolean ready;
    private static volatile boolean carried;
    private static volatile int slot;
    private static volatile int edge = 1;
    private static volatile int carriedEdge = 1;
    private static volatile long wantedStamp = NOTHING;
    private static volatile long failedStamp = NOTHING;
    private static volatile long failedAt;

    private IslandArt() {}

    public static void accept(long stamp, Path file) {
        if (stamp == wantedStamp || retriedRecently(stamp)) return;

        wantedStamp = stamp;
        if (stamp <= 0L) {
            drop();
            return;
        }
        Util.backgroundExecutor().execute(() -> prepare(stamp, file));
    }

    // WHY: обложка держится за стемпом моста, а мост живёт дольше сеанса в мире: после выхода в меню
    // WHY: он присылает тот же стемп, и без сброса ожидания обложка второй раз не грузится - в
    // WHY: карточке трека остаётся аватар Discord до самой смены песни
    public static void forget() {
        wantedStamp = NOTHING;
        failedStamp = NOTHING;
        drop();
    }

    private static void drop() {
        ready = false;
        carried = false;
        IslandTone.forget();
    }

    // WHY: неудачная подготовка снимает ожидание: иначе тот же стемп уже не примут, и трек доиграет
    // WHY: без обложки, хотя файл появился через полсекунды
    private static void missed(long stamp) {
        if (stamp != wantedStamp) return;

        wantedStamp = NOTHING;
        failedStamp = stamp;
        failedAt = System.currentTimeMillis();
    }

    // WHY: файл, который не декодируется, повторялся каждый кадр: сотня фоновых разборов в секунду
    // WHY: минутами. Повтор того же стемпа не чаще раза в секунду
    private static boolean retriedRecently(long stamp) {
        return stamp == failedStamp && System.currentTimeMillis() - failedAt < RETRY_MS;
    }

    // WHY: обложка, которую не удалось разобрать, не должна оставлять на месте картинку прошлого
    // WHY: трека: остров возвращается к аватару
    private static void abandon(long stamp) {
        if (wantedStamp == NOTHING && failedStamp == stamp) drop();
    }

    public static boolean ready() {
        return ready;
    }

    public static boolean pending() {
        return wantedStamp > 0L;
    }

    public static boolean carries() {
        return carried;
    }

    public static int edge() {
        return edge;
    }

    public static int carriedEdge() {
        return carriedEdge;
    }

    public static ResourceLocation texture() {
        return SLOTS[slot];
    }

    public static ResourceLocation carriedTexture() {
        return SLOTS[slot ^ 1];
    }

    // WHY: чтение с диска, декод, обрезка карточки, снятие цветов и скругление это единицы
    // WHY: миллисекунд каждое, и на смене трека они складывались в заметный провал кадра, потому
    // WHY: что шли прямо в рендер-потоке. В кадре осталась только заливка текстуры в видеопамять
    private static void prepare(long stamp, Path file) {
        if (!Files.isRegularFile(file)) {
            missed(stamp);
            return;
        }

        NativeImage decoded = null;
        NativeImage squared = null;
        NativeImage[] levels = null;
        try (InputStream stream = Files.newInputStream(file)) {
            decoded = NativeImage.read(stream);
            squared = IslandImage.squared(decoded);
            IslandTone.read(squared);
            levels = IslandScale.chain(squared, IslandImage.CORNER_SHARE);
            NativeImage[] carriedLevels = levels;
            Minecraft.getInstance().execute(() -> hold(stamp, carriedLevels));
        } catch (Exception error) {
            IslandPicture.discard(levels);
            missed(stamp);
            Minecraft.getInstance().execute(() -> abandon(stamp));
            BattleCraftMod.LOGGER.warn("[battlecraft] cover art rejected: {}", error.toString());
        } finally {
            if (decoded != null) decoded.close();
            if (squared != null) squared.close();
        }
    }

    // WHY: обложки занимают две ячейки по очереди: на перевороте уходящая ещё рисуется на лицевой
    // WHY: стороне, а запись новой по тому же адресу закрыла бы её текстуру прямо посреди хода.
    // WHY: снимок, устаревший за время подготовки, выбрасывается: фоновые загрузки могут прийти
    // WHY: не в том порядке, в каком их просили
    private static void hold(long stamp, NativeImage[] levels) {
        if (stamp != wantedStamp) {
            IslandPicture.discard(levels);
            return;
        }

        IslandPicture picture;
        try {
            picture = IslandPicture.upload(levels);
        } catch (Exception error) {
            IslandPicture.discard(levels);
            missed(stamp);
            BattleCraftMod.LOGGER.warn("[battlecraft] cover art upload failed: {}", error.toString());
            return;
        }

        int next = ready ? slot ^ 1 : slot;
        Minecraft.getInstance().getTextureManager().register(SLOTS[next], picture);
        carried = ready;
        carriedEdge = edge;
        slot = next;
        edge = picture.edge();
        ready = true;
        if (carried) IslandFlip.begin(IslandOrder.pending());
    }
}
