package com.persiki84.battlecraft.client.menu.container;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import net.minecraft.world.entity.player.Inventory;

public final class GlassContainers {

    private GlassContainers() {}

    public static Screen restyled(Screen screen) {
        Inventory inventory = inventory();
        if (inventory == null) return null;

        Screen storage = storage(screen, inventory);
        return storage != null ? storage : machine(screen, inventory);
    }

    private static Inventory inventory() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? null : minecraft.player.getInventory();
    }

    private static Screen storage(Screen screen, Inventory inventory) {
        if (screen instanceof ShulkerBoxScreen shulker && !(screen instanceof GlassShulkerBoxScreen)) {
            return new GlassShulkerBoxScreen(shulker.getMenu(), inventory, shulker.getTitle());
        }
        if (screen instanceof ContainerScreen chest && !(screen instanceof GlassChestScreen)) {
            return new GlassChestScreen(chest.getMenu(), inventory, chest.getTitle());
        }
        if (screen instanceof DispenserScreen dispenser && !(screen instanceof GlassDispenserScreen)) {
            return new GlassDispenserScreen(dispenser.getMenu(), inventory, dispenser.getTitle());
        }
        if (screen instanceof HopperScreen hopper && !(screen instanceof GlassHopperScreen)) {
            return new GlassHopperScreen(hopper.getMenu(), inventory, hopper.getTitle());
        }
        return null;
    }

    private static Screen machine(Screen screen, Inventory inventory) {
        if (screen instanceof CraftingScreen crafting && !(screen instanceof GlassCraftingScreen)) {
            return new GlassCraftingScreen(crafting.getMenu(), inventory, crafting.getTitle());
        }
        if (screen instanceof BlastFurnaceScreen blast && !(screen instanceof GlassBlastFurnaceScreen)) {
            return new GlassBlastFurnaceScreen(blast.getMenu(), inventory, blast.getTitle());
        }
        if (screen instanceof SmokerScreen smoker && !(screen instanceof GlassSmokerScreen)) {
            return new GlassSmokerScreen(smoker.getMenu(), inventory, smoker.getTitle());
        }
        if (screen instanceof FurnaceScreen furnace && !(screen instanceof GlassFurnaceScreen)) {
            return new GlassFurnaceScreen(furnace.getMenu(), inventory, furnace.getTitle());
        }
        return null;
    }
}
