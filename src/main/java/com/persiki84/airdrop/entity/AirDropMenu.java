package com.persiki84.airdrop.entity;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// WHY: ящик аирдропа только отдаёт: положить в него нельзя ни курсором, ни шифт-кликом, ни
// WHY: цифрой хотбара (SWAP кладёт предмет в слот мимо проверки курсора), иначе ящик становится
// WHY: чужим тайником и не исчезает как пустой
public final class AirDropMenu extends ChestMenu {
    private static final int ROWS = 3;
    private static final int DROP_SLOTS = ROWS * 9;
    private static final double REACH_SQR = 8.0 * 8.0;

    private final AirDropEntity drop;

    public AirDropMenu(int id, Inventory inventory, Container container, AirDropEntity drop) {
        super(MenuType.GENERIC_9x3, id, inventory, container, ROWS);
        this.drop = drop;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (index >= DROP_SLOTS || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack moving = slot.getItem();
        ItemStack original = moving.copy();
        if (!this.moveItemStackTo(moving, DROP_SLOTS, this.slots.size(), true)) return ItemStack.EMPTY;

        if (moving.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        boolean intoDrop = slotId >= 0 && slotId < DROP_SLOTS;
        if (intoDrop && (swapsIn(clickType, button, player) || !getCarried().isEmpty())) return;
        super.clicked(slotId, button, clickType, player);
    }

    private static boolean swapsIn(ClickType clickType, int button, Player player) {
        return clickType == ClickType.SWAP && !player.getInventory().getItem(button).isEmpty();
    }

    // WHY: SimpleContainer считает себя доступным всегда: без этой проверки открытое окно жило
    // WHY: после исчезновения ящика и с любого расстояния
    @Override
    public boolean stillValid(Player player) {
        return drop.isAlive() && player.distanceToSqr(drop) <= REACH_SQR;
    }
}
