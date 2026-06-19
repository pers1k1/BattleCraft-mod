package com.persiki84.battlecraft.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SculkShriekerBlockEntity.class)
public abstract class SculkShriekerMixin {

    @Shadow
    private int warningLevel;

    @Inject(method = "tryRespond", at = @At("HEAD"))
    private void battlecraft$instantWarden(ServerLevel level, CallbackInfo ci) {
        if (this.warningLevel > 0) {
            this.warningLevel = 4;
        }
    }
}
