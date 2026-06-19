package com.persiki84.combattimer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatEventHandler {

    private static final Map<UUID, Long> combatTimers = new HashMap<>();
    private static final Map<UUID, ServerBossEvent> bossBars = new HashMap<>();

    @SubscribeEvent
    public void onDamage(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getEntity() instanceof ServerPlayer victim) {
            if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
                startCombat(victim);
                startCombat(attacker);
            }
        }
    }

    private void startCombat(ServerPlayer player) {
        long endTime = System.currentTimeMillis() + (CombatTimerMod.combatDuration * 1000L);
        combatTimers.put(player.getUUID(), endTime);

        ServerBossEvent bar = bossBars.computeIfAbsent(player.getUUID(), uuid -> {
            ServerBossEvent newBar = new ServerBossEvent(
                    Component.translatable("combattimer.boss.enter").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    BossEvent.BossBarColor.RED,
                    BossEvent.BossBarOverlay.PROGRESS
            );
            newBar.addPlayer(player);
            return newBar;
        });

        bar.setVisible(true);
        bar.setProgress(1.0f);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        long now = System.currentTimeMillis();
        var iterator = combatTimers.entrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();
            UUID uuid = entry.getKey();
            long endTime = entry.getValue();

            ServerBossEvent bar = bossBars.get(uuid);
            if (bar != null) {
                if (now >= endTime) {
                    bar.setVisible(false);
                    bar.removeAllPlayers();
                    bossBars.remove(uuid);
                    iterator.remove();
                } else {
                    long timeLeft = endTime - now;
                    float progress = (float) timeLeft / (CombatTimerMod.combatDuration * 1000L);
                    bar.setProgress(progress);

                    int secondsLeft = (int) (timeLeft / 1000) + 1;
                    bar.setName(Component.translatable("combattimer.boss.timer",
                            Component.literal(String.valueOf(secondsLeft)).withStyle(ChatFormatting.YELLOW)
                    ).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        if (combatTimers.containsKey(uuid)) {
            long now = System.currentTimeMillis();

            if (now < combatTimers.get(uuid)) {

                if (CombatTimerMod.killOnLogout) {
                    player.setHealth(0);

                    if (player.getServer() != null) {
                        player.getServer().getPlayerList().broadcastSystemMessage(
                                Component.translatable("combattimer.logout.killed", player.getName()).withStyle(ChatFormatting.RED),
                                false
                        );
                    }
                } else {
                    if (player.getServer() != null) {
                        player.getServer().getPlayerList().broadcastSystemMessage(
                                Component.translatable("combattimer.logout.warning", player.getName()).withStyle(ChatFormatting.YELLOW),
                                false
                        );
                    }
                }
            }

            combatTimers.remove(uuid);
            if (bossBars.containsKey(uuid)) {
                bossBars.get(uuid).removeAllPlayers();
                bossBars.remove(uuid);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            if (combatTimers.containsKey(uuid)) {
                combatTimers.remove(uuid);
                if (bossBars.containsKey(uuid)) {
                    bossBars.get(uuid).setVisible(false);
                    bossBars.get(uuid).removeAllPlayers();
                    bossBars.remove(uuid);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            bossBars.remove(uuid);
            combatTimers.remove(uuid);
        }
    }
}
