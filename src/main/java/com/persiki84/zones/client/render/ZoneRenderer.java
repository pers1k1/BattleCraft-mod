package com.persiki84.zones.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.persiki84.capturepoints.client.ClientCaptureData;
import com.persiki84.shared.zone.ZoneArea;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneSource;
import com.persiki84.zones.ZonesMod;
import com.persiki84.zones.client.ZoneOccupancy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

@Mod.EventBusSubscriber(modid = ZonesMod.MOD_ID, value = Dist.CLIENT)
public final class ZoneRenderer {
    private static final float WALL_ALPHA = 0.20f;
    private static final float RING_ALPHA = 0.95f;
    private static final float FRESNEL_POWER = 1.6f;
    private static final double WALL_DISTANCE = 64.0;
    private static final float PULSE_SECONDS = 2.5f;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final long ANIMATION_CYCLE_MILLIS = 3600000L;

    private static long lastFrameTime = System.currentTimeMillis();
    private static float animationTime;

    private ZoneRenderer() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            VisibleZones.reset();
            ZoneColors.reset();
            ZoneOccupancy.clear();
            return;
        }
        VisibleZones.refresh();
        ZoneOccupancy.refresh(minecraft.player);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Vec3 camera = event.getCamera().getPosition();
        Matrix4f modelView = event.getPoseStack().last().pose();
        Matrix4f projection = event.getProjectionMatrix();
        ZoneMarkers.project(camera, modelView, projection);

        List<Zone> zones = VisibleZones.current();
        if (zones.isEmpty() || !ZoneShaders.ready()) return;

        drawVolumes(zones, event.getFrustum(), camera, modelView, projection);
    }

    private static void drawVolumes(List<Zone> zones, Frustum frustum, Vec3 camera,
                                    Matrix4f modelView, Matrix4f projection) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastFrameTime;
        lastFrameTime = now;

        Minecraft minecraft = Minecraft.getInstance();
        float pulsePhase = (now % (long) (PULSE_SECONDS * 1000.0f)) / (PULSE_SECONDS * 1000.0f) * TAU;
        animationTime = (float) ((now % ANIMATION_CYCLE_MILLIS) / 1000.0);

        beginState();
        for (Zone zone : zones) {
            if (!frustum.isVisible(zone.area().bounds())) continue;
            drawZone(zone, minecraft, camera, modelView, projection, pulsePhase, elapsed);
        }
        endState();
    }

    private static void drawZone(Zone zone, Minecraft minecraft, Vec3 camera, Matrix4f modelView,
                                 Matrix4f projection, float pulsePhase, long elapsed) {
        ZoneArea area = zone.area();
        float progress = captureProgress(zone);
        float[] color = ZoneColors.resolve(zone, progress, elapsed);

        double originX = area.centerX() - camera.x;
        double originY = area.center().getY() - camera.y;
        double originZ = area.centerZ() - camera.z;

        double distanceSquared = originX * originX + originZ * originZ;
        if (distanceSquared < WALL_DISTANCE * WALL_DISTANCE) {
            drawWall(zone, area, minecraft, originX, originY, originZ, color, progress, pulsePhase,
                    modelView, projection);
        }
        drawRing(zone, area, minecraft, originX, originY, originZ, color, progress, pulsePhase, modelView, projection);
    }

    private static void drawWall(Zone zone, ZoneArea area, Minecraft minecraft,
                                 double originX, double originY, double originZ,
                                 float[] color, float progress, float pulsePhase,
                                 Matrix4f modelView, Matrix4f projection) {
        ShaderInstance shader = ZoneShaders.volume();
        applyUniforms(shader, originX, originY, originZ, 1.0f, 1.0f, 1.0f,
                color, WALL_ALPHA, progress, pulsePhase, 0.0f);

        RenderSystem.disableCull();
        VertexBuffer wall = ZoneMeshes.wall(minecraft.level, zone.id(), area);
        wall.bind();
        wall.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();
        RenderSystem.enableCull();
    }

    private static void drawRing(Zone zone, ZoneArea area, Minecraft minecraft,
                                 double originX, double originY, double originZ,
                                 float[] color, float progress, float pulsePhase,
                                 Matrix4f modelView, Matrix4f projection) {
        ShaderInstance shader = ZoneShaders.ring();
        applyUniforms(shader, originX, originY, originZ, 1.0f, 1.0f, 1.0f,
                color, RING_ALPHA, progress, pulsePhase, 1.0f);

        RenderSystem.polygonOffset(-1.0f, -6.0f);
        RenderSystem.enablePolygonOffset();

        VertexBuffer ring = ZoneMeshes.ring(minecraft.level, zone.id(), area);
        ring.bind();
        ring.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();

        RenderSystem.disablePolygonOffset();
        RenderSystem.polygonOffset(0.0f, 0.0f);
    }

    private static void applyUniforms(ShaderInstance shader, double originX, double originY, double originZ,
                                      float scaleX, float scaleY, float scaleZ,
                                      float[] color, float alpha, float progress, float pulsePhase, float surfaceMode) {
        shader.safeGetUniform("ZoneOrigin").set((float) originX, (float) originY, (float) originZ);
        shader.safeGetUniform("ZoneScale").set(scaleX, scaleY, scaleZ);
        shader.safeGetUniform("ZoneColor").set(color[0], color[1], color[2], alpha);
        shader.safeGetUniform("FresnelPower").set(FRESNEL_POWER);
        shader.safeGetUniform("PulsePhase").set(pulsePhase);
        shader.safeGetUniform("CaptureProgress").set(progress);
        shader.safeGetUniform("SurfaceMode").set(surfaceMode);
        shader.safeGetUniform("AnimationTime").set(animationTime);
    }

    private static float captureProgress(Zone zone) {
        if (zone.source() != ZoneSource.CAPTURE_POINT) return 0.0f;
        return ClientCaptureData.getProgress(zone.id());
    }

    private static void beginState() {
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
    }

    private static void endState() {
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }
}
