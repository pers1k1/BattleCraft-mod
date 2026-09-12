package com.persiki84.combattimer;

import com.persiki84.knockdown.cap.KnockdownProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.battlecraft.network.S2CCombatStatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatEventHandler {

    private static final Map<UUID, Long> combatTimers = new HashMap<>();
    private static final Map<UUID, ServerBossEvent> bossBars = new HashMap<>();

    @SubscribeEvent
    public void onDamage(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!ModuleSwitches.allows(ModuleId.COMBAT_TIMER)) return;

        if (event.getEntity() instanceof ServerPlayer victim) {
            if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
                startCombat(victim);
                startCombat(attacker);
            }
        }
    }

    private void startCombat(ServerPlayer player) {
        long endTime = System.currentTimeMillis() + (CombatTimerMod.combatDuration * 1000L);
        if (combatTimers.put(player.getUUID(), endTime) == null) {
            tellClient(player, true);
        }

        ServerBossEvent bar = bossBars.computeIfAbsent(player.getUUID(), uuid -> {
            ServerBossEvent newBar = new ServerBossEvent(
                    Component.translatable("combattimer.boss.enter"),
                    BossEvent.BossBarColor.RED,
                    BossEvent.BossBarOverlay.PROGRESS
            );
            newBar.addPlayer(player);
            return newBar;
        });

        bar.setVisible(true);
        bar.setProgress(1.0f);
    }

    // WHY: снятие записи жило внутри проверки боссбара, поэтому пара без бара висела вечно и
    // WHY: игрок оставался «в бою» до перезапуска: клиенту уже ушло true, а false не приходило
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
            if (now >= endTime) {
                if (bar != null) endCombat(bar);
                bossBars.remove(uuid);
                iterator.remove();
                continue;
            }
            if (bar == null) continue;

            long timeLeft = endTime - now;
            bar.setProgress(Math.min(1.0f, (float) timeLeft / (CombatTimerMod.combatDuration * 1000L)));
            bar.setName(Component.translatable("combattimer.boss.timer", (int) (timeLeft / 1000) + 1));
        }
    }

    private void endCombat(ServerBossEvent bar) {
        bar.setVisible(false);
        for (ServerPlayer viewer : bar.getPlayers()) {
            tellClient(viewer, false);
        }
        bar.removeAllPlayers();
    }

    private void tellClient(ServerPlayer player, boolean engaged) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2CCombatStatePacket(engaged));
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        Long endTime = combatTimers.remove(uuid);
        if (endTime != null && System.currentTimeMillis() < endTime) {
            punishCombatLog(player);
        }

        ServerBossEvent bar = bossBars.remove(uuid);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    // WHY: обе карты статические и в одиночной игре переживали выход в меню, поэтому записи
    // WHY: прошлого мира доигрывали в следующем
    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        for (ServerBossEvent bar : bossBars.values()) {
            bar.removeAllPlayers();
        }
        bossBars.clear();
        combatTimers.clear();
    }

    private void punishCombatLog(ServerPlayer player) {
        if (!CombatTimerMod.killOnLogout) {
            announce(player, "combattimer.logout.warning", ChatFormatting.YELLOW);
            return;
        }

        player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> cap.setKnocked(false));
        player.kill();
        announce(player, "combattimer.logout.killed", ChatFormatting.RED);
    }

    private void announce(ServerPlayer player, String key, ChatFormatting color) {
        if (player.getServer() == null) return;

        player.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable(key, player.getName()).withStyle(color), false);
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            if (combatTimers.remove(uuid) != null) {
                ServerBossEvent bar = bossBars.remove(uuid);
                if (bar != null) endCombat(bar);
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
