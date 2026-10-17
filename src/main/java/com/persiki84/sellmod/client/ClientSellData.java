package com.persiki84.sellmod.client;

import com.persiki84.sellmod.network.SellSyncPacket;
import com.persiki84.shared.economy.CurrencyMode;
import com.persiki84.shared.economy.Money;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// WHY: зеркало валюты сервера. В режиме «Предмет» баланс считается по инвентарю на месте, как и
// WHY: раньше: ответ сервера отставал бы от подобранной стопки. В режиме «Счёт» баланс приходит
// WHY: числом, потому что в инвентаре его нет
public final class ClientSellData {
    private static final Map<Item, Integer> prices = new HashMap<>();
    private static Item currency = Items.EMERALD;
    private static ItemStack currencyStack = new ItemStack(Items.EMERALD);
    private static int currencyStackSize = Items.EMERALD.getMaxStackSize();
    private static CurrencyMode mode = CurrencyMode.DEFAULT;
    private static String customName = "";
    private static ItemStack icon = ItemStack.EMPTY;
    private static long accountBalance;
    private static boolean known;
    private static int version;

    private ClientSellData() {}

    public static void sync(SellSyncPacket packet) {
        prices.clear();
        for (Map.Entry<String, Integer> entry : packet.prices.entrySet()) {
            Item item = resolve(entry.getKey());
            if (item != null) prices.put(item, entry.getValue());
        }
        Item resolved = resolve(packet.currencyItem);
        currency = resolved == null ? Items.EMERALD : resolved;
        currencyStack = new ItemStack(currency);
        currencyStackSize = Math.max(1, currency.getMaxStackSize());
        mode = packet.mode;
        customName = packet.name;
        icon = packet.icon.isEmpty() ? ItemStack.EMPTY : packet.icon.copyWithCount(1);
        accountBalance = Money.clamp(packet.balance);
        known = true;
        version++;
    }

    public static void balanceArrived(long balance) {
        accountBalance = Money.clamp(balance);
    }

    public static void clear() {
        prices.clear();
        currency = Items.EMERALD;
        currencyStack = new ItemStack(currency);
        currencyStackSize = Math.max(1, currency.getMaxStackSize());
        mode = CurrencyMode.DEFAULT;
        customName = "";
        icon = ItemStack.EMPTY;
        accountBalance = 0L;
        known = false;
        version++;
    }

    // WHY: подписи с именем валюты кешируются у экранов, и номер синхронизации говорит им, что имя,
    // WHY: значок или режим сменились, без сравнения строк в кадре
    public static int version() {
        return version;
    }

    public static boolean known() {
        return known;
    }

    public static CurrencyMode mode() {
        return mode;
    }

    public static boolean accounts() {
        return mode == CurrencyMode.ACCOUNT;
    }

    public static Item currency() {
        return currency;
    }

    public static ItemStack currencyStack() {
        return currencyStack;
    }

    public static String customName() {
        return customName;
    }

    // WHY: пустой значок значит «оператор не выбрал», и тогда рисуется свой значок монеты; в режиме
    // WHY: «Предмет» значком всегда служит сам валютный предмет
    public static boolean iconSet() {
        return accounts() && !icon.isEmpty();
    }

    public static ItemStack icon() {
        return accounts() ? icon : currencyStack;
    }

    public static boolean drawsCoin() {
        return accounts() && icon.isEmpty();
    }

    public static Component unitName() {
        return Money.unit(mode, customName, currency.getDescription());
    }

    public static Component amountText(long amount) {
        return Money.amount(amount, mode, customName, currency.getDescription());
    }

    public static long accountBalance() {
        return accountBalance;
    }

    public static int balance(LocalPlayer player) {
        if (accounts()) return (int) accountBalance;
        return countIn(player.getInventory().items) + countIn(player.getInventory().offhand);
    }

    private static int countIn(List<ItemStack> slots) {
        int total = 0;
        for (int index = 0; index < slots.size(); index++) {
            ItemStack stack = slots.get(index);
            if (stack.is(currency)) total += stack.getCount();
        }
        return total;
    }

    // WHY: в режиме «Счёт» скупка принимает и валютный предмет по номиналу, поэтому он тоже входит в
    // WHY: обещанную выручку. Сумма идёт в long и зажимается: цена не ограничена сверху
    public static int pending(LocalPlayer player) {
        if (prices.isEmpty() && !accounts()) return 0;
        long total = pendingIn(player.getInventory().items) + pendingIn(player.getInventory().offhand);
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    private static long pendingIn(List<ItemStack> slots) {
        long total = 0L;
        for (int index = 0; index < slots.size(); index++) {
            ItemStack stack = slots.get(index);
            if (stack.isEmpty()) continue;
            if (accounts() && stack.is(currency)) {
                total += stack.getCount();
                continue;
            }
            Integer price = prices.get(stack.getItem());
            if (price != null) total += (long) price * stack.getCount();
        }
        return total;
    }

    public static String format(int amount) {
        if (accounts()) return MoneyText.digits(amount);
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
