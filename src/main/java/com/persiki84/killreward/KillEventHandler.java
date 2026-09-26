package com.persiki84.killreward;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Team;
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

    // WHY: награда больше стака одним предметом уходила клиенту байтом количества и терялась:
    // WHY: выдаётся стаками предельного размера, остаток падает под ноги
    private static void hand(ServerPlayer player, net.minecraft.world.item.Item item, int amount) {
        int left = amount;
        while (left > 0) {
            ItemStack stack = new ItemStack(item, Math.min(left, item.getMaxStackSize()));
            left -= stack.getCount();
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
    }

    private void giveReward(ServerPlayer player, String victimName) {
        var item = KillRewardMod.getRewardItem();
        if (item != null) {
            String itemName = item.getDescription().getString();
            hand(player, item, KillRewardMod.rewardAmount);

            player.sendSystemMessage(
                    Component.translatable("killreward.kill_rewarded", victimName,
                            KillRewardMod.rewardAmount, itemName).withStyle(ChatFormatting.GREEN)
            );

            KillRewardMod.LOGGER.info("KillReward: Игрок {} получил {}x {}",
                    player.getName().getString(), KillRewardMod.rewardAmount, itemName);

            String record = KillRewardMod.rewardAmount + "x " + itemName;
            KillRewardMod.lastRewards.put(player.getUUID(), record);
        }
    }
}
