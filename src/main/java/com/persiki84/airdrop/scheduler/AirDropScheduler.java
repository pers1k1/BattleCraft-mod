package com.persiki84.airdrop.scheduler;

import com.persiki84.airdrop.AirDropMod;
import com.persiki84.airdrop.config.AirDropConfig;
import com.persiki84.airdrop.entity.AirDropEntity;
import com.persiki84.airdrop.entity.ModEntities;
import com.persiki84.airdrop.loot.LootRoller;
import com.persiki84.airdrop.loot.LootTables;
import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.MatchEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import static com.persiki84.airdrop.config.AirDropLimits.TICKS_PER_SECOND;

public final class AirDropScheduler {
    private static final int MIN_DESCENT_TICKS = 20;
    private static final int MIN_DROP_HEIGHT = 24;

    private int ticks;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !AirDropMod.enabled()) return;
        if (!AirDropConfig.SERVER.autoSpawnEnabled.get() || !timerRuns()) {
            ticks = 0;
            return;
        }

        ticks++;
        if (ticks < AirDropConfig.SERVER.intervalSeconds.get() * TICKS_PER_SECOND) return;
        ticks = 0;
        spawnRound(e.getServer());
    }

    @SubscribeEvent
    public void onMatchStarted(MatchEvent.Started event) {
        ticks = 0;
    }

    private static boolean timerRuns() {
        return !AirDropConfig.SERVER.matchOnly.get() || BattleCraftManager.getInstance().matchRunning();
    }

    private void spawnRound(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (!isAllowedDimension(level) || level.players().isEmpty()) continue;
            if (level.random.nextDouble() > AirDropConfig.SERVER.intervalSpawnChance.get()) continue;

            spawn(level, null);
        }
    }

    private static boolean isAllowedDimension(ServerLevel level) {
        String id = level.dimension().location().toString();
        return AirDropConfig.SERVER.allowedDimensions.get().contains(id);
    }

    // WHY: ручной и плановый сброс идут одним путём: раньше у ручного была своя копия, которая
    // WHY: не объявляла координаты и округляла отрицательные координаты к нулю на блок мимо
    public static BlockPos spawn(ServerLevel level, BlockPos forcedOrNull) {
        BlockPos landing = landingSpot(level, forcedOrNull);
        int descentTicks = Math.max(MIN_DESCENT_TICKS, AirDropConfig.SERVER.flyingAnimTicks.get());
        int startY = Math.max(AirDropConfig.SERVER.maxSpawnY.get(), landing.getY() + MIN_DROP_HEIGHT);

        AirDropEntity drop = ModEntities.AIRDROP.get().create(level);
        if (drop == null) return null;

        drop.moveTo(landing.getX() + 0.5, startY, landing.getZ() + 0.5, level.random.nextFloat() * 360.0f, 0.0f);
        drop.setFallSpeed((float) (startY - landing.getY()) / descentTicks);
        drop.setFlyingAnimTicks(descentTicks);
        drop.matchSpawned(BattleCraftManager.getInstance().getPhase() == BattleCraftManager.GamePhase.ACTIVE);
        LootRoller.fill(drop.getInventory(), LootTables.getOrEmpty(AirDropConfig.airdropTable()), level.random);
        level.addFreshEntity(drop);

        announce(level, landing);
        return landing;
    }

    private static BlockPos landingSpot(ServerLevel level, BlockPos forcedOrNull) {
        if (forcedOrNull != null) {
            return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, forcedOrNull);
        }

        BlockPos center = spawnCenter(level);
        int radius = AirDropConfig.SERVER.spawnRadius.get();
        int x = center.getX() + (int) Math.round((level.random.nextDouble() * 2.0 - 1.0) * radius);
        int z = center.getZ() + (int) Math.round((level.random.nextDouble() * 2.0 - 1.0) * radius);
        BlockPos inside = level.getWorldBorder().clampToBounds(x, 0, z);
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, inside);
    }

    private static BlockPos spawnCenter(ServerLevel level) {
        if (AirDropConfig.SERVER.centerAtWorldSpawn.get()) return level.getSharedSpawnPos();

        return BlockPos.containing(AirDropConfig.SERVER.centerX.get(), 0.0, AirDropConfig.SERVER.centerZ.get());
    }

    private static void announce(ServerLevel level, BlockPos landing) {
        if (!AirDropConfig.SERVER.announceCoords.get()) {
            broadcast(level, Component.translatable("airdrop.scheduler.broadcast_quiet").withStyle(ChatFormatting.GREEN));
            return;
        }
        broadcast(level, Component.translatable("airdrop.scheduler.broadcast", landing.getX(), landing.getZ())
                .withStyle(ChatFormatting.GREEN));
    }

    private static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }
}
