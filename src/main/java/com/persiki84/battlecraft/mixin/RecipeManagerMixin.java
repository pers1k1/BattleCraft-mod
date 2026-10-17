package com.persiki84.battlecraft.mixin;

import com.mojang.datafixers.util.Pair;
import com.persiki84.battlecraft.craft.CraftRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// WHY: запрет крафта стоит в самом поиске рецептов: через него идут верстак и сетка инвентаря, печи,
// WHY: костёр, камнерез, кузнечный стол и модовые станки, поэтому результат не появляется ни в одном
// WHY: окне, печь не плавит, а кэш последнего рецепта у печи спрашивает отсюда же каждый тик.
// WHY: Запрещённый рецепт не отбрасывает поиск целиком: если тот же набор складывается в разрешённый
// WHY: рецепт, отдаётся он. На вызов здесь ложится один CallbackInfoReturnable, сам вызов строит
// WHY: поток, Optional и пару
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Shadow
    private <C extends Container, T extends Recipe<C>> Map<ResourceLocation, T> byType(RecipeType<T> type) {
        throw new AssertionError();
    }

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;"
            + "Lnet/minecraft/world/level/Level;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private <C extends Container, T extends Recipe<C>> void battlecraft$skipBanned(
            RecipeType<T> type, C container, Level level, CallbackInfoReturnable<Optional<T>> cir) {
        Optional<T> found = cir.getReturnValue();
        if (CraftRules.idle(level) || found.isEmpty() || !CraftRules.blocks(found.get(), container, level)) return;

        for (T recipe : byType(type).values()) {
            if (battlecraft$allowed(recipe, container, level)) {
                cir.setReturnValue(Optional.of(recipe));
                return;
            }
        }
        cir.setReturnValue(Optional.empty());
    }

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true)
    private <C extends Container, T extends Recipe<C>> void battlecraft$skipBannedCached(
            RecipeType<T> type, C container, Level level, ResourceLocation last,
            CallbackInfoReturnable<Optional<Pair<ResourceLocation, T>>> cir) {
        Optional<Pair<ResourceLocation, T>> found = cir.getReturnValue();
        if (CraftRules.idle(level) || found.isEmpty()
                || !CraftRules.blocks(found.get().getSecond(), container, level)) return;

        for (Map.Entry<ResourceLocation, T> entry : byType(type).entrySet()) {
            if (battlecraft$allowed(entry.getValue(), container, level)) {
                cir.setReturnValue(Optional.of(Pair.of(entry.getKey(), entry.getValue())));
                return;
            }
        }
        cir.setReturnValue(Optional.empty());
    }

    // WHY: взятие результата (ResultSlot.onTake) считает остаток сетки этим поиском и не перепроверяет
    // WHY: рецепт. Результат, посчитанный до запрета, при отфильтрованном пустом ответе получил бы
    // WHY: в остаток сами стопки сетки вместо копий, и снятие ингредиента удвоило бы их. Остаток
    // WHY: поэтому всегда считает ванильный рецепт
    @Redirect(method = "getRemainingItemsFor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;"
                    + "Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    private <C extends Container, T extends Recipe<C>> Optional<T> battlecraft$remainderUnfiltered(
            RecipeManager self, RecipeType<T> type, C container, Level level) {
        CraftRules.pause();
        try {
            return self.getRecipeFor(type, container, level);
        } finally {
            CraftRules.resume();
        }
    }

    @Inject(method = "getRecipesFor", at = @At("RETURN"), cancellable = true)
    private <C extends Container, T extends Recipe<C>> void battlecraft$dropBanned(
            RecipeType<T> type, C container, Level level, CallbackInfoReturnable<List<T>> cir) {
        if (CraftRules.idle(level)) return;

        List<T> found = cir.getReturnValue();
        List<T> allowed = new ArrayList<>(found.size());
        for (T recipe : found) {
            if (!CraftRules.blocks(recipe, container, level)) allowed.add(recipe);
        }
        if (allowed.size() != found.size()) cir.setReturnValue(allowed);
    }

    @Inject(method = "getAllRecipesFor", at = @At("RETURN"), cancellable = true)
    private <C extends Container, T extends Recipe<C>> void battlecraft$listAllowed(
            RecipeType<T> type, CallbackInfoReturnable<List<T>> cir) {
        RecipeManager self = (RecipeManager) (Object) this;
        List<T> found = cir.getReturnValue();
        List<T> allowed = null;
        for (int index = 0; index < found.size(); index++) {
            T recipe = found.get(index);
            boolean banned = CraftRules.blocksListed(self, recipe);
            if (banned && allowed == null) allowed = new ArrayList<>(found.subList(0, index));
            if (!banned && allowed != null) allowed.add(recipe);
        }
        if (allowed != null) cir.setReturnValue(List.copyOf(allowed));
    }

    @Inject(method = "byKey", at = @At("RETURN"), cancellable = true)
    private void battlecraft$hideBannedKey(ResourceLocation key, CallbackInfoReturnable<Optional<? extends Recipe<?>>> cir) {
        Optional<? extends Recipe<?>> found = cir.getReturnValue();
        if (found.isPresent() && CraftRules.blocksKeyed((RecipeManager) (Object) this, found.get())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    private static <C extends Container, T extends Recipe<C>> boolean battlecraft$allowed(T recipe, C container,
                                                                                          Level level) {
        return recipe.matches(container, level) && !CraftRules.blocks(recipe, container, level);
    }
}
