package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.craft.CraftRules;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// WHY: стойка спрашивает выход зелья одним методом и для проверки «можно ли варить», и для самой
// WHY: варки. Пустой выход для запрещённого зелья значит, что стойка не начинает варку и не жжёт
// WHY: порошок впустую, а соседние бутылки с разрешённым зельем варятся как обычно
@Mixin(value = BrewingRecipeRegistry.class, remap = false)
public abstract class BrewingRecipeRegistryMixin {

    @Inject(method = "getOutput", at = @At("RETURN"), cancellable = true)
    private static void battlecraft$skipBanned(ItemStack input, ItemStack ingredient,
                                               CallbackInfoReturnable<ItemStack> cir) {
        if (CraftRules.blocksBrewed(cir.getReturnValue())) cir.setReturnValue(ItemStack.EMPTY);
    }
}
