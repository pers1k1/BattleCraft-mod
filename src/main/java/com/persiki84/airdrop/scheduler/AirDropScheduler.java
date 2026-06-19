package com.persiki84.airdrop.scheduler;

import com.persiki84.airdrop.config.AirDropConfig;
import com.persiki84.airdrop.entity.ModEntities;
import com.persiki84.airdrop.loot.AirDropLootManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class AirDropScheduler {
    private int ticks;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        if (!AirDropConfig.SERVER.modEnabled.get()) return;

        if (!AirDropConfig.SERVER.autoSpawnEnabled.get()) {
            ticks = 0;
            return;
        }

        MinecraftServer server = e.getServer();
        if (server == null) return;

        int interval = AirDropConfig.SERVER.intervalSeconds.get() * 20;
        if (interval <= 0) return;

        ticks++;
        if (ticks < interval) return;
        ticks = 0;

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
        var rand = level.random;

        double centerX = AirDropConfig.SERVER.centerX.get();
        double centerZ = AirDropConfig.SERVER.centerZ.get();

        if (centerX == 0.0 && centerZ == 0.0) {
            BlockPos ws = level.getSharedSpawnPos();
            centerX = ws.getX();
            centerZ = ws.getZ();
        }

        double spawnX, spawnZ;
        if (forcedLandingOrNull != null) {
            spawnX = forcedLandingOrNull.getX() + 0.5;
            spawnZ = forcedLandingOrNull.getZ() + 0.5;
        } else {
            int r = AirDropConfig.SERVER.spawnRadius.get();
            spawnX = centerX + (rand.nextDouble() * 2.0 - 1.0) * r;
            spawnZ = centerZ + (rand.nextDouble() * 2.0 - 1.0) * r;
        }

        int startY = AirDropConfig.SERVER.maxSpawnY.get();

        BlockPos landingPos = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos((int)spawnX, 0, (int)spawnZ)
        );

        double distance = startY - landingPos.getY();

        int animTicks = AirDropConfig.SERVER.flyingAnimTicks.get();
        if (animTicks < 20) animTicks = 20;

        double calculatedSpeed = distance / (double) animTicks;

        var drop = ModEntities.AIRDROP.get().create(level);
        if (drop == null) return null;

        drop.moveTo(spawnX, startY, spawnZ);
        drop.setFallSpeed((float) calculatedSpeed);
        drop.setFlyingAnimTicks(animTicks);

        AirDropLootManager.fillInventory(rand, drop.getInventory(), new ResourceLocation("airdrop", "global"));

        level.addFreshEntity(drop);

        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("airdrop.scheduler.broadcast").withStyle(ChatFormatting.GREEN)
                        .append(Component.translatable("airdrop.scheduler.landing_coords", landingPos.getX(), landingPos.getZ()).withStyle(ChatFormatting.GOLD)),
                false
        );

        return landingPos;
    }
}
