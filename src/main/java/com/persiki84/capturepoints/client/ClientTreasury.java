package com.persiki84.capturepoints.client;

import net.minecraft.world.item.ItemStack;

import java.util.List;

// WHY: снимок казны своей команды: сервер шлёт его при входе, на каждое изменение и по запросу
// WHY: магазина. Версия растёт с каждым снимком, по ней экран понимает, что пора перестроиться
public final class ClientTreasury {
    private static List<ItemStack> items = List.of();
    private static boolean enabled;
    private static int version;

    private ClientTreasury() {}

    public static void accept(boolean payoutToTreasury, List<ItemStack> stacks) {
        enabled = payoutToTreasury;
        items = List.copyOf(stacks);
        version++;
    }

    public static void forget() {
        accept(false, List.of());
    }

    public static List<ItemStack> items() {
        return items;
    }

    public static boolean shown() {
        return enabled || !items.isEmpty();
    }

    public static int version() {
        return version;
    }

    public static int total() {
        int total = 0;
        for (ItemStack stack : items) {
            total += stack.getCount();
        }
        return total;
    }
}
