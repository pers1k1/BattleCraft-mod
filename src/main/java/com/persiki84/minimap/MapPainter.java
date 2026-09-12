package com.persiki84.minimap;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

public final class MapPainter {
    public static final int COLORS = 256;

    private static final int SNOW_COLOR = 0xDCE5EB;
    private static final int SIDE = 16;
    private static final int STEP_LIMIT = 4;
    private static final double STEP_SHADE = 0.08;
    private static final double CHECKER_SHADE = 0.96;
    private static final double SNOW_MIX = 0.5;

    private MapPainter() {}

    // WHY: пустая покраска значит, что красить было нечего: чанк ещё без местности или это пустота.
    // WHY: записанная в карту, она остаётся чёрным квадратом навсегда, известный чанк не красят заново
    public static boolean blank(int[] colors) {
        for (int color : colors) {
            if (color != 0) return false;
        }
        return true;
    }

    public static int[] paint(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        int[] colors = new int[COLORS];
        int startX = pos.getMinBlockX();
        int startZ = pos.getMinBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = 0; x < SIDE; x++) {
            for (int z = 0; z < SIDE; z++) {
                colors[x + z * SIDE] = column(chunk, cursor, startX + x, startZ + z, x, z);
            }
        }
        return colors;
    }

    private static int column(LevelChunk chunk, BlockPos.MutableBlockPos cursor,
                              int worldX, int worldZ, int x, int z) {
        int y = surfaceY(chunk, cursor, worldX, worldZ, topOf(chunk, x, z));
        cursor.set(worldX, y, worldZ);
        MapColor mapColor = chunk.getBlockState(cursor).getMapColor(chunk.getLevel(), cursor);

        int northY = z > 0 ? surfaceY(chunk, cursor, worldX, worldZ - 1, topOf(chunk, x, z - 1)) : y;
        int color = mapColor == MapColor.SNOW ? snowColor(chunk, cursor, worldX, y, worldZ) : mapColor.col;
        return shaded(color, y - northY, worldX, worldZ, mapColor == MapColor.WATER);
    }

    private static int topOf(LevelChunk chunk, int x, int z) {
        return Math.min(chunk.getMaxBuildHeight() - 1, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
    }

    private static int surfaceY(LevelChunk chunk, BlockPos.MutableBlockPos cursor,
                                int worldX, int worldZ, int from) {
        int y = from;
        while (true) {
            cursor.set(worldX, y, worldZ);
            if (chunk.getBlockState(cursor).getMapColor(chunk.getLevel(), cursor) != MapColor.NONE) return y;
            if (y - 1 <= chunk.getMinBuildHeight()) return y;

            y--;
        }
    }

    private static int snowColor(LevelChunk chunk, BlockPos.MutableBlockPos cursor, int worldX, int y, int worldZ) {
        if (y - 1 <= chunk.getMinBuildHeight()) return SNOW_COLOR;

        cursor.set(worldX, y - 1, worldZ);
        BlockState under = chunk.getBlockState(cursor);
        if (!under.is(BlockTags.LEAVES)) return SNOW_COLOR;

        int leaf = under.getMapColor(chunk.getLevel(), cursor).col;
        int red = (int) (((leaf >> 16) & 0xFF) * SNOW_MIX + ((SNOW_COLOR >> 16) & 0xFF) * SNOW_MIX);
        int green = (int) (((leaf >> 8) & 0xFF) * SNOW_MIX + ((SNOW_COLOR >> 8) & 0xFF) * SNOW_MIX);
        int blue = (int) ((leaf & 0xFF) * SNOW_MIX + (SNOW_COLOR & 0xFF) * SNOW_MIX);
        return (red << 16) | (green << 8) | blue;
    }

    private static int shaded(int color, int heightDiff, int worldX, int worldZ, boolean water) {
        double shade = 1.0;
        if (heightDiff > 0) {
            shade = 1.0 + Math.min(STEP_LIMIT, heightDiff) * STEP_SHADE;
        } else if (heightDiff < 0) {
            shade = 1.0 + Math.max(-STEP_LIMIT, heightDiff) * STEP_SHADE;
        } else if ((worldX + worldZ) % 2 == 0) {
            shade = CHECKER_SHADE;
        }
        if (water) shade = 1.0;

        int red = Math.min(255, (int) (((color >> 16) & 0xFF) * shade));
        int green = Math.min(255, (int) (((color >> 8) & 0xFF) * shade));
        int blue = Math.min(255, (int) ((color & 0xFF) * shade));
        return (red << 16) | (green << 8) | blue;
    }
}
