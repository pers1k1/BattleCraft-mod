package com.persiki84.shared.economy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// WHY: в режиме «Счёт» деньги не выпадают из инвентаря, поэтому смерть ничего не стоила бы. Доля
// WHY: счёта уходит убийце-игроку, а смерть без убийцы её сжигает
public final class WalletDeaths {
    private static final long PERCENT = 100L;

    private WalletDeaths() {}

    public static void died(ServerPlayer victim, ServerPlayer killer) {
        int percent = Wallets.settings().deathLossPercent();
        if (!Wallet.accounts() || percent <= 0) return;

        long loss = Wallets.open(victim).balance() * percent / PERCENT;
        if (loss <= 0L) return;

        boolean robbed = killer != null && !killer.getUUID().equals(victim.getUUID());
        Wallet.post(victim, -loss, WalletEntryKind.DEATH, robbed ? killer.getGameProfile().getName() : "", 0);
        Component lost = Wallet.amountText(loss);
        victim.sendSystemMessage((robbed
                ? Component.translatable("sellmod.money.death.lost_to", lost, killer.getDisplayName())
                : Component.translatable("sellmod.money.death.lost", lost)).withStyle(ChatFormatting.RED));
        if (robbed) plunder(killer, victim, loss);
    }

    private static void plunder(ServerPlayer killer, ServerPlayer victim, long loss) {
        long gained = Wallet.post(killer, Math.min(loss, Wallet.room(killer)), WalletEntryKind.PLUNDER,
                victim.getGameProfile().getName(), 0);
        if (gained <= 0L) return;

        killer.sendSystemMessage(Component.translatable("sellmod.money.death.plunder", victim.getDisplayName(),
                Wallet.amountText(gained)).withStyle(ChatFormatting.GREEN));
    }
}
