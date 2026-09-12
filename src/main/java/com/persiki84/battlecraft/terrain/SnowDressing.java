package com.persiki84.battlecraft.terrain;

import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID)
public final class SnowDressing {
    private static final int MAX_RADIUS = 12;
    private static final int MAX_JOBS = 8;
    private static final int COLUMNS_PER_TICK = 256;
    private static final int SETTLE_TICKS = 40;
    private static final BlockState SNOW = Blocks.SNOW.defaultBlockState();

    private static final Deque<Job> jobs = new ArrayDeque<>();

    private SnowDressing() {}

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        List<BlockPos> broken = event.getAffectedBlocks();
        if (broken.isEmpty() || jobs.size() >= MAX_JOBS) return;

        BlockPos center = broken.get(broken.size() / 2);
        if (!snowy(level, center)) return;

        jobs.add(new Job(level, center, reach(broken, center)));
    }

    private static int reach(List<BlockPos> broken, BlockPos center) {
        int span = 0;
        for (BlockPos pos : broken) {
            span = Math.max(span, Math.max(Math.abs(pos.getX() - center.getX()), Math.abs(pos.getZ() - center.getZ())));
        }
        return Math.min(MAX_RADIUS, span + 1);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide) return;

        Job job = jobs.peek();
        if (job == null || job.level != event.level) return;
        if (job.waited++ < SETTLE_TICKS) return;

        if (job.advance()) jobs.poll();
    }

    private static boolean snowy(Level level, BlockPos pos) {
        return level.getBiome(pos).value().coldEnoughToSnow(pos);
    }

    private static void dress(ServerLevel level, int x, int z) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        if (!level.getBlockState(top).isAir() || !snowy(level, top)) return;
        if (!SNOW.canSurvive(level, top)) return;

        level.setBlock(top, SNOW, Block.UPDATE_ALL);
    }

    private static final class Job {
        private final ServerLevel level;
        private final BlockPos center;
        private final int radius;
        private int cursor;
        private int waited;

        private Job(ServerLevel level, BlockPos center, int radius) {
            this.level = level;
            this.center = center;
            this.radius = radius;
        }

        private boolean advance() {
            int side = radius * 2 + 1;
            int columns = side * side;
            int stop = Math.min(columns, cursor + COLUMNS_PER_TICK);

            for (; cursor < stop; cursor++) {
                int x = center.getX() - radius + cursor % side;
                int z = center.getZ() - radius + cursor / side;
                if (level.isLoaded(new BlockPos(x, center.getY(), z))) dress(level, x, z);
            }
            return cursor >= columns;
        }
    }
}
