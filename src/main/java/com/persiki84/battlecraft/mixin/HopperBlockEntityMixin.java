package com.persiki84.battlecraft.mixin;

import com.persiki84.airdrop.cache.CacheContainers;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// WHY: запрет ставить воронку под тайник не держал вагонетку с воронкой, которая проезжает под
// WHY: ним и выкачивает каждое пополнение. Источник считается так же, как у ванили, и до хука
// WHY: Forge: у сундука есть capability предметов, и ванильная ветка до него не доходит
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {

    @Inject(method = "suckInItems", at = @At("HEAD"), cancellable = true)
    private static void battlecraft$spareLootCaches(Level level, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        int x = Mth.floor(hopper.getLevelX());
        int y = Mth.floor(hopper.getLevelY() + 1.0D);
        int z = Mth.floor(hopper.getLevelZ());
        if (CacheContainers.drainBlocked(level, x, y, z)) cir.setReturnValue(false);
    }
}
