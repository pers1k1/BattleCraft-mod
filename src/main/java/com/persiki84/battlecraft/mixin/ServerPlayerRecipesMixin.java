package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.craft.CraftRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

// WHY: награда достижения выдаёт рецепты по ключу один раз: отказ во время запрета оставил бы
// WHY: игрока без рецепта, а засчитанное достижение повторно его уже не даст
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerRecipesMixin {

    @Redirect(method = "awardRecipesByKey", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/RecipeManager;byKey(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;"))
    private Optional<? extends Recipe<?>> battlecraft$awardUnfiltered(RecipeManager manager, ResourceLocation key) {
        return CraftRules.keyUnfiltered(manager, key);
    }
}
