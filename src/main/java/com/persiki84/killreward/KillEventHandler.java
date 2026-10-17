package com.persiki84.killreward;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Team;
import com.persiki84.shared.economy.Wallet;
import com.persiki84.shared.economy.WalletEntryKind;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class KillEventHandler {

    @SubscribeEvent
    public void onPlayerKill(LivingDeathEvent event) {
        if (!KillRewardMod.rewardsEnabled()) return;

        if (event.isCanceled()) return;

        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (killer == victim) return;
        if (!rivals(killer, victim)) return;

        long wait = RepeatKillGuard.secondsLeft(killer.getUUID(), victim.getUUID());
        if (wait > 0L) {
            killer.displayClientMessage(Component.translatable("killreward.repeat_kill",
                    victim.getName().getString(), wait).withStyle(ChatFormatting.GRAY), true);
            return;
        }

        RepeatKillGuard.remember(killer.getUUID(), victim.getUUID());
        giveReward(killer, victim.getName().getString());
    }

    // WHY: в матче без команды стоят только те, кто ещё не выбрал сторону: такую жертву можно было
    // WHY: завести вторым аккаунтом и убивать ради награды, ни с кем не воюя
    private static boolean rivals(ServerPlayer killer, Player victim) {
        Team victimTeam = victim.getTeam();
        if (victimTeam == null) return !KillRewardMod.matchActive();

        Team killerTeam = killer.getTeam();
        if (killerTeam == null || KillRewardMod.rewardTeamKills) return true;
        return !killerTeam.getName().equals(victimTeam.getName());
    }

    // WHY: награда идёт через кошелёк: валютный предмет в режиме «Счёт» ложится на счёт, всё прочее
    // WHY: в инвентарь. Не влезшее падает под ноги стопками предельного размера: пакет слота пишет
    // WHY: количество байтом, и одна куча больше стака у клиента показалась бы неверно
    private void giveReward(ServerPlayer player, String victimName) {
        Item item = KillRewardMod.getRewardItem();
        if (item == null) return;

        ItemStack reward = new ItemStack(item, KillRewardMod.rewardAmount);
        ItemStack rest = Wallet.hand(player, reward, WalletEntryKind.KILL, victimName);
        int perStack = Math.max(1, rest.getMaxStackSize());
        while (!rest.isEmpty()) {
            player.drop(rest.split(perStack), false);
        }

        Component prize = Wallet.prize(reward);
        player.sendSystemMessage(Component.translatable("killreward.kill_rewarded_prize", victimName, prize)
                .withStyle(ChatFormatting.GREEN));
        KillRewardMod.LOGGER.info("KillReward: {} got {}x {}", player.getName().getString(),
                reward.getCount(), item.getDescriptionId());
        KillRewardMod.lastRewards.put(player.getUUID(), prize);
    }
}
