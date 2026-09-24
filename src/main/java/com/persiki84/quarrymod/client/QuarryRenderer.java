package com.persiki84.quarrymod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.persiki84.battlecraft.client.ClientModules;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.quarrymod.QuarryMod;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

@Mod.EventBusSubscriber(modid = QuarryMod.MODID, value = Dist.CLIENT)
public final class QuarryRenderer {
    private static final float VOID_RANGE = 44.0f;
    private static final float LACE_RANGE = 26.0f;
    private static final float FADE_TAIL = 8.0f;
    private static final float APPEAR_SECONDS = 0.7f;
    private static final int DRAW_LIMIT = 96;

    private static final float MODE_WALLS = 0.0f;
    private static final float MODE_CORE = 1.0f;
    private static final float MODE_RING = 2.0f;
    private static final float MODE_LACE = 3.0f;
    private static final float MODE_BLOOM = 4.0f;

    private static final float BREAK_SECONDS = 1.2f;
    private static final float ASSEMBLE_SECONDS = 1.1f;
    private static final float REFUSE_SECONDS = 0.7f;
    private static final float CORE_MIN = 0.10f;
    private static final float CORE_GROWTH = 0.16f;
    private static final float RING_BOB = 0.025f;
    private static final float WALL_SCALE = 0.985f;
    private static final float LACE_SCALE = 1.01f;
    private static final float LACE_SWELL = 0.12f;
    private static final float BLOOM_REACH = 0.9f;
    private static final float WALL_ALPHA = 0.55f;
    private static final float CORE_ALPHA = 0.8f;
    private static final float RING_ALPHA = 0.85f;
    private static final float LACE_ALPHA = 0.3f;
    private static final float BLOOM_ALPHA = 0.8f;
    private static final int REFUSE_COLOR = 0xFFFF5A55;
    private static final double TAU = Math.PI * 2.0;
    private static final long LOOP_MILLIS = 240000L;
    private static final int CORE_TURNS = 20;
    private static final int RING_TURNS = -4;
    private static final int BOB_TURNS = 48;

    private static float animationTime;
    private static double loop;
    private static String worldKey = "";

    private QuarryRenderer() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getInstance();
        String here = minecraft.level == null || minecraft.player == null
                ? ""
                : minecraft.level.dimension().location().toString();
        if (here.equals(worldKey)) return;

        worldKey = here;
        ClientQuarryField.forget();
        QuarryMarkers.reset();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!ClientModules.allows(ModuleId.QUARRY)) {
            QuarryMarkers.reset();
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        Matrix4f modelView = event.getPoseStack().last().pose();
        Matrix4f projection = event.getProjectionMatrix();
        QuarryMarkers.project(camera, modelView, projection);

        List<ClientQuarryField.Cell> cells = ClientQuarryField.cells();
        if (cells.isEmpty() || !QuarryShaders.ready()) return;

        long looped = System.currentTimeMillis() % LOOP_MILLIS;
        animationTime = looped / 1000.0f;
        loop = looped / (double) LOOP_MILLIS;
        drawField(cells, camera, event.getFrustum(), modelView, projection);
    }

    private static void drawField(List<ClientQuarryField.Cell> cells, Vec3 camera, Frustum frustum,
                                  Matrix4f modelView, Matrix4f projection) {
        int drawn = 0;

        begin();
        try {
            for (int index = 0; index < cells.size() && drawn < DRAW_LIMIT; index++) {
                ClientQuarryField.Cell cell = cells.get(index);
                if (drawCell(cell, camera, frustum, modelView, projection)) drawn++;
            }
        } finally {
            end();
        }
    }

    private static boolean drawCell(ClientQuarryField.Cell cell, Vec3 camera, Frustum frustum,
                                    Matrix4f modelView, Matrix4f projection) {
        double originX = cell.pos().getX() + 0.5 - camera.x;
        double originY = cell.pos().getY() + 0.5 - camera.y;
        double originZ = cell.pos().getZ() + 0.5 - camera.z;
        double distance = Math.sqrt(originX * originX + originY * originY + originZ * originZ);

        float range = cell.excavated() ? VOID_RANGE : LACE_RANGE;
        if (distance > range || !frustum.isVisible(cell.bounds())) return false;

        float fade = fade(distance, range);
        float shown = fade * appeared(cell);
        if (cell.excavated()) {
            drawExcavated(cell, originX, originY, originZ, shown, modelView, projection);
        } else {
            drawWhole(cell, originX, originY, originZ, shown, modelView, projection);
        }
        drawBlooms(cell, originX, originY, originZ, fade, modelView, projection);
        return true;
    }

    private static void drawExcavated(ClientQuarryField.Cell cell, double originX, double originY, double originZ,
                                      float fade, Matrix4f modelView, Matrix4f projection) {
        float progress = cell.progress();
        float refusal = refusal(cell);
        float glow = Math.max(refusal, settling(cell.sinceBroken(), BREAK_SECONDS));
        int tint = UiTheme.mix(cell.color(), REFUSE_COLOR, refusal);

        uniforms(originX, originY, originZ, WALL_SCALE, tint, WALL_ALPHA, MODE_WALLS, progress, glow, fade, 0.0f);
        draw(QuarryMeshes.cage(), modelView, projection);

        float coreSize = CORE_MIN + CORE_GROWTH * progress;
        uniforms(originX, originY, originZ, coreSize, tint, CORE_ALPHA, MODE_CORE, progress, refusal, fade,
                turn(cell, CORE_TURNS));
        draw(QuarryMeshes.core(), modelView, projection);

        float bob = (float) Math.sin(turn(cell, BOB_TURNS)) * RING_BOB;
        uniforms(originX, originY + bob, originZ, 1.0f, tint, RING_ALPHA, MODE_RING, progress, refusal, fade,
                turn(cell, RING_TURNS));
        draw(QuarryMeshes.ring(), modelView, projection);
    }

    private static void drawWhole(ClientQuarryField.Cell cell, double originX, double originY, double originZ,
                                  float fade, Matrix4f modelView, Matrix4f projection) {
        float assembling = settling(cell.sinceRestored(), ASSEMBLE_SECONDS);
        float scale = LACE_SCALE * (1.0f + LACE_SWELL * assembling);
        float refusal = refusal(cell);
        int tint = UiTheme.mix(cell.color(), REFUSE_COLOR, refusal);
        float glow = Math.max(refusal, assembling);

        uniforms(originX, originY, originZ, scale, tint, LACE_ALPHA, MODE_LACE, 1.0f, glow, fade, 0.0f);
        RenderSystem.polygonOffset(-1.0f, -6.0f);
        RenderSystem.enablePolygonOffset();
        try {
            draw(QuarryMeshes.cage(), modelView, projection);
        } finally {
            RenderSystem.disablePolygonOffset();
            RenderSystem.polygonOffset(0.0f, 0.0f);
        }
    }

    // WHY: волна добычи и сборки живёт поверх любого состояния клетки: блок успевает вернуться
    // WHY: раньше, чем она догорит, и обрывать её на половине заметно
    private static void drawBlooms(ClientQuarryField.Cell cell, double originX, double originY, double originZ,
                                   float fade, Matrix4f modelView, Matrix4f projection) {
        float broken = cell.sinceBroken();
        if (broken < BREAK_SECONDS) {
            bloom(cell, originX, originY, originZ, fade, broken / BREAK_SECONDS, modelView, projection);
        }

        float restored = cell.sinceRestored();
        if (restored < ASSEMBLE_SECONDS) {
            bloom(cell, originX, originY, originZ, fade, restored / ASSEMBLE_SECONDS, modelView, projection);
        }
    }

    private static void bloom(ClientQuarryField.Cell cell, double originX, double originY, double originZ,
                              float fade, float stage, Matrix4f modelView, Matrix4f projection) {
        float eased = UiAnim.easeOut(stage);
        float radius = 1.0f + BLOOM_REACH * eased;

        uniforms(originX, originY, originZ, radius, cell.color(), BLOOM_ALPHA, MODE_BLOOM, 1.0f,
                1.0f - eased, fade, 0.0f);
        draw(QuarryMeshes.shell(), modelView, projection);
    }

    private static float settling(float since, float seconds) {
        return since < seconds ? 1.0f - UiAnim.easeOut(since / seconds) : 0.0f;
    }

    private static float refusal(ClientQuarryField.Cell cell) {
        return settling(cell.sinceRefused(), REFUSE_SECONDS);
    }

    // WHY: угол считается от петли времени, а не копится в клетке: накопленный угол обнулялся при
    // WHY: пересоздании клетки снимком и прыгал на сворачивании, когда сцена брала его дробную долю
    private static float turn(ClientQuarryField.Cell cell, int turnsPerLoop) {
        double turns = loop * turnsPerLoop + cell.drift();
        return (float) ((turns - Math.floor(turns)) * TAU);
    }

    // WHY: клетка приходит снимком, когда рядом открылась выработка или игрок подошёл к полю, и без
    // WHY: проявления вставала кадром; вспышка ломания идёт мимо него, это отклик на удар
    private static float appeared(ClientQuarryField.Cell cell) {
        return UiAnim.easeOut(UiAnim.clamp01(cell.sinceBorn() / APPEAR_SECONDS));
    }

    private static float fade(double distance, float range) {
        if (distance <= range - FADE_TAIL) return 1.0f;
        return UiAnim.clamp01((float) ((range - distance) / FADE_TAIL));
    }

    private static void uniforms(double originX, double originY, double originZ, float scale, int color,
                                 float alpha, float mode, float phase, float pulse, float fade, float spin) {
        QuarryShaders.place((float) originX, (float) originY, (float) originZ, scale, scale, scale);
        QuarryShaders.paint(QuarryTint.red(color), QuarryTint.green(color), QuarryTint.blue(color), alpha);
        QuarryShaders.shape(mode, phase, pulse);
        QuarryShaders.motion(fade, spin, animationTime);
    }

    private static void draw(VertexBuffer mesh, Matrix4f modelView, Matrix4f projection) {
        mesh.bind();
        mesh.drawWithShader(modelView, projection, QuarryShaders.field());
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
