package com.persiki84.airdrop.scheduler;

import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import com.persiki84.airdrop.config.AirDropConfig;
import com.persiki84.airdrop.entity.ModEntities;
import com.persiki84.airdrop.loot.AirDropLootManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class AirDropScheduler {
    private static final int TICKS_PER_SECOND = 20;
    private static final int MIN_ANIM_TICKS = 20;
    private static final int MIN_DROP_HEIGHT = 24;
    private static final int DESCENT_SLOWDOWN = 2;

    private int ticks;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!AirDropConfig.SERVER.modEnabled.get()) return;
        if (!ModuleSwitches.allows(ModuleId.AIRDROP)) return;

        if (!AirDropConfig.SERVER.autoSpawnEnabled.get()) {
            ticks = 0;
            return;
        }

        MinecraftServer server = e.getServer();
        if (server == null) return;

        int interval = AirDropConfig.SERVER.intervalSeconds.get() * TICKS_PER_SECOND;
        if (interval <= 0) return;

        ticks++;
        if (ticks < interval) return;
        ticks = 0;

        spawnRound(server);
    }

    private void spawnRound(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (!isAllowedDimension(level)) continue;
            if (level.random.nextDouble() > AirDropConfig.SERVER.intervalSpawnChance.get()) continue;

            spawn(level, null);
        }
    }

    private boolean isAllowedDimension(ServerLevel level) {
        String id = level.dimension().location().toString();
        for (String s : AirDropConfig.SERVER.allowedDimensions.get()) {
            if (id.equals(s)) return true;
        }
        return false;
    }

    public static BlockPos spawn(ServerLevel level, BlockPos forcedLandingOrNull) {
        BlockPos landingPos = landingSpot(level, forcedLandingOrNull);
        int descentTicks = descentTicks(AirDropConfig.SERVER.flyingAnimTicks.get());
        int startY = Math.max(AirDropConfig.SERVER.maxSpawnY.get(), landingPos.getY() + MIN_DROP_HEIGHT);

        var drop = ModEntities.AIRDROP.get().create(level);
        if (drop == null) return null;

        drop.moveTo(landingPos.getX() + 0.5, startY, landingPos.getZ() + 0.5);
        drop.setFallSpeed(descentSpeed(startY - landingPos.getY(), descentTicks));
        drop.setFlyingAnimTicks(descentTicks);

        AirDropLootManager.fillInventory(level.random, drop.getInventory(),
                new ResourceLocation("airdrop", "global"));
        level.addFreshEntity(drop);

        announce(level, landingPos);
        return landingPos;
    }

    public static int descentTicks(int configuredAnimTicks) {
        return Math.max(MIN_ANIM_TICKS, configuredAnimTicks) * DESCENT_SLOWDOWN;
    }

    public static float descentSpeed(double distance, int descentTicks) {
        return (float) (distance / descentTicks);
    }

    private static BlockPos landingSpot(ServerLevel level, BlockPos forcedLandingOrNull) {
        if (forcedLandingOrNull != null) {
            return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, forcedLandingOrNull);
        }

        BlockPos center = spawnCenter(level);
        int radius = AirDropConfig.SERVER.spawnRadius.get();
        int x = center.getX() + (int) ((level.random.nextDouble() * 2.0 - 1.0) * radius);
        int z = center.getZ() + (int) ((level.random.nextDouble() * 2.0 - 1.0) * radius);

        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
    }

    private static BlockPos spawnCenter(ServerLevel level) {
        if (AirDropConfig.SERVER.centerAtWorldSpawn.get()) {
            return level.getSharedSpawnPos();
        }
        return new BlockPos((int) (double) AirDropConfig.SERVER.centerX.get(), 0,
                (int) (double) AirDropConfig.SERVER.centerZ.get());
    }

    private static void announce(ServerLevel level, BlockPos landingPos) {
        Component message = Component.translatable("airdrop.scheduler.broadcast").withStyle(ChatFormatting.GREEN)
                .append(Component.translatable("airdrop.scheduler.landing_coords",
                        landingPos.getX(), landingPos.getZ()).withStyle(ChatFormatting.GOLD));

        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }
}
