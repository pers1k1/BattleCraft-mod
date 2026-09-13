package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.compat.curios.Curios;
import net.minecraft.server.commands.ClearInventoryCommands;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;

// WHY: ванильный /clear ходит только по инвентарю и корзине верстака, поэтому надетое в слотах
// WHY: Curios переживало очистку: слоты чужого мода досчитываются тем же пределом и тем же счётом
@Mixin(ClearInventoryCommands.class)
public class ClearInventoryCommandsMixin {
    @Redirect(method = "clearInventory",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;clearOrCountMatchingItems"
                            + "(Ljava/util/function/Predicate;ILnet/minecraft/world/Container;)I"))
    private static int battlecraft$alsoCurios(Inventory inventory, Predicate<ItemStack> wanted,
                                              int limit, Container crafting) {
        int cleared = inventory.clearOrCountMatchingItems(wanted, limit, crafting);
        if (!Curios.available()) return cleared;

        // WHY: ноль у ванили значит «только посчитать», отрицательное - «без предела», поэтому
        // WHY: исчерпанный положительный предел обязан останавливать обход, а не считать заново
        if (limit > 0 && cleared >= limit) return cleared;
        int left = limit <= 0 ? limit : limit - cleared;
        return cleared + Curios.clearOrCount(inventory.player, wanted, left);
    }
}
