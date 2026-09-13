package com.persiki84.battlecraft.compat.iff;

import com.persiki84.battlecraft.compat.curios.Curios;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public final class IffDevice {
    public static final String MOD_ID = "superbwarfare";

    private static final ResourceLocation ITEM_ID = new ResourceLocation(MOD_ID, "iff");
    private static final String SLOT = "iff";
    private static final String ISSUED_TAG = "BattleCraftIssued";

    private static boolean probed;
    private static Item item;

    private IffDevice() {}

    // WHY: устройство принадлежит SuperbWarfare и живёт в слоте Curios: нет любого из двух модов -
    // WHY: выдавать нечего, и правило обязано молчать, а не сыпать ошибками каждый тик
    public static boolean available() {
        if (!probed) {
            probed = true;
            if (ModList.get().isLoaded(MOD_ID) && Curios.available()) {
                item = ForgeRegistries.ITEMS.getValue(ITEM_ID);
            }
        }
        return item != null;
    }

    public static boolean isIssued(ItemStack stack) {
        if (stack.isEmpty() || !available() || !stack.is(item)) return false;

        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(ISSUED_TAG);
    }

    public static boolean equipped(Player player) {
        return available() && Curios.isEquipped(player, item);
    }

    public static boolean issue(Player player) {
        if (!available()) return false;

        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putBoolean(ISSUED_TAG, true);
        return Curios.equip(player, SLOT, stack);
    }

    public static void revoke(Player player) {
        if (!available()) return;

        Curios.take(player, IffDevice::isIssued);
        player.getInventory().clearOrCountMatchingItems(IffDevice::isIssued, -1, player.inventoryMenu.getCraftSlots());
    }
}
