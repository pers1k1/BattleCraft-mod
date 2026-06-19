package com.persiki84.airdrop.client.model;

import com.persiki84.airdrop.AirDropMod;
import com.persiki84.airdrop.entity.AirDropEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class AirDropModel<T extends AirDropEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(new ResourceLocation(AirDropMod.MOD_ID, "airdrop"), "main");

    private final ModelPart airdrop;
    private final ModelPart parachute;
    private final ModelPart root;

    public AirDropModel(ModelPart root) {
        this.root = root;
        this.airdrop = root.getChild("airdrop");
        this.parachute = this.airdrop.getChild("parachute");
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition airdrop = partdefinition.addOrReplaceChild("airdrop", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-8.0F, 45.0F, -16.0F, 16.0F, 2.0F, 32.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 36).addBox(6.0F, 34.0F, -16.0F, 2.0F, 11.0F, 32.0F, new CubeDeformation(0.0F))
                        .texOffs(72, 36).addBox(-8.0F, 33.0F, -16.0F, 2.0F, 12.0F, 32.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 84).addBox(-6.0F, 34.0F, 14.0F, 12.0F, 11.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(40, 84).addBox(-6.0F, 34.0F, -16.0F, 12.0F, 11.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(96, 0).addBox(-9.0F, 32.0F, -17.0F, 4.0F, 14.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(116, 0).addBox(5.0F, 32.0F, -17.0F, 4.0F, 14.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(119, 3).addBox(5.0F, 46.0F, -17.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(119, 3).addBox(-9.0F, 46.0F, -17.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(119, 3).addBox(-9.0F, 46.0F, 16.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(119, 3).addBox(5.0F, 46.0F, 16.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(136, 0).addBox(-9.0F, 32.0F, 13.0F, 4.0F, 14.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(156, 0).addBox(5.0F, 32.0F, 13.0F, 4.0F, 14.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(2, 132).addBox(8.0F, 36.0F, -13.0F, 1.0F, 2.0F, 26.0F, new CubeDeformation(0.0F))
                        .texOffs(2, 164).addBox(8.0F, 41.0F, -13.0F, 1.0F, 2.0F, 26.0F, new CubeDeformation(0.0F))
                        .texOffs(64, 132).addBox(-9.0F, 36.0F, -13.0F, 1.0F, 2.0F, 26.0F, new CubeDeformation(0.0F))
                        .texOffs(64, 164).addBox(-9.0F, 41.0F, -13.0F, 1.0F, 2.0F, 26.0F, new CubeDeformation(0.0F))
                        .texOffs(124, 130).addBox(-5.0F, 36.0F, 16.0F, 10.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(124, 140).addBox(-5.0F, 36.0F, -17.0F, 10.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(160, 84).addBox(-3.0F, 38.0F, -18.0F, 6.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(160, 96).addBox(-3.0F, 38.0F, 17.0F, 6.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(180, 36).addBox(8.0F, 34.0F, -9.5F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(180, 36).addBox(8.0F, 34.0F, 6.5F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -47.0F, 0.0F));

        airdrop.addOrReplaceChild("r1_post_fr", CubeListBuilder.create()
                        .texOffs(120, 3).addBox(0.0F, -1.0F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(8.0F, 47.0F, -16.0F, 0.0F, -1.5708F, 0.0F));

        airdrop.addOrReplaceChild("r2_post_fr", CubeListBuilder.create()
                        .texOffs(120, 3).addBox(0.0F, -1.0F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-9.0F, 47.0F, -16.0F, 0.0F, -1.5708F, 0.0F));

        airdrop.addOrReplaceChild("r3_post_fr", CubeListBuilder.create()
                        .texOffs(120, 3).addBox(0.0F, -1.0F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-9.0F, 47.0F, 13.0F, 0.0F, -1.5708F, 0.0F));

        airdrop.addOrReplaceChild("r4_post_fr", CubeListBuilder.create()
                        .texOffs(120, 3).addBox(0.0F, -1.0F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(8.0F, 47.0F, 13.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition parachute = airdrop.addOrReplaceChild("parachute", CubeListBuilder.create()
                        .texOffs(0, 300).addBox(-22.0F, 0.0F, -22.0F, 44.0F, 1.0F, 44.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 350).addBox(-17.0F, -1.0F, -17.0F, 34.0F, 1.0F, 34.0F, new CubeDeformation(0.0F))
                        .texOffs(200, 60).addBox(-3.0F, 32.0F, -3.0F, 6.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        parachute.addOrReplaceChild("r5_cord1", CubeListBuilder.create()
                        .texOffs(188, 60).addBox(0.0F, -35.0F, -1.0F, 1.0F, 35.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-9.0F, 33.0F, 17.0F, -0.1222F, 0.0F, -0.3665F));

        parachute.addOrReplaceChild("r6_cord4", CubeListBuilder.create()
                        .texOffs(188, 60).addBox(0.0F, -35.0F, -1.0F, 1.0F, 35.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-9.0F, 33.0F, -16.0F, 0.1222F, 0.0F, -0.3665F));

        parachute.addOrReplaceChild("r7_cord3", CubeListBuilder.create()
                        .texOffs(188, 60).addBox(0.0F, -35.0F, -1.0F, 1.0F, 35.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(8.0F, 33.0F, -16.0F, 0.1222F, 0.0F, 0.3665F));

        parachute.addOrReplaceChild("r8_cord2", CubeListBuilder.create()
                        .texOffs(188, 60).addBox(0.0F, -35.0F, -1.0F, 1.0F, 35.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(8.0F, 33.0F, 17.0F, -0.1222F, 0.0F, 0.3665F));

        PartDefinition latch_a = airdrop.addOrReplaceChild("latch_a", CubeListBuilder.create()
                        .texOffs(192, 36).addBox(0.0F, -4.0F, -1.5F, 1.5F, 4.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(192, 44).addBox(-1.5F, -3.5F, -1.5F, 1.5F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(8.0F, 34.0F, -8.0F));

        PartDefinition latch_b = airdrop.addOrReplaceChild("latch_b", CubeListBuilder.create()
                        .texOffs(192, 36).addBox(0.0F, -4.0F, -1.5F, 1.5F, 4.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(192, 44).addBox(-1.5F, -3.5F, -1.5F, 1.5F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offset(8.0F, 34.0F, 8.0F));

        PartDefinition lid = airdrop.addOrReplaceChild("lid", CubeListBuilder.create()
                        .texOffs(0, 196).addBox(0.0F, -3.0F, -16.0F, 16.0F, 3.0F, 32.0F, new CubeDeformation(0.0F))
                        .texOffs(100, 196).addBox(0.0F, 0.0F, 15.0F, 16.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(100, 200).addBox(0.0F, 0.0F, -16.0F, 16.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(140, 196).addBox(15.0F, 0.0F, -16.0F, 1.0F, 1.0F, 32.0F, new CubeDeformation(0.0F))
                        .texOffs(16, 248).addBox(6.0F, -4.0F, -8.0F, 3.0F, 1.0F, 16.0F, new CubeDeformation(0.0F))
                        .texOffs(27, 259).addBox(6.0F, -4.0F, -16.0F, 3.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(27, 259).addBox(6.0F, -4.0F, 11.0F, 3.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(70, 232).addBox(0.0F, -4.0F, -11.0F, 16.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(150, 232).addBox(0.0F, -4.0F, 8.0F, 16.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(211, 197).addBox(-1.0F, -2.0F, -13.0F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(213, 199).addBox(-1.0F, -2.0F, -14.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(211, 207).addBox(-1.0F, -2.0F, 10.0F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(213, 209).addBox(-1.0F, -2.0F, 13.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-8.0F, 33.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 256, 512);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root.getAllParts().forEach(ModelPart::resetPose);

        this.parachute.visible = !entity.isLanded();

        if (!entity.isLeveling()) {
            float speed = Mth.clamp(entity.getFallSpeed() / 0.15F, 0.75F, 2.5F);
            this.animate(entity.flyingAnimationState, AirDropAnimations.FLYING, ageInTicks, speed);
        }
        this.animate(entity.openingAnimationState, AirDropAnimations.OPENING, ageInTicks, 1.0F);
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack,
                               com.mojang.blaze3d.vertex.VertexConsumer vertexConsumer,
                               int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        airdrop.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
