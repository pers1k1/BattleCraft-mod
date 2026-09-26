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
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

@Mod.EventBusSubscriber(modid = ZonesMod.MOD_ID)
public class ZoneSpawnHandler {
    private static final Random RANDOM = new Random();
    private static final int PLACEMENT_ATTEMPTS = 8;
    private static final int HEADROOM_BLOCKS = 2;

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide || !ModuleSwitches.allows(ModuleId.ZONES)) return;

        Zone spawn = spawnZoneFor(player);
        if (spawn == null) return;

        ServerLevel level = levelOf(player, spawn);
        BlockPos target = spawnTarget(level, player, spawn);
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

    // WHY: высоту якоря задал оператор, и она значима: поиск по карте высот уводил из бункера
    // WHY: на крышу, а в Незере на бедрок потолка; клетка выше спасает якорь, поставленный на ковре
    private static BlockPos spawnTarget(ServerLevel level, ServerPlayer player, Zone spawn) {
        if (spawn.spawnsAtAnchor()) {
            BlockPos anchor = spawn.spawnAnchor();
            level.getChunkAt(anchor);
            if (fitsPlayer(level, player, anchor)) return anchor;
            if (fitsPlayer(level, player, anchor.above())) return anchor.above();
        }
        return pickPlacement(level, player, spawn.area());
    }

    private static BlockPos pickPlacement(ServerLevel level, ServerPlayer player, ZoneArea area) {
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            BlockPos column = randomInside(area);
            if (!area.containsHorizontally(column.getX() + 0.5, column.getZ() + 0.5)) continue;

            BlockPos standing = standingSpotIn(level, player, area, column);
            if (standing != null) return standing;
        }
        BlockPos central = standingSpotIn(level, player, area, area.center());
        return central != null ? central : area.center();
    }

    private static BlockPos randomInside(ZoneArea area) {
        double offsetX = (RANDOM.nextDouble() * 2.0 - 1.0) * area.size();
        double offsetZ = (RANDOM.nextDouble() * 2.0 - 1.0) * area.size();
        return BlockPos.containing(area.centerX() + offsetX, area.center().getY(), area.centerZ() + offsetZ);
    }

    // WHY: скан сверху зоны вниз, но не выше логической высоты мира: в Незере зона, уходящая
    // WHY: выше потолка, иначе первой свободной клеткой отдавала крышу над бедроком
    private static BlockPos standingSpotIn(ServerLevel level, ServerPlayer player, ZoneArea area, BlockPos column) {
        level.getChunkAt(column);
        int ceiling = level.getMinBuildHeight() + level.getLogicalHeight() - HEADROOM_BLOCKS;
        int top = Math.min((int) Math.floor(area.maxY()), ceiling);
        int bottom = Math.max((int) Math.ceil(area.minY()), level.getMinBuildHeight() + 1);

        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos(column.getX(), top, column.getZ());
        for (int y = top; y >= bottom; y--) {
            feet.setY(y);
            if (standable(level, feet) && fitsPlayer(level, player, feet)) return feet.immutable();
        }
        return null;
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return false;
        if (!level.getFluidState(feet).isEmpty()) return false;
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty();
    }

    private static boolean fitsPlayer(ServerLevel level, ServerPlayer player, BlockPos feet) {
        AABB body = player.getDimensions(Pose.STANDING)
                .makeBoundingBox(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        return level.noCollision(player, body);
    }
}
