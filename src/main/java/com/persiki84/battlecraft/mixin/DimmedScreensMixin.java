package com.persiki84.battlecraft.mixin;

import com.persiki84.shared.client.menu.DimmedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import org.spongepowered.asm.mixin.Mixin;

// WHY: раньше каждый такой экран подменялся наследником в ScreenEvent.Opening, и ванильный
// WHY: успевал построиться до замены: у творческого инвентаря это ещё и перестройка всех вкладок
// WHY: и лишняя подмена containerMenu у игрока. Метка на самом классе снимает вторую загрузку
@Mixin({
        PauseScreen.class,
        InventoryScreen.class,
        CreativeModeInventoryScreen.class,
        ContainerScreen.class,
        ShulkerBoxScreen.class,
        DispenserScreen.class,
        HopperScreen.class,
        CraftingScreen.class,
        AbstractFurnaceScreen.class
})
public abstract class DimmedScreensMixin implements DimmedScreen {
}
