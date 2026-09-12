package com.persiki84.battlecraft.client.hud;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class GunBridge {
    public static final int SPARE_HIDDEN = -1;
    public static final int SPARE_INFINITE = -2;

    private GunBridge() {}

    public static boolean available() {
        return TaczBridge.available() || SuperbBridge.available();
    }

    public static Gun read(ItemStack stack, Inventory inventory) {
        Gun gun = TaczBridge.read(stack, inventory);
        return gun != null ? gun : SuperbBridge.read(stack, inventory);
    }

    public record Gun(int loaded, int magazine, int spare, int spareMax, String fireMode) {}
}
