package com.persiki84.airdrop.cache;

import com.persiki84.airdrop.AirDropMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public final class CacheContainers {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

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

    // WHY: шалкер поршень ломает вместе с лутом, а раздатчик и выбрасыватель выкидывают лут по
    // WHY: сигналу редстоуна: тайником бывают только сундук, сундук-ловушка и бочка
    public static boolean accepts(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return (block instanceof ChestBlock || block instanceof BarrelBlock) && resolve(level, pos) != null;
    }

    public static LootCache covering(Level level, BlockPos pos) {
        LootCache direct = LootCaches.at(level, pos);
        if (direct != null) return direct;

        BlockPos twin = chestTwin(level, pos);
        return twin == null ? null : LootCaches.at(level, twin);
    }

    // WHY: воронки и вагонетки с воронкой спрашивают это каждый тик, поэтому до блока дело доходит
    // WHY: только рядом с тайником: в измерении без тайников это один поиск без единой аллокации
    public static boolean drainBlocked(Level level, int x, int y, int z) {
        if (!LootCaches.anyIn(level)) return false;
        if (LootCaches.holds(level, BlockPos.asLong(x, y, z))) return AirDropMod.enabled();
        if (!neighbourHeld(level, x, y, z)) return false;

        BlockPos twin = chestTwin(level, new BlockPos(x, y, z));
        return twin != null && LootCaches.holds(level, twin.asLong()) && AirDropMod.enabled();
    }

    private static boolean neighbourHeld(Level level, int x, int y, int z) {
        for (Direction side : SIDES) {
            if (LootCaches.holds(level, BlockPos.asLong(x + side.getStepX(), y, z + side.getStepZ()))) return true;
        }
        return false;
    }

    private static BlockPos chestTwin(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
        return pos.relative(ChestBlock.getConnectedDirection(state));
    }
}
