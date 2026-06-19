package com.persiki84.airdrop.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.persiki84.airdrop.AirDropMod;
import com.persiki84.airdrop.client.model.AirDropModel;
import com.persiki84.airdrop.entity.AirDropEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class AirDropRenderer extends EntityRenderer<AirDropEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AirDropMod.MOD_ID, "textures/entity/airdrop.png");

    private final AirDropModel<AirDropEntity> model;

    public AirDropRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new AirDropModel<>(ctx.bakeLayer(AirDropModel.LAYER_LOCATION));
        this.shadowRadius = 0.6F;
    }

    @Override
    public void render(AirDropEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        poseStack.pushPose();

        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        float ageInTicks = entity.tickCount + partialTick;

        model.setupAnim(entity, 0, 0, ageInTicks, 0, 0);
        model.renderToBuffer(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(AirDropEntity entity) {
        return TEXTURE;
    }
}
