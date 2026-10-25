package com.persiki84.battlecraft.mixin.superb;

import com.persiki84.battlecraft.mode.MatchKills;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// WHY: своего события уничтожения техники у SuperbWarfare нет, а уход сущности не отличает взрыв
// WHY: от уборки ключом; destroy зовётся ровно на переходе здоровья в ноль, сверено javap по 0.8.9
@Mixin(targets = "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity", remap = false)
public abstract class VehicleDestroyMixin {

    @Shadow
    public abstract Entity getLastAttacker();

    @Shadow
    public abstract String getLastDriverUUID();

    @Shadow
    public abstract boolean hasWeapon();

    @Inject(method = "destroy", at = @At("HEAD"))
    private void battlecraft$scoreWreck(CallbackInfo info) {
        MatchKills.vehicleDestroyed((Entity) (Object) this, hasWeapon(), getLastAttacker(), getLastDriverUUID());
    }
}
