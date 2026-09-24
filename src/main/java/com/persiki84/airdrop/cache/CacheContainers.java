package com.persiki84.airdrop.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public final class CacheContainers {

    private CacheContainers() {}

    // WHY: половинка двойного сундука держит только свои 27 слотов: без склейки лут ложился бы
    // WHY: в одну половину, а вторая половина того же сундука оставалась пустой
    public static Container resolve(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, level, pos, true);
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof Container container ? container : null;
    }

    public static boolean isContainer(Level level, BlockPos pos) {
        return resolve(level, pos) != null;
    }

    public static LootCache covering(Level level, BlockPos pos) {
        LootCache direct = LootCaches.at(level, pos);
        if (direct != null) return direct;

        BlockPos twin = chestTwin(level, pos);
        return twin == null ? null : LootCaches.at(level, twin);
    }

    private static BlockPos chestTwin(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
        return pos.relative(ChestBlock.getConnectedDirection(state));
    }
}
