package com.persiki84.shared.economy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

// WHY: в режиме «Предмет» деньги это стопки в инвентаре: основная сетка и вторая рука, как их считал
// WHY: магазин с самого начала. Счёт, списание и выдача обязаны видеть одни и те же ячейки, иначе
// WHY: насчитанное нельзя было бы списать
public final class CurrencyItems {

    private CurrencyItems() {}

    public static int count(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        return countIn(inventory.items, item) + countIn(inventory.offhand, item);
    }

    private static int countIn(List<ItemStack> slots, Item item) {
        int total = 0;
        for (ItemStack stack : slots) {
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    // WHY: ваниль кладёт новую стопку в пустую ячейку основной сетки и доливает в начатые без тега, в
    // WHY: том числе во вторую руку; ровно эта ёмкость и гарантирует, что выдача не уронит ничего
    public static int room(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        int perStack = perStack(inventory, item);
        int room = 0;
        for (ItemStack stack : inventory.items) {
            room += stack.isEmpty() ? perStack : spaceIn(stack, item, perStack);
        }
        return room + spaceIn(inventory.offhand.get(0), item, perStack);
    }

    private static int spaceIn(ItemStack stack, Item item, int perStack) {
        if (stack.isEmpty() || !stack.is(item) || stack.hasTag() || !stack.isStackable()) return 0;
        return Math.max(0, perStack - stack.getCount());
    }

    // WHY: насколько изменится room, если эту стопку убрать из ячейки: пустая ячейка основной сетки
    // WHY: вмещает целую стопку, а пустая вторая рука новую стопку не принимает вовсе
    public static int freedBy(ItemStack stack, Item item, int perStack, boolean mainGrid) {
        int before = spaceIn(stack, item, perStack);
        return mainGrid ? perStack - before : -before;
    }

    public static int perStack(Inventory inventory, Item item) {
        return Math.max(1, Math.min(new ItemStack(item).getMaxStackSize(), inventory.getMaxStackSize()));
    }

    public static void take(ServerPlayer player, Item item, int amount) {
        Inventory inventory = player.getInventory();
        int remaining = takeFrom(inventory.items, item, amount);
        takeFrom(inventory.offhand, item, remaining);
    }

    private static int takeFrom(List<ItemStack> slots, Item item, int amount) {
        int remaining = amount;
        for (ItemStack stack : slots) {
            if (remaining <= 0) break;
            if (!stack.is(item)) continue;

            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
        return remaining;
    }

    // WHY: выдача идёт стопками предельного размера: пакет слота пишет количество байтом, и одна
    // WHY: стопка на сотни монет у клиента показалась бы неверно
    public static int give(ServerPlayer player, Item item, int amount) {
        Inventory inventory = player.getInventory();
        int perStack = perStack(inventory, item);
        int left = amount;
        while (left > 0) {
            ItemStack chunk = new ItemStack(item, Math.min(left, perStack));
            int offered = chunk.getCount();
            inventory.add(chunk);
            left -= offered - chunk.getCount();
            if (!chunk.isEmpty()) break;
        }
        return left;
    }

    public static void drop(ServerPlayer player, Item item, int amount) {
        int perStack = perStack(player.getInventory(), item);
        for (int left = amount; left > 0; left -= perStack) {
            player.drop(new ItemStack(item, Math.min(left, perStack)), false);
        }
    }
}
