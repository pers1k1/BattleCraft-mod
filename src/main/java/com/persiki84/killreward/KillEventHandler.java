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
        if (!KillRewardMod.modEnabled) return;

        if (event.isCanceled()) return;

        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;

        if (!KillRewardMod.rewardTeamKills) {
            Team killerTeam = killer.getTeam();
            Team victimTeam = victim.getTeam();
            if (killerTeam != null && victimTeam != null &&
                    killerTeam.getName().equals(victimTeam.getName())) {
                return;
            }
        }

        giveReward(killer);
    }

    private void giveReward(ServerPlayer player) {
        var item = KillRewardMod.getRewardItem();
        if (item != null) {
            ItemStack reward = new ItemStack(item, KillRewardMod.rewardAmount);
            String itemName = item.getDescription().getString();

            if (!player.getInventory().add(reward)) {
                player.drop(reward, false);
            }

            player.displayClientMessage(
                    Component.translatable("killreward.reward_received", KillRewardMod.rewardAmount, itemName)
                            .withStyle(ChatFormatting.GREEN),
                    true
            );

            KillRewardMod.LOGGER.info("KillReward: Игрок {} получил {}x {}",
                    player.getName().getString(), KillRewardMod.rewardAmount, itemName);

            String record = KillRewardMod.rewardAmount + "x " + itemName;
            KillRewardMod.lastRewards.put(player.getUUID(), record);
        }
    }
}
