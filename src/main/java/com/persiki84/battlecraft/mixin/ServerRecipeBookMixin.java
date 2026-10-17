package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.craft.CraftRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

// WHY: загрузка книги выбрасывает рецепт, которого не нашёл byKey, и при сохранении игрока он
// WHY: пропадает насовсем: запрет по фазе не имеет права стирать открытые рецепты
@Mixin(ServerRecipeBook.class)
public abstract class ServerRecipeBookMixin {

    @Redirect(method = "loadRecipes", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/RecipeManager;byKey(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;"))
    private Optional<? extends Recipe<?>> battlecraft$loadUnfiltered(RecipeManager manager, ResourceLocation key) {
        return CraftRules.keyUnfiltered(manager, key);
    }
}
