package com.persiki84.shared.economy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

// WHY: единственная точка, через которую мод берёт и платит деньги. Режим валюты один на сервер, и
// WHY: покупка, скупка, награды, доход и казна не знают, лежат ли деньги стопками в инвентаре или
// WHY: числом на счёте: иначе каждый поток денег заводил бы свою копию этой развилки
public final class Wallet {
    private static final Supplier<Item> FALLBACK = () -> Items.EMERALD;
    private static Supplier<Item> currency = FALLBACK;
    private static WalletListener listener = WalletListener.NONE;

    private Wallet() {}

    public static void bind(Supplier<Item> currencySource, WalletListener walletListener) {
        currency = currencySource == null ? FALLBACK : currencySource;
        listener = walletListener == null ? WalletListener.NONE : walletListener;
    }

    public static CurrencyMode mode() {
        return Wallets.settings().mode();
    }

    public static boolean accounts() {
        return mode() == CurrencyMode.ACCOUNT;
    }

    public static Item currencyItem() {
        Item item = currency.get();
        return item == null || item == Items.AIR ? Items.EMERALD : item;
    }

    public static boolean isCurrency(ItemStack stack) {
        return !stack.isEmpty() && stack.is(currencyItem());
    }

    public static long balance(ServerPlayer player) {
        return accounts() ? Wallets.open(player).balance() : CurrencyItems.count(player, currencyItem());
    }

    public static long transferable(ServerPlayer player) {
        return accounts() ? Wallets.open(player).transferable() : CurrencyItems.count(player, currencyItem());
    }

    public static long room(ServerPlayer player) {
        if (accounts()) return Wallets.LIMIT - Wallets.open(player).balance();
        return CurrencyItems.room(player, currencyItem());
    }

    public static boolean charge(ServerPlayer player, long amount, WalletEntryKind kind, String subject, int count) {
        if (amount < 0L || amount > balance(player)) return false;
        if (amount == 0L) return true;

        if (accounts()) {
            post(player, -amount, kind, subject, count);
            return true;
        }
        CurrencyItems.take(player, currencyItem(), (int) amount);
        note(player, -amount, kind, subject, count);
        return true;
    }

    // WHY: в режиме «Предмет» не влезшее падает под ноги, как выручка всегда и падала; вызывающий
    // WHY: обязан сам не просить больше, чем room, если ронять нельзя
    public static long credit(ServerPlayer player, long amount, WalletEntryKind kind, String subject, int count) {
        if (amount <= 0L) return 0L;
        if (accounts()) return post(player, Math.min(amount, room(player)), kind, subject, count);

        Item item = currencyItem();
        int whole = (int) Math.min(amount, Integer.MAX_VALUE);
        int left = CurrencyItems.give(player, item, whole);
        if (left > 0) CurrencyItems.drop(player, item, left);
        note(player, whole, kind, subject, count);
        return whole;
    }

    // WHY: награды, доход и казна выдают готовые стопки. Валютная стопка в режиме «Счёт» уходит на
    // WHY: счёт, всё прочее ложится в инвентарь. Возвращается то, что не поместилось: что с ним делать
    // WHY: (уронить, вернуть в казну, отложить), решает вызывающий
    public static ItemStack hand(ServerPlayer player, ItemStack stack, WalletEntryKind kind, String subject) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack rest = stack.copy();
        if (accounts() && isCurrency(rest)) {
            rest.shrink((int) post(player, Math.min(rest.getCount(), room(player)), kind, subject, 0));
            return rest;
        }
        player.getInventory().add(rest);
        int given = stack.getCount() - rest.getCount();
        if (given > 0 && isCurrency(stack)) note(player, given, kind, subject, 0);
        return rest;
    }

    public static Component unitName() {
        return Money.unit(mode(), Wallets.settings().name(), currencyItem().getDescription());
    }

    public static Component amountText(long amount) {
        return Money.amount(amount, mode(), Wallets.settings().name(), currencyItem().getDescription());
    }

    public static Component prize(ItemStack stack) {
        if (isCurrency(stack)) return amountText(stack.getCount());
        return Component.translatable(Money.ITEM_AMOUNT_KEY, stack.getCount(), stack.getHoverName());
    }

    static long post(ServerPlayer player, long delta, WalletEntryKind kind, String subject, int count) {
        if (delta == 0L) return 0L;

        WalletAccount account = Wallets.open(player);
        account.balance(account.balance() + delta);
        record(account, delta, account.balance(), kind, subject, count);
        listener.balanceChanged(player);
        return Math.abs(delta);
    }

    static void post(WalletAccount account, long delta, WalletEntryKind kind, String subject) {
        if (delta == 0L) return;

        account.balance(account.balance() + delta);
        record(account, delta, account.balance(), kind, subject, 0);
        ServerPlayer online = Wallets.online(account.id());
        if (online != null) listener.balanceChanged(online);
    }

    static void note(ServerPlayer player, long delta, WalletEntryKind kind, String subject, int count) {
        long after = CurrencyItems.count(player, currencyItem());
        record(Wallets.open(player), delta, after, kind, subject, count);
    }

    private static void record(WalletAccount account, long delta, long after, WalletEntryKind kind,
                               String subject, int count) {
        account.record(new WalletEntry(kind, delta, after, System.currentTimeMillis(), subject, count));
        Wallets.touch();
    }

    static void settingsChanged() {
        Wallets.touch();
        MinecraftServer server = Wallets.server();
        if (server != null) listener.settingsChanged(server);
    }

    static void balanceChanged(ServerPlayer player) {
        listener.balanceChanged(player);
    }
}
