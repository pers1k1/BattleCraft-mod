package com.persiki84.airdrop.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.persiki84.airdrop.AirDropMod;
import com.persiki84.airdrop.entity.AirDropEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public final class LampHaloLayer extends GeoRenderLayer<AirDropEntity> {
    private static final String LAMP_BONE = "lamp_glow";
    private static final RenderType HALO = RenderType.eyes(new ResourceLocation(AirDropMod.MOD_ID, "textures/effect/lamp_halo.png"));
    private static final float MODEL_UNITS = 16.0F;
    private static final float BASE_RADIUS = 0.5F;
    private static final float RADIUS_PER_BLOCK = 0.018F;
    private static final float MAX_RADIUS = 2.4F;
    private static final float TOWARD_CAMERA = 0.35F;
    private static final float VISIBLE_SCALE = 0.01F;

    private final Vector3f lampInView = new Vector3f();
    private float lampStrength;
    private boolean lampSeen;

    public LampHaloLayer(GeoRenderer<AirDropEntity> renderer) {
        super(renderer);
    }

    // WHY: позиция снимается по ходу обхода костей, а рисуется ореол после модели: буфер другого
    // WHY: типа рендера посреди обхода закрыл бы пачку, в которую GeckoLib ещё пишет кубы
    @Override
    public void renderForBone(PoseStack poseStack, AirDropEntity drop, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!LAMP_BONE.equals(bone.getName()) || drop.isLooted() || bone.getScaleX() < VISIBLE_SCALE) return;

        poseStack.last().pose().transformPosition(bone.getPivotX() / MODEL_UNITS, bone.getPivotY() / MODEL_UNITS,
                bone.getPivotZ() / MODEL_UNITS, lampInView);
        lampStrength = Mth.clamp(bone.getScaleX(), 0.0F, 1.0F);
        lampSeen = true;
    }

    @Override
    public void render(PoseStack poseStack, AirDropEntity drop, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        if (!lampSeen) return;

        lampSeen = false;
        drawHalo(bufferSource.getBuffer(HALO));
    }

    // WHY: поза сущности уже несёт поворот камеры, поэтому точка после неё лежит в пространстве
    // WHY: вида, и квад, выровненный по осям этого пространства, смотрит прямо в камеру
    private void drawHalo(VertexConsumer halo) {
        float distance = lampInView.length();
        float radius = Math.min(MAX_RADIUS, BASE_RADIUS + distance * RADIUS_PER_BLOCK);
        float x = lampInView.x;
        float y = lampInView.y;
        float z = lampInView.z + TOWARD_CAMERA;
        float glow = lampStrength;
        corner(halo, x - radius, y - radius, z, glow, 0.0F, 1.0F);
        corner(halo, x + radius, y - radius, z, glow, 1.0F, 1.0F);
        corner(halo, x + radius, y + radius, z, glow, 1.0F, 0.0F);
        corner(halo, x - radius, y + radius, z, glow, 0.0F, 0.0F);
    }

    private static void corner(VertexConsumer halo, float x, float y, float z, float glow, float u, float v) {
        halo.vertex(x, y, z, glow, glow, glow, 1.0F, u, v, OverlayTexture.NO_OVERLAY,
                LightTexture.FULL_BRIGHT, 0.0F, 0.0F, 1.0F);
    }
}
