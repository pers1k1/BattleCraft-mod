package com.persiki84.battlecraft.pulse;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.battlecraft.network.S2CServerPulsePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

// WHY: TPS считается по стене, а не из средней длины тика: сервер, который не успевает, гонит тики
// WHY: подряд без сна, и длина тика 40 мс при отставании не значит 20 TPS. Средняя длина тика
// WHY: (getAverageTickTime) идёт отдельным числом: она показывает запас, пока TPS ещё полный
@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID)
public final class ServerPulse {
    private static final long WINDOW_NANOS = 1_000_000_000L;
    // WHY: встроенный сервер на паузе не зовёт super.tickServer, событие тика не приходит, а стена
    // WHY: идёт; пауза бывает только у неопубликованного мира (Minecraft.isPaused требует
    // WHY: !isPublished), поэтому разрыв между тиками отбрасывается только там. Выделенный сервер
    // WHY: на паузу не встаёт, и его зависание обязано дойти до игроков красной цифрой
    private static final long PAUSE_GAP_NANOS = 250_000_000L;
    private static final long NEVER = 0L;
    private static final float FULL_RATE = 20.0f;
    private static final float NANOS_PER_SECOND = 1.0E9f;

    private static long windowStart = NEVER;
    private static long lastEnd = NEVER;
    private static int ticks;
    private static boolean measured;
    private static float rate = FULL_RATE;
    private static float spent;

    private ServerPulse() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        windowStart = NEVER;
        lastEnd = NEVER;
        ticks = 0;
        measured = false;
    }

    // WHY: отметка окна ставится в начале тика: начала тиков стоят на сетке nextTickTime, а конец
    // WHY: гуляет на длину тика, и разница длин тиков на краях окна давала здоровому серверу 19.2-19.9
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        long now = System.nanoTime();
        if (event.phase == TickEvent.Phase.END) {
            lastEnd = now;
            return;
        }
        if (windowStart == NEVER || resumedFromPause(event.getServer(), now)) {
            restart(now);
            return;
        }
        ticks++;
        long elapsed = now - windowStart;
        if (elapsed < WINDOW_NANOS) return;

        measure(event.getServer(), elapsed);
        restart(now);
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), current());
    }

    private static boolean resumedFromPause(MinecraftServer server, long now) {
        return !server.isPublished() && lastEnd != NEVER && now - lastEnd > PAUSE_GAP_NANOS;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!measured || !(event.getEntity() instanceof ServerPlayer player)) return;
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), current());
    }

    private static void measure(MinecraftServer server, long elapsed) {
        rate = Math.min(FULL_RATE, ticks * NANOS_PER_SECOND / elapsed);
        spent = server.getAverageTickTime();
        measured = true;
    }

    private static void restart(long now) {
        windowStart = now;
        ticks = 0;
    }

    private static S2CServerPulsePacket current() {
        return new S2CServerPulsePacket(rate, spent);
    }
}
