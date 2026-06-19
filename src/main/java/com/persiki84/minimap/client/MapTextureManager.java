package com.persiki84.minimap.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.material.MapColor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MapTextureManager {
    public static class MapRegion {
        public final int rx;
        public final int rz;
        public NativeImage image;
        public DynamicTexture texture;
        public ResourceLocation textureLocation;
        public boolean dirty = false;

        public MapRegion(int rx, int rz) {
            this.rx = rx;
            this.rz = rz;
            this.image = new NativeImage(NativeImage.Format.RGBA, 512, 512, false);
            this.image.fillRect(0, 0, 512, 512, 0x00000000);
            this.texture = new DynamicTexture(image);
            this.textureLocation = Minecraft.getInstance().getTextureManager().register("minimap_region_" + rx + "_" + rz, texture);
        }

        public void close() {
            if (this.texture != null) this.texture.close();
            if (this.image != null) this.image.close();
        }
    }

    private static final Map<Long, MapRegion> regions = new ConcurrentHashMap<>();

    public static void init() {
        clearAll();
        redrawFromStorage();
    }

    public static void clearAll() {
        for (MapRegion region : regions.values()) {
            region.close();
        }
        regions.clear();
    }

    private static int scanIndex = 0;

    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        ChunkPos centerChunk = new ChunkPos(new BlockPos((int) mc.player.getX(), 0, (int) mc.player.getZ()));
        int radius = 8;
        int diameter = radius * 2 + 1;
        int totalChunks = diameter * diameter;

        int chunksUpdatedThisTick = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos cp = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                if (!ClientMapData.chunkData.containsKey(cp) && mc.level.hasChunk(cp.x, cp.z)) {
                    LevelChunk chunk = mc.level.getChunk(cp.x, cp.z);
                    if (updateChunk(chunk, cp)) {
                        chunksUpdatedThisTick++;
                        if (chunksUpdatedThisTick > 2) {
                            uploadDirtyRegions();
                            return;
                        }
                    }
                }
            }
        }

        int chunksCheckedThisTick = 0;
        while (chunksCheckedThisTick < 20 && chunksUpdatedThisTick < 2) {
            scanIndex = (scanIndex + 1) % totalChunks;
            int dx = (scanIndex % diameter) - radius;
            int dz = (scanIndex / diameter) - radius;

            ChunkPos cp = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
            chunksCheckedThisTick++;

            if (ClientMapData.chunkData.containsKey(cp) && mc.level.hasChunk(cp.x, cp.z)) {
                LevelChunk chunk = mc.level.getChunk(cp.x, cp.z);
                if (updateChunk(chunk, cp)) {
                    chunksUpdatedThisTick++;
                }
            }
        }

        if (chunksUpdatedThisTick > 0) {
            uploadDirtyRegions();
        }
    }

    private static void uploadDirtyRegions() {
        for (MapRegion region : regions.values()) {
            if (region.dirty) {
                region.texture.upload();
                region.dirty = false;
            }
        }
    }

    private static void redrawFromStorage() {
        for (Map.Entry<ChunkPos, int[]> entry : ClientMapData.chunkData.entrySet()) {
            drawChunkToImage(entry.getKey(), entry.getValue());
        }
        uploadDirtyRegions();
    }

    public static void markChunkUpdated(ChunkPos cp) {
        int[] colors = ClientMapData.chunkData.get(cp);
        if (colors != null) {
            drawChunkToImage(cp, colors);
            uploadDirtyRegions();
        }
    }

    private static void drawChunkToImage(ChunkPos cp, int[] colors) {
        int rx = cp.x >> 5;
        int rz = cp.z >> 5;
        long regionId = ChunkPos.asLong(rx, rz);
        MapRegion region = regions.computeIfAbsent(regionId, k -> new MapRegion(rx, rz));

        int localChunkX = cp.x & 31;
        int localChunkZ = cp.z & 31;
        int pixelStartX = localChunkX * 16;
        int pixelStartZ = localChunkZ * 16;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int color = colors[x + z * 16];
                if (color != 0) {
                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int b = color & 0xFF;
                    int a = 255;
                    int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                    region.image.setPixelRGBA(pixelStartX + x, pixelStartZ + z, abgr);
                }
            }
        }
        region.dirty = true;
    }

    private static boolean updateChunk(LevelChunk chunk, ChunkPos cp) {
        int[] newColors = new int[256];
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
                        MapColor ncolor = nstate.getMapColor(chunk.getLevel(), mpos);
                        if (ncolor != MapColor.NONE) break;
                        ny--;
                    } while (ny > chunk.getMinBuildHeight());
                    yNorth = ny;
                }

                int color = mapColor.col;
                boolean isSnow = (mapColor == MapColor.SNOW);

                if (isSnow) {
                    if (y - 1 > chunk.getMinBuildHeight()) {
                        mpos.set(worldX, y - 1, worldZ);
                        BlockState underState = chunk.getBlockState(mpos);
                        if (underState.is(net.minecraft.tags.BlockTags.LEAVES)) {
                            MapColor underColor = underState.getMapColor(chunk.getLevel(), mpos);
                            int leafCol = underColor.col;
                            int r1 = (leafCol >> 16) & 0xFF;
                            int g1 = (leafCol >> 8) & 0xFF;
                            int b1 = leafCol & 0xFF;
                            int rBlend = (int) (r1 * 0.5 + 220 * 0.5);
                            int gBlend = (int) (g1 * 0.5 + 229 * 0.5);
                            int bBlend = (int) (b1 * 0.5 + 235 * 0.5);
                            color = (rBlend << 16) | (gBlend << 8) | bBlend;
                        } else {
                            color = 0xDCE5EB;
                        }
                    } else {
                        color = 0xDCE5EB;
                    }
                }

                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;

                int heightDiff = y - yNorth;
                double shade = 1.0;
                if (heightDiff > 0) {
                    shade = 1.0 + Math.min(4, heightDiff) * 0.08;
                } else if (heightDiff < 0) {
                    shade = 1.0 + Math.max(-4, heightDiff) * 0.08;
                } else {
                    if ((worldX + worldZ) % 2 == 0) shade = 0.96;
                }

                if (mapColor == MapColor.WATER) {
                    shade = 1.0;
                }

                r = Math.min(255, (int)(r * shade));
                g = Math.min(255, (int)(g * shade));
                b = Math.min(255, (int)(b * shade));

                int shadedColor = (r << 16) | (g << 8) | b;
                newColors[x + z * 16] = shadedColor;
            }
        }

        int[] oldColors = ClientMapData.chunkData.get(cp);
        if (oldColors != null && java.util.Arrays.equals(oldColors, newColors)) {
            return false;
        }

        ClientMapData.chunkData.put(cp, newColors);
        drawChunkToImage(cp, newColors);

        if (ClientMapData.serverHasMod) {
            String dim = Minecraft.getInstance().level.dimension().location().toString().replace(":", "_");
            java.util.List<com.persiki84.minimap.network.MapChunkSyncPacket.ChunkData> list = new java.util.ArrayList<>();
            list.add(new com.persiki84.minimap.network.MapChunkSyncPacket.ChunkData(cp.x, cp.z, newColors));
            com.persiki84.minimap.network.PacketHandler.INSTANCE.sendToServer(new com.persiki84.minimap.network.MapChunkSyncPacket(dim, list));
        }
        return true;
    }

    public static void renderMap(GuiGraphics guiGraphics, double mapX, double mapZ, float zoom, int screenCenterX, int screenCenterY, int screenW, int screenH) {
        double scale = zoom;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int maxW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int maxH = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        for (MapRegion region : regions.values()) {
            double regionWorldX = region.rx * 512.0;
            double regionWorldZ = region.rz * 512.0;

            double screenStartTexX = screenCenterX + (regionWorldX - mapX) * scale;
            double screenStartTexY = screenCenterY + (regionWorldZ - mapZ) * scale;
            double renderWidth = 512 * scale;
            double renderHeight = 512 * scale;

            if (screenStartTexX + renderWidth < 0 || screenStartTexX > maxW || screenStartTexY + renderHeight < 0 || screenStartTexY > maxH) {
                continue;
            }

            RenderSystem.setShaderTexture(0, region.textureLocation);
            guiGraphics.blit(region.textureLocation, (int)screenStartTexX, (int)screenStartTexY, 0, 0, (int)renderWidth, (int)renderHeight, (int)renderWidth, (int)renderHeight);
        }
    }
}
