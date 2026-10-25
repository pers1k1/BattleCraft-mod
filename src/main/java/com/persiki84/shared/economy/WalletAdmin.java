package com.persiki84.shared.economy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.LongSupplier;

// WHY: правки оператора идут той же дорогой, что и игровые деньги: в режиме «Предмет» баланс это
// WHY: стопки в инвентаре, поэтому менять его можно только игроку в сети и только целиком
public final class WalletAdmin {
    private static LongSupplier matchStartMoney = () -> 0L;

    public static final long UNKNOWN = -1L;

    private WalletAdmin() {}

    public static long balanceOf(WalletAccount account) {
        if (Wallet.accounts()) return account.balance();

        ServerPlayer online = Wallets.online(account.id());
        return online == null ? UNKNOWN : CurrencyItems.count(online, Wallet.currencyItem());
    }

    public static Component set(WalletAccount account, long amount, String operator) {
        long current = balanceOf(account);
        if (current == UNKNOWN) return Component.translatable("sellmod.money.error.offline_items", account.name());
        return change(account, Wallets.clamp(amount) - current, operator);
    }

    public static Component change(WalletAccount account, long delta, String operator) {
        if (Wallet.accounts()) return changeAccount(account, delta, operator);

        ServerPlayer online = Wallets.online(account.id());
        if (online == null) return Component.translatable("sellmod.money.error.offline_items", account.name());
        return changeItems(online, delta, operator);
    }

    private static Component changeAccount(WalletAccount account, long delta, String operator) {
        long next = account.balance() + delta;
        if (next < 0L) return Component.translatable("sellmod.money.error.not_enough", account.name());
        if (next > Wallets.LIMIT) return Component.translatable("sellmod.money.error.limit", account.name());

        Wallet.post(account, delta, WalletEntryKind.ADMIN, operator);
        notice(Wallets.online(account.id()), account.balance(), delta);
        return null;
    }

    private static Component changeItems(ServerPlayer player, long delta, String operator) {
        Item item = Wallet.currencyItem();
        if (delta > 0L && delta > CurrencyItems.room(player, item)) {
            return Component.translatable("sellmod.money.error.no_room", player.getDisplayName());
        }
        if (delta < 0L && -delta > CurrencyItems.count(player, item)) {
            return Component.translatable("sellmod.money.error.not_enough", player.getGameProfile().getName());
        }
        if (delta > 0L) CurrencyItems.give(player, item, (int) delta);
        if (delta < 0L) CurrencyItems.take(player, item, (int) -delta);
        if (delta != 0L) Wallet.note(player, delta, WalletEntryKind.ADMIN, operator, 0);
        notice(player, CurrencyItems.count(player, item), delta);
        return null;
    }

    private static void notice(ServerPlayer player, long balance, long delta) {
        if (player == null || delta == 0L) return;
        player.sendSystemMessage(Component.translatable("sellmod.money.admin.notice", Wallet.amountText(balance))
                .withStyle(ChatFormatting.YELLOW));
    }

    // WHY: сброс ставит стартовый баланс каждому известному счёту; игрок, впервые вошедший позже,
    // WHY: получает его же при заведении счёта
    public static int resetAll(WalletEntryKind kind, String operator) {
        return resetAll(kind, operator, Wallets.settings().startBalance());
    }

    private static int resetAll(WalletEntryKind kind, String operator, long start) {
        int changed = 0;
        for (WalletAccount account : Wallets.all()) {
            long delta = start - account.balance();
            if (delta != 0L) {
                Wallet.post(account, delta, kind, operator);
                changed++;
            }
            account.grant(start);
        }
        Wallets.touch();
        return changed;
    }

    // WHY: стартовые деньги режима матча подменяют общую сумму сброса, а не идут вторым начислением:
    // WHY: порядок подписчиков события старта не гарантирован, и два источника перетирали бы друг друга
    public static void matchStartMoney(LongSupplier source) {
        matchStartMoney = source;
    }

    public static void matchStarted() {
        if (!Wallet.accounts() || !Wallets.settings().resetOnStart()) return;
        long override = matchStartMoney.getAsLong();
        resetAll(WalletEntryKind.RESET, "", override > 0L ? Wallets.clamp(override) : Wallets.settings().startBalance());
    }

    public static void mode(CurrencyMode mode) {
        Wallets.settings().mode(mode);
        Wallet.settingsChanged();
    }

    public static void name(String name) {
        Wallets.settings().name(name);
        Wallet.settingsChanged();
    }

    public static void icon(ItemStack icon) {
        Wallets.settings().icon(icon);
        Wallet.settingsChanged();
    }

    public static void startBalance(long amount) {
        Wallets.settings().startBalance(amount);
        Wallet.settingsChanged();
    }

    public static void deathLossPercent(int percent) {
        Wallets.settings().deathLossPercent(percent);
        Wallet.settingsChanged();
    }

    public static void resetOnStart(boolean enabled) {
        Wallets.settings().resetOnStart(enabled);
        Wallet.settingsChanged();
    }
}
