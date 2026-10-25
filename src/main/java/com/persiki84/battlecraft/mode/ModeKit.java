package com.persiki84.battlecraft.mode;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import com.persiki84.shared.economy.Wallet;
import com.persiki84.shared.economy.WalletEntryKind;
import com.persiki84.shared.economy.Wallets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// WHY: набор и стартовые деньги выдаются один раз, после команд старта: те могли переносить и
// WHY: чистить игроков. Деньги в режиме «Счёт» со сбросом на старте ставит сам сброс счетов (сумма
// WHY: режима подменяет общую), иначе они начисляются здесь, чтобы не было второго источника суммы
final class ModeKit {
    private static final String SUBJECT = "mode";

    private ModeKit() {}

    static void handOut(MinecraftServer server, MatchMode mode) {
        boolean resetHandlesMoney = resetsOnStart();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (MatchKills.teamOf(server, player.getScoreboardName()) == null) continue;
            if (!resetHandlesMoney) payStartMoney(player, mode.startMoney());
            give(player, mode);
        }
    }

    // WHY: в режиме «Предмет» не влезшая валюта падала под ноги стопками, и большая стартовая сумма
    // WHY: становилась десятками тысяч сущностей за тик: выдаётся только то, что помещается. Деньги идут
    // WHY: до набора: срезанная сумма пропадает, а не влезший предмет набора хотя бы ложится под ноги
    private static void payStartMoney(ServerPlayer player, long startMoney) {
        if (startMoney <= 0L) return;
        long payable = Math.min(startMoney, Wallet.room(player));
        if (payable < startMoney) {
            BattleCraftMod.LOGGER.warn("Start money of {} cut from {} to {}: no room",
                    player.getScoreboardName(), startMoney, payable);
        }
        if (payable > 0L) Wallet.credit(player, payable, WalletEntryKind.RESET, SUBJECT, 0);
    }

    static boolean resetsOnStart() {
        return ModuleSwitches.allows(ModuleId.SELL) && Wallet.accounts() && Wallets.settings().resetOnStart();
    }

    private static void give(ServerPlayer player, MatchMode mode) {
        for (KitEntry entry : mode.kit()) {
            ItemStack sample = entry.stack();
            if (sample.isEmpty()) {
                BattleCraftMod.LOGGER.warn("Kit item {} of mode {} is unknown, skipped", entry.itemId(), mode.id().id());
                continue;
            }
            hand(player, sample, entry.count());
        }
    }

    // WHY: стопка больше предела предмета кладётся в инвентарь целиком в один слот, поэтому запись
    // WHY: режется на стопки предельного размера. Под ноги падает одна не влезшая стопка, остаток записи
    // WHY: только в журнал: 640 неукладываемых предметов на игрока это сотни сущностей за тик
    private static void hand(ServerPlayer player, ItemStack sample, int count) {
        int portion = Math.max(1, sample.getMaxStackSize());
        for (int left = count; left > 0; left -= portion) {
            ItemStack part = sample.copyWithCount(Math.min(left, portion));
            ItemStack rest = Wallet.hand(player, part, WalletEntryKind.RESET, SUBJECT);
            if (rest.isEmpty()) continue;

            player.drop(rest, false);
            int skipped = left - part.getCount();
            if (skipped > 0) {
                BattleCraftMod.LOGGER.warn("Kit item {} for {}: inventory full, {} more skipped",
                        sample.getItem(), player.getScoreboardName(), skipped);
            }
            return;
        }
    }
}
