package com.persiki84.sellmod.client;

import com.persiki84.sellmod.network.SellSyncPacket;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

public final class ClientSellData {
    private static final Map<Item, Integer> prices = new HashMap<>();
    private static Item currency = Items.EMERALD;
    private static int currencyStackSize = Items.EMERALD.getMaxStackSize();
    private static boolean known;

    private ClientSellData() {}

    public static void sync(SellSyncPacket packet) {
        prices.clear();
        for (Map.Entry<String, Integer> entry : packet.prices.entrySet()) {
            Item item = resolve(entry.getKey());
            if (item != null) prices.put(item, entry.getValue());
        }
        Item resolved = resolve(packet.currencyItem);
        currency = resolved == null ? Items.EMERALD : resolved;
        currencyStackSize = Math.max(1, currency.getMaxStackSize());
        known = true;
    }

    public static void clear() {
        prices.clear();
        currency = Items.EMERALD;
        currencyStackSize = Math.max(1, currency.getMaxStackSize());
        known = false;
    }

    public static boolean known() {
        return known;
    }

    public static Item currency() {
        return currency;
    }

    public static int balance(LocalPlayer player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(currency)) total += stack.getCount();
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(currency)) total += stack.getCount();
        }
        return total;
    }

    public static int pending(LocalPlayer player) {
        if (prices.isEmpty()) return 0;
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            Integer price = prices.get(stack.getItem());
            if (price != null) total += price * stack.getCount();
        }
        for (ItemStack stack : player.getInventory().offhand) {
            Integer price = prices.get(stack.getItem());
            if (price != null) total += price * stack.getCount();
        }
        return total;
    }

    public static String format(int amount) {
        int perStack = currencyStackSize;
        int stacks = amount / perStack;
        int rest = amount % perStack;
        if (stacks <= 0) return String.valueOf(rest);
        if (rest == 0) return stacks + "×" + perStack;
        return stacks + "×" + perStack + "+" + rest;
    }

    private static Item resolve(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !ForgeRegistries.ITEMS.containsKey(location)) return null;
        return ForgeRegistries.ITEMS.getValue(location);
    }
}
