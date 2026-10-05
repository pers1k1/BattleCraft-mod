package com.persiki84.capturepoints.capture;

import com.persiki84.capturepoints.treasury.TeamTreasury;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

final class IncomeShares {
    private static int rotation;

    private IncomeShares() {}

    // WHY: сумма делится на игроков команды в сети поровну, а остаток от деления ходит по кругу между
    // WHY: выплатами: иначе лишнюю единицу всегда получал бы первый в списке игроков сервера
    static void split(List<ServerPlayer> members, ItemStack income, String pointName) {
        int count = members.size();
        if (count == 0) return;

        int base = income.getCount() / count;
        int extra = income.getCount() % count;
        for (int index = 0; index < count; index++) {
            int part = base + (Math.floorMod(index - rotation, count) < extra ? 1 : 0);
            if (part > 0) CapturePointManager.payIncome(members.get(index), income, part, pointName);
        }
        rotation = Math.floorMod(rotation + extra, count);
    }

    static void treasury(MinecraftServer server, String team, ItemStack income, String pointName) {
        int kept = TeamTreasury.deposit(team, income);
        CapturePointManager.persist();
        TeamTreasury.syncTeam(server, team);
        Component line = kept < income.getCount()
                ? Component.translatable("capturepoints.treasury.overflow", pointName, kept, income.getHoverName())
                : Component.translatable("capturepoints.treasury.income", pointName, kept, income.getHoverName());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (team.equals(TeamTreasury.teamOf(player))) {
                player.sendSystemMessage(line.copy().withStyle(ChatFormatting.GREEN));
            }
        }
    }
}
