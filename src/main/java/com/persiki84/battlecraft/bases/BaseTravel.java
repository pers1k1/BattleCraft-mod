package com.persiki84.battlecraft.bases;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.capturepoints.capture.BaseRules;
import com.persiki84.combattimer.CombatEventHandler;
import com.persiki84.knockdown.cap.KnockdownCapability;
import com.persiki84.knockdown.cap.KnockdownProvider;
import com.persiki84.zones.ZoneLookup;
import com.persiki84.zones.event.ZoneSpawnHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

// WHY: перелёт с базы на базу идёт каналом: стой на месте и не получай урона, иначе он срывается. Нельзя
// WHY: в бою и чаще отката; техника не переносится, игрок сходит с неё. Все условия проверяет сервер
@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID)
public final class BaseTravel {
    private static final double DRIFT = 1.5;
    private static final long SECOND_MS = 1000L;

    private record Journey(BaseSpot target, long endsAt, Vec3 from) {}

    private static final Map<UUID, Journey> journeys = new HashMap<>();
    private static final Map<UUID, Long> cooldowns = new HashMap<>();

    private BaseTravel() {}

    @Nullable
    public static Component start(ServerPlayer player, String id) {
        Component refusal = refusal(player);
        if (refusal != null) return refusal;
        BaseSpot here = BaseNetwork.at(player);
        if (here == null) return text("not_base");
        BaseSpot target = BaseNetwork.find(ZoneLookup.teamNameOf(player), id);
        if (target == null || target.id().equals(here.id()) || here.home() && target.home()) return text("no_target");
        if (journeys.containsKey(player.getUUID())) return text("busy");

        long now = System.currentTimeMillis();
        Long ready = cooldowns.get(player.getUUID());
        if (ready != null && ready > now) return text("cooldown", (ready - now + SECOND_MS - 1) / SECOND_MS);
        journeys.put(player.getUUID(), new Journey(target, now + BaseRules.channelSeconds() * SECOND_MS, player.position()));
        tell(player, text("channel", BaseRules.channelSeconds(), target.name()));
        return null;
    }

    @Nullable
    private static Component refusal(ServerPlayer player) {
        if (!BaseRules.travel()) return text("off");
        if (!player.isAlive() || player.isSpectator() || knocked(player)) return text("down");
        if (CombatEventHandler.inCombat(player.getUUID())) return text("combat");
        return null;
    }

    private static boolean knocked(ServerPlayer player) {
        KnockdownCapability state = player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).orElse(null);
        return state != null && state.isKnocked();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || journeys.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Journey>> pending = journeys.entrySet().iterator();
        while (pending.hasNext()) {
            Map.Entry<UUID, Journey> entry = pending.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) {
                pending.remove();
                continue;
            }
            Component broken = broken(player, entry.getValue());
            if (broken == null && now < entry.getValue().endsAt()) continue;
            pending.remove();
            if (broken != null) {
                tell(player, broken);
            } else {
                arrive(player, entry.getValue().target(), now);
            }
        }
    }

    // WHY: бой и нокдаун проверяются весь канал, а не только на старте: иначе игрок встаёт на базе,
    // WHY: запускает перелёт и отстреливается, пока тот идёт
    @Nullable
    private static Component broken(ServerPlayer player, Journey journey) {
        if (player.position().distanceTo(journey.from()) > DRIFT) return text("moved");
        if (knocked(player)) return text("down");
        if (CombatEventHandler.inCombat(player.getUUID())) return text("combat");
        return null;
    }

    // WHY: срывает канал любое попадание, и отменённое тоже: бессмертие после возрождения гасит атаку
    // WHY: раньше урона, и обстрел стоящего на базе иначе не мешал бы ему улететь
    @SubscribeEvent(receiveCanceled = true)
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || journeys.remove(player.getUUID()) == null) return;
        tell(player, text("hurt"));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        journeys.remove(event.getEntity().getUUID());
    }

    // WHY: за время канала базу могли взять или выключить, и ту, на которую летят, и ту, с которой
    private static void arrive(ServerPlayer player, BaseSpot planned, long now) {
        BaseSpot target = BaseNetwork.find(ZoneLookup.teamNameOf(player), planned.id());
        if (target == null || BaseNetwork.at(player) == null) {
            tell(player, text("lost"));
            return;
        }
        ServerLevel level = ZoneSpawnHandler.levelFor(player, target.dimension());
        player.stopRiding();
        BlockPos spot = ZoneSpawnHandler.placeAt(level, player, target);
        player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), player.getXRot());
        cooldowns.put(player.getUUID(), now + BaseRules.cooldownSeconds() * SECOND_MS);
        tell(player, text("arrived", target.name()));
    }

    // WHY: ход перелёта идёт строкой над хотбаром, а не в чат: игроку со скрытым чатом сервер обычные
    // WHY: системные сообщения не шлёт вовсе, и он улетал бы молча. Тосты мода забирают её себе
    public static void tell(ServerPlayer player, Component message) {
        player.sendSystemMessage(message, true);
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("battlecraft.bases.travel." + key, arguments);
    }
}
