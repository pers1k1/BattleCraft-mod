package com.persiki84.minimap.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

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

        private long lastDrawn = Long.MIN_VALUE;

        public MapRegion(int rx, int rz) {
            this.rx = rx;
            this.rz = rz;
        }

        private void load() {
            lastDrawn = com.persiki84.shared.client.ui.UiFrame.frame();
            if (texture != null) return;

            image = new NativeImage(NativeImage.Format.RGBA, 512, 512, false);
            image.fillRect(0, 0, 512, 512, 0);
            texture = new DynamicTexture(image);
            textureLocation = Minecraft.getInstance().getTextureManager()
                    .register("minimap_region_" + rx + "_" + rz, texture);
            texture.bind();
            com.mojang.blaze3d.platform.GlStateManager._texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            com.mojang.blaze3d.platform.GlStateManager._texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            loadedRegions++;
            restoreRegion(this);
            texture.upload();
            dirty = false;
        }

        public void close() {
            if (texture == null) return;

            Minecraft.getInstance().getTextureManager().release(textureLocation);
            texture.close();
            textureLocation = null;
            texture = null;
            image = null;
            dirty = false;
            loadedRegions--;
        }
    }

    private static final Map<Long, MapRegion> regions = new ConcurrentHashMap<>();

    private static int loadedRegions;

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

    private static final int MAX_SCAN_RADIUS = 32;
    private static final int MIN_SCAN_RADIUS = 2;
    private static final int LOOKUP_BUDGET = 768;
    private static final int FRESH_BUDGET = 16;
    private static final int RESCAN_BUDGET = 4;
    private static final long SWEEP_NANOS = 2_000_000L;
    private static final int REGION_BUDGET = 24;

    private static final java.util.List<ChunkPos> window = new java.util.ArrayList<>();

    private static int windowRadius = -1;
    private static int cursor;

    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        ChunkPos center = new ChunkPos(new BlockPos((int) mc.player.getX(), 0, (int) mc.player.getZ()));
        if (sweep(mc, center) > 0) uploadDirtyRegions();
        trimRegions(center);
    }

    // WHY: окно покраски равно дальности прорисовки, а не восьми чанкам вокруг игрока: всё, что
    // WHY: клиент держит загруженным, обязано попасть на карту, иначе за спиной остаются пятна
    // WHY: непрокрашенной земли, и чем дальше прорисовка, тем их больше
    private static int sweep(Minecraft mc, ChunkPos center) {
        java.util.List<ChunkPos> offsets = window(mc);
        long deadline = System.nanoTime() + SWEEP_NANOS;
        int painted = 0;
        int fresh = 0;
        int rescans = 0;

        for (int step = 0; step < LOOKUP_BUDGET; step++) {
            ChunkPos offset = offsets.get(cursor);
            cursor = (cursor + 1) % offsets.size();

            ChunkPos pos = new ChunkPos(center.x + offset.x, center.z + offset.z);
            boolean known = ClientMapData.chunkData.containsKey(pos);
            if (known && rescans >= RESCAN_BUDGET) continue;
            if (!mc.level.hasChunk(pos.x, pos.z)) continue;

            if (known) rescans++; else fresh++;
            if (updateChunk(mc.level.getChunk(pos.x, pos.z), pos)) painted++;
            if (fresh >= FRESH_BUDGET || System.nanoTime() > deadline) break;
        }
        return painted;
    }

    private static java.util.List<ChunkPos> window(Minecraft mc) {
        int radius = Math.max(MIN_SCAN_RADIUS,
                Math.min(MAX_SCAN_RADIUS, mc.options.getEffectiveRenderDistance()));
        if (radius == windowRadius) return window;

        windowRadius = radius;
        cursor = 0;
        window.clear();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                window.add(new ChunkPos(dx, dz));
            }
        }
        window.sort(java.util.Comparator.comparingLong(pos -> (long) pos.x * pos.x + (long) pos.z * pos.z));
        return window;
    }

    private static void trimRegions(ChunkPos center) {
        if (loadedRegions <= REGION_BUDGET) return;

        int centerRegionX = center.x >> 5;
        int centerRegionZ = center.z >> 5;
        long oldestVisible = com.persiki84.shared.client.ui.UiFrame.frame() - 1;
        MapRegion farthest = null;
        long worst = -1L;
        for (MapRegion region : regions.values()) {
            if (region.texture == null || region.lastDrawn >= oldestVisible) continue;

            long dx = (long) region.rx - centerRegionX;
            long dz = (long) region.rz - centerRegionZ;
            long distance = dx * dx + dz * dz;
            if (distance <= worst) continue;

            worst = distance;
            farthest = region;
        }
        if (farthest != null) farthest.close();
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

    // WHY: заливка региона в видеопамять стоит мегабайта на вызов, поэтому пачка чанков
    // WHY: рисуется целиком и грузится один раз: на полной карте это тысячи заливок подряд
    public static void markChunksUpdated(java.util.Collection<ChunkPos> positions) {
        boolean painted = false;
        for (ChunkPos cp : positions) {
            int[] colors = ClientMapData.chunkData.get(cp);
            if (colors == null) continue;

            drawChunkToImage(cp, colors);
            painted = true;
        }
        if (painted) uploadDirtyRegions();
    }

    private static void drawChunkToImage(ChunkPos cp, int[] colors) {
        int rx = cp.x >> 5;
        int rz = cp.z >> 5;
        long regionId = ChunkPos.asLong(rx, rz);
        MapRegion region = regions.computeIfAbsent(regionId, k -> new MapRegion(rx, rz));

        if (region.image == null) return;
        paintChunk(region, cp.x, cp.z, colors);
    }

    private static void restoreRegion(MapRegion region) {
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                int chunkX = region.rx * 32 + x;
                int chunkZ = region.rz * 32 + z;
                int[] colors = ClientMapData.chunkData.get(new ChunkPos(chunkX, chunkZ));
                if (colors != null) paintChunk(region, chunkX, chunkZ, colors);
            }
        }
    }

    private static void paintChunk(MapRegion region, int chunkX, int chunkZ, int[] colors) {
        int localChunkX = chunkX & 31;
        int localChunkZ = chunkZ & 31;
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
                } else {
                    region.image.setPixelRGBA(pixelStartX + x, pixelStartZ + z, 0);
                }
            }
        }
        region.dirty = true;
    }

    // WHY: пустая покраска это не знание о местности, а чанк, который нечем было красить: записав
    // WHY: её, карта запоминала чёрный квадрат навсегда, потому что известный чанк больше не берут
    private static boolean updateChunk(LevelChunk chunk, ChunkPos cp) {
        int[] newColors = com.persiki84.minimap.MapPainter.paint(chunk);
        if (com.persiki84.minimap.MapPainter.blank(newColors)) return false;

        int[] oldColors = ClientMapData.chunkData.get(cp);
        if (oldColors != null && java.util.Arrays.equals(oldColors, newColors)) {
            return false;
        }

        ClientMapData.chunkData.put(cp, newColors);
        ClientMapStorage.touch();
        drawChunkToImage(cp, newColors);
        return true;
    }

    public static void renderMap(GuiGraphics guiGraphics, double mapX, double mapZ, float zoom,
                                 float centerX, float centerY, float clipX0, float clipY0, float clipX1, float clipY1) {
        float size = 512.0f * zoom;

        for (MapRegion region : regions.values()) {
            float x = (float) (centerX + (region.rx * 512.0 - mapX) * zoom);
            float y = (float) (centerY + (region.rz * 512.0 - mapZ) * zoom);

            if (x + size < clipX0 || x > clipX1 || y + size < clipY0 || y > clipY1) {
                continue;
            }

            region.load();
            drawRegion(guiGraphics, region, x, y, size);
        }
    }

    private static void drawRegion(GuiGraphics guiGraphics, MapRegion region, float x, float y, float size) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, region.textureLocation);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = guiGraphics.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, x, y + size, 0.0F).uv(0.0F, 1.0F).endVertex();
        builder.vertex(matrix, x + size, y + size, 0.0F).uv(1.0F, 1.0F).endVertex();
        builder.vertex(matrix, x + size, y, 0.0F).uv(1.0F, 0.0F).endVertex();
        builder.vertex(matrix, x, y, 0.0F).uv(0.0F, 0.0F).endVertex();
        tesselator.end();

        com.persiki84.shared.client.ui.UiRender.standardBlend();
    }
}
