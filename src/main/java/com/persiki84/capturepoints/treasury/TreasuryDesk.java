package com.persiki84.capturepoints.treasury;

import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.shared.ActionGate;
import com.persiki84.zones.shop.ShopTransactions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// WHY: заявка клиента на казну проверяется здесь целиком: своя команда, магазин не чужой базы, игрок
// WHY: жив и на ногах, частота заявок. Взятое видит вся команда в чате: общая казна без следа
// WHY: превращалась бы в «кто первый, того и тапки» без возможности разобраться
public final class TreasuryDesk {
    private static final String REFRESH_GATE = "treasuryRefreshTick";
    private static final int REFRESH_TICKS = 10;

    private TreasuryDesk() {}

    public static void refresh(ServerPlayer player) {
        if (ActionGate.allow(player, REFRESH_GATE, REFRESH_TICKS)) TeamTreasury.syncTo(player);
    }

    // WHY: на любом отказе игрок получает свежий снимок: экран уже спрятал стопку, которую просили,
    // WHY: и без ответа она осталась бы невидимой до следующего дохода
    public static void take(ServerPlayer player, int slot, ItemStack item, int count) {
        String refusal = refusal(player, slot, item);
        if (refusal != null) {
            if (!refusal.isEmpty()) deny(player, refusal);
            TeamTreasury.syncTo(player);
            return;
        }
        String team = TeamTreasury.teamOf(player);
        ItemStack held = TeamTreasury.peek(team, slot, item);
        int taken = TeamTreasury.take(player, team, slot, item, count);
        if (taken <= 0) {
            deny(player, "capturepoints.treasury.error.full");
            TeamTreasury.syncTo(player);
            return;
        }
        CapturePointManager.persist();
        TeamTreasury.syncTeam(player.server, team);
        announce(player, team, held, taken);
    }

    // WHY: в творческом режиме инвентарь «принимает» всё и обнуляет стопку, даже когда места нет:
    // WHY: казна списала бы предметы в никуда. Пустая строка значит, что отказ уже объявлен
    private static String refusal(ServerPlayer player, int slot, ItemStack item) {
        String team = TeamTreasury.teamOf(player);
        if (team == null) return "capturepoints.treasury.error.no_team";
        if (player.isCreative()) return "capturepoints.treasury.error.creative";
        if (!ShopTransactions.treasuryReady(player, team)) return "";
        return TeamTreasury.peek(team, slot, item).isEmpty() ? "capturepoints.treasury.error.gone" : null;
    }

    private static void announce(ServerPlayer taker, String team, ItemStack held, int taken) {
        Component line = Component.translatable("capturepoints.treasury.taken", taker.getDisplayName(), taken,
                held.getHoverName()).withStyle(ChatFormatting.GRAY);
        for (ServerPlayer player : taker.server.getPlayerList().getPlayers()) {
            if (team.equals(TeamTreasury.teamOf(player))) player.sendSystemMessage(line);
        }
    }

    private static void deny(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
    }
}
