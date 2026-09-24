package com.persiki84.zones.event;

import com.persiki84.shared.zone.ZoneArea;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneLookup;
import com.persiki84.zones.ZoneRegistry;
import com.persiki84.zones.ZoneType;
import com.persiki84.zones.ZonesMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

@Mod.EventBusSubscriber(modid = ZonesMod.MOD_ID)
public class ZoneSpawnHandler {
    private static final Random RANDOM = new Random();
    private static final int PLACEMENT_ATTEMPTS = 8;

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide || !ModuleSwitches.allows(ModuleId.ZONES)) return;

        Zone spawn = spawnZoneFor(player);
        if (spawn == null) return;

        ServerLevel level = levelOf(player, spawn);
        BlockPos target = spawn.spawnsAtAnchor()
                ? surfaceAt(level, spawn.spawnAnchor())
                : pickPlacement(level, spawn.area());
        player.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                player.getYRot(), player.getXRot());
    }

    // WHY: у базы есть свой мир, и возрождение обязано вести в него: иначе игрок, умерший в аду,
    // WHY: вставал бы по координатам базы, но в аду
    private static ServerLevel levelOf(ServerPlayer player, Zone spawn) {
        if (spawn.dimension() == null) return player.serverLevel();

        ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, spawn.dimension()));
        return level == null ? player.serverLevel() : level;
    }

    private static Zone spawnZoneFor(ServerPlayer player) {
        String team = ZoneLookup.teamNameOf(player);
        if (team == null) return null;

        for (Zone zone : ZoneRegistry.all()) {
            if (zone.type() == ZoneType.BASE && zone.belongsTo(team)) return zone;
        }
        return null;
    }

    private static BlockPos pickPlacement(ServerLevel level, ZoneArea area) {
        BlockPos fallback = surfaceAt(level, area.center());
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            BlockPos candidate = surfaceAt(level, randomInside(area));
            if (area.contains(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5)) return candidate;
        }
        return fallback;
    }

    private static BlockPos randomInside(ZoneArea area) {
        double offsetX = (RANDOM.nextDouble() * 2.0 - 1.0) * area.size();
        double offsetZ = (RANDOM.nextDouble() * 2.0 - 1.0) * area.size();
        return BlockPos.containing(area.centerX() + offsetX, area.center().getY(), area.centerZ() + offsetZ);
    }

    private static BlockPos surfaceAt(ServerLevel level, BlockPos pos) {
        level.getChunkAt(pos);
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
        return surface.getY() > level.getMinBuildHeight() ? surface : pos;
    }
}
