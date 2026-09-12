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

    private MapPainter() {}

    public static int[] paint(LevelChunk chunk) {
        ChunkPos cp = chunk.getPos();
        int[] colors = new int[COLORS];
        int startX = cp.getMinBlockX();
        int startZ = cp.getMinBlockZ();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;

                int y = Math.min(chunk.getMaxBuildHeight() - 1, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
                BlockState state;
                MapColor mapColor;

                do {
                    mpos.set(worldX, y, worldZ);
                    state = chunk.getBlockState(mpos);
                    mapColor = state.getMapColor(chunk.getLevel(), mpos);
                    y--;
                } while (mapColor == MapColor.NONE && y > chunk.getMinBuildHeight());
                y++;

                int yNorth = y;
                if (z > 0) {
                    int ny = Math.min(chunk.getMaxBuildHeight() - 1, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1));
                    do {
                        mpos.set(worldX, ny, worldZ - 1);
                        BlockState nstate = chunk.getBlockState(mpos);
                        if (nstate.getMapColor(chunk.getLevel(), mpos) != MapColor.NONE) break;
                        ny--;
                    } while (ny > chunk.getMinBuildHeight());
                    yNorth = ny;
                }

                int color = mapColor.col;

                if (mapColor == MapColor.SNOW) {
                    color = SNOW_COLOR;
                    if (y - 1 > chunk.getMinBuildHeight()) {
                        mpos.set(worldX, y - 1, worldZ);
                        BlockState underState = chunk.getBlockState(mpos);
                        if (underState.is(BlockTags.LEAVES)) {
                            int leaf = underState.getMapColor(chunk.getLevel(), mpos).col;
                            int r = (int) (((leaf >> 16) & 0xFF) * 0.5 + 220 * 0.5);
                            int g = (int) (((leaf >> 8) & 0xFF) * 0.5 + 229 * 0.5);
                            int b = (int) ((leaf & 0xFF) * 0.5 + 235 * 0.5);
                            color = (r << 16) | (g << 8) | b;
                        }
                    }
                }

                double shade = 1.0;
                int heightDiff = y - yNorth;
                if (heightDiff > 0) {
                    shade = 1.0 + Math.min(4, heightDiff) * 0.08;
                } else if (heightDiff < 0) {
                    shade = 1.0 + Math.max(-4, heightDiff) * 0.08;
                } else if ((worldX + worldZ) % 2 == 0) {
                    shade = 0.96;
                }
                if (mapColor == MapColor.WATER) {
                    shade = 1.0;
                }

                int r = Math.min(255, (int) (((color >> 16) & 0xFF) * shade));
                int g = Math.min(255, (int) (((color >> 8) & 0xFF) * shade));
                int b = Math.min(255, (int) ((color & 0xFF) * shade));

                colors[x + z * 16] = (r << 16) | (g << 8) | b;
            }
        }

        return colors;
    }
}
