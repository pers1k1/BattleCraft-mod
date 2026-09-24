package com.persiki84.airdrop.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.persiki84.airdrop.AirDropMod;
import com.persiki84.battlecraft.client.ClientModules;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.shared.client.ui.UiAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

@Mod.EventBusSubscriber(modid = AirDropMod.MOD_ID, value = Dist.CLIENT)
public final class CacheRenderer {
    private static final float RANGE = 40.0f;
    private static final float FADE_TAIL = 8.0f;
    private static final int DRAW_LIMIT = 48;
    private static final float MODE_FRAME = 0.0f;
    private static final float MODE_HALO = 1.0f;
    private static final float MODE_RING = 2.0f;
    private static final float MODE_BLOOM = 3.0f;
    private static final float FRAME_MARGIN = 0.05f;
    private static final float HALO_SPREAD = 2.1f;
    private static final float HALO_LIFT = 0.02f;
    private static final float RING_LIFT = 0.16f;
    private static final float RING_SPAN = 0.95f;
    private static final float BLOOM_REACH = 1.1f;
    private static final float SHIFT_SECONDS = 0.8f;
    private static final float BLOOM_SECONDS = 1.4f;
    private static final float FRAME_ALPHA = 0.75f;
    private static final float HALO_ALPHA = 0.6f;
    private static final float RING_ALPHA = 0.85f;
    private static final float BLOOM_ALPHA = 0.8f;
    private static final long LOOP_MILLIS = 240000L;
    private static final double TAU = Math.PI * 2.0;
    private static final int RING_TURNS = -6;

    private static float animationTime;
    private static double loop;
    private static String worldKey = "";

    private CacheRenderer() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getInstance();
        String here = minecraft.level == null ? "" : minecraft.level.dimension().location().toString();
        if (here.equals(worldKey)) return;

        worldKey = here;
        ClientCacheField.forget();
        CacheMarkers.reset();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !ClientModules.allows(ModuleId.AIRDROP)) {
            CacheMarkers.reset();
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        Matrix4f modelView = event.getPoseStack().last().pose();
        Matrix4f projection = event.getProjectionMatrix();
        CacheMarkers.project(camera, modelView, projection);

        List<ClientCacheField.Cell> cells = ClientCacheField.cells();
        if (cells.isEmpty() || !CacheShaders.ready()) return;

        long looped = System.currentTimeMillis() % LOOP_MILLIS;
        animationTime = looped / 1000.0f;
        loop = looped / (double) LOOP_MILLIS;
        drawField(minecraft.level, cells, camera, event.getFrustum(), modelView, projection);
    }

    private static void drawField(BlockGetter level, List<ClientCacheField.Cell> cells, Vec3 camera,
                                  Frustum frustum, Matrix4f modelView, Matrix4f projection) {
        int drawn = 0;
        begin();
        try {
            for (int index = 0; index < cells.size() && drawn < DRAW_LIMIT; index++) {
                if (drawCell(level, cells.get(index), camera, frustum, modelView, projection)) drawn++;
            }
        } finally {
            end();
        }
    }

    private static boolean drawCell(BlockGetter level, ClientCacheField.Cell cell, Vec3 camera, Frustum frustum,
                                    Matrix4f modelView, Matrix4f projection) {
        if (cell.missing()) return false;

        double distance = Math.sqrt(cell.pos().distToCenterSqr(camera));
        if (distance > RANGE || !frustum.isVisible(cell.bounds())) return false;

        AABB box = fitted(level, cell);
        float fade = fade(distance);
        float life = life(cell);
        float bloom = settling(cell.sinceFilled(), BLOOM_SECONDS);
        int color = CacheTint.color(cell.tier());

        drawFrame(box, camera, color, fade, life, bloom, cell, modelView, projection);
        if (life > 0.01f || bloom > 0.0f) drawHalo(box, camera, color, fade, life, bloom, cell, modelView, projection);
        if (cell.waiting()) drawRing(box, camera, color, fade, cell, modelView, projection);
        if (bloom > 0.0f) drawBloom(box, camera, color, fade, bloom, cell, modelView, projection);
        return true;
    }

    // WHY: состояние тайника переходит плавно в обе стороны: мгновенное угасание рамки при
    // WHY: опустошении и мгновенное зажигание при пополнении читались как мигание
    private static float life(ClientCacheField.Cell cell) {
        if (cell.empty()) return 1.0f - UiAnim.easeOut(Math.min(1.0f, cell.sinceEmptied() / SHIFT_SECONDS));
        return UiAnim.easeOut(Math.min(1.0f, cell.sinceFilled() / SHIFT_SECONDS));
    }

    private static void drawFrame(AABB box, Vec3 camera, int color, float fade, float life, float bloom,
                                  ClientCacheField.Cell cell, Matrix4f modelView, Matrix4f projection) {
        placeAt(midX(box), midY(box), midZ(box), camera, (float) box.getXsize() + FRAME_MARGIN,
                (float) box.getYsize() + FRAME_MARGIN, (float) box.getZsize() + FRAME_MARGIN);
        uniforms(color, FRAME_ALPHA, MODE_FRAME, life, bloom, fade, 0.0f, cell.drift());
        RenderSystem.polygonOffset(-1.0f, -6.0f);
        RenderSystem.enablePolygonOffset();
        try {
            draw(CacheMeshes.cage(), modelView, projection);
        } finally {
            RenderSystem.disablePolygonOffset();
            RenderSystem.polygonOffset(0.0f, 0.0f);
        }
    }

    private static void drawHalo(AABB box, Vec3 camera, int color, float fade, float life, float bloom,
                                 ClientCacheField.Cell cell, Matrix4f modelView, Matrix4f projection) {
        float spread = (float) Math.max(box.getXsize(), box.getZsize()) * HALO_SPREAD;
        placeAt(midX(box), box.minY + HALO_LIFT, midZ(box), camera, spread, 1.0f, spread);
        uniforms(color, HALO_ALPHA, MODE_HALO, life, bloom, fade, 0.0f, cell.drift());
        draw(CacheMeshes.floor(), modelView, projection);
    }

    private static void drawRing(AABB box, Vec3 camera, int color, float fade, ClientCacheField.Cell cell,
                                 Matrix4f modelView, Matrix4f projection) {
        float span = (float) Math.min(box.getXsize(), box.getZsize()) * RING_SPAN;
        placeAt(midX(box), box.maxY + RING_LIFT, midZ(box), camera, span, 1.0f, span);
        uniforms(color, RING_ALPHA, MODE_RING, cell.refillProgress(), 0.0f, fade, turn(cell, RING_TURNS), cell.drift());
        draw(CacheMeshes.ring(), modelView, projection);
    }

    private static void drawBloom(AABB box, Vec3 camera, int color, float fade, float bloom,
                                  ClientCacheField.Cell cell, Matrix4f modelView, Matrix4f projection) {
        float size = (float) Math.max(box.getXsize(), box.getZsize()) * (1.0f + BLOOM_REACH * (1.0f - bloom));
        placeAt(midX(box), midY(box), midZ(box), camera, size, size, size);
        uniforms(color, BLOOM_ALPHA, MODE_BLOOM, 1.0f, bloom, fade, 0.0f, cell.drift());
        draw(CacheMeshes.shell(), modelView, projection);
    }

    // WHY: рамка облегает настоящую форму блока, а не куб: сундук ниже и уже блока, а двойной
    // WHY: сундук это две половины, и рамка обязана обнять обе, иначе она режет сундук пополам
    private static AABB fitted(BlockGetter level, ClientCacheField.Cell cell) {
        BlockState state = level.getBlockState(cell.pos());
        if (cell.fittedTo(state)) return cell.frame();

        AABB box = shapeOf(level, cell.pos(), state);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            Direction toTwin = ChestBlock.getConnectedDirection(state);
            BlockPos twin = cell.pos().relative(toTwin);
            box = box.minmax(shapeOf(level, twin, level.getBlockState(twin)));
        }
        cell.fit(state, box);
        return box;
    }

    private static AABB shapeOf(BlockGetter level, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getShape(level, pos);
        return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
    }

    private static float turn(ClientCacheField.Cell cell, int turnsPerLoop) {
        double turns = loop * turnsPerLoop + cell.drift();
        return (float) ((turns - Math.floor(turns)) * TAU);
    }

    private static float settling(float since, float seconds) {
        return since < seconds ? 1.0f - UiAnim.easeOut(since / seconds) : 0.0f;
    }

    private static float fade(double distance) {
        if (distance <= RANGE - FADE_TAIL) return 1.0f;
        return UiAnim.clamp01((float) ((RANGE - distance) / FADE_TAIL));
    }

    private static double midX(AABB box) {
        return (box.minX + box.maxX) * 0.5;
    }

    private static double midY(AABB box) {
        return (box.minY + box.maxY) * 0.5;
    }

    private static double midZ(AABB box) {
        return (box.minZ + box.maxZ) * 0.5;
    }

    private static void placeAt(double x, double y, double z, Vec3 camera, float scaleX, float scaleY, float scaleZ) {
        CacheShaders.place((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z), scaleX, scaleY, scaleZ);
    }

    private static void uniforms(int color, float alpha, float mode, float phase, float pulse, float fade,
                                 float spin, float drift) {
        CacheShaders.paint(color, alpha);
        CacheShaders.shape(mode, phase, pulse);
        CacheShaders.motion(fade, spin, drift, animationTime);
    }

    private static void draw(VertexBuffer mesh, Matrix4f modelView, Matrix4f projection) {
        mesh.bind();
        mesh.drawWithShader(modelView, projection, CacheShaders.shader());
        VertexBuffer.unbind();
    }

    private static void begin() {
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
    }

    private static void end() {
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }
}
