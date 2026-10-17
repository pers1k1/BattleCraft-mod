package com.persiki84.airdrop.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.persiki84.airdrop.AirDropMod;
import com.persiki84.airdrop.entity.AirDropEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class AirDropRenderer extends GeoEntityRenderer<AirDropEntity> {

    public AirDropRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(new ResourceLocation(AirDropMod.MOD_ID, "airdrop")));
        this.shadowRadius = 0.6F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        addRenderLayer(new LampHaloLayer(this));
    }

    // WHY: GeckoLib поворачивает по телу только живую сущность, у остальных угол всегда ноль. Ящик
    // WHY: падает со случайным поворотом, и дым шашки, посчитанный от него, висел в метре от модели
    @Override
    protected void applyRotations(AirDropEntity drop, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                  float partialTick) {
        super.applyRotations(drop, poseStack, ageInTicks, drop.getYRot(), partialTick);
    }
}
