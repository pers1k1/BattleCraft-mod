package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.client.combat.ParkourHands;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void battlecraft$hideDuringParkour(PoseStack pose, MultiBufferSource buffer, int light,
                                               LivingEntity entity, float limbSwing, float limbSwingAmount,
                                               float partialTick, float age, float headYaw, float headPitch,
                                               CallbackInfo callback) {
        if (entity instanceof Player player && ParkourHands.busy(player)) callback.cancel();
    }
}
