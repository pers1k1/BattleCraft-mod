package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.client.menu.MenuPose;
import com.persiki84.battlecraft.client.menu.MenuPresenceClient;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void battlecraft$holdMenu(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                      float age, float headYaw, float headPitch, CallbackInfo callback) {
        if (!(entity instanceof Player player) || !MenuPose.posed(player)) return;

        float hold = MenuPresenceClient.hold(player.getUUID());
        if (!MenuPose.holding(hold)) return;

        MenuPose.apply((PlayerModel<?>) (Object) this, hold, age);
    }
}
