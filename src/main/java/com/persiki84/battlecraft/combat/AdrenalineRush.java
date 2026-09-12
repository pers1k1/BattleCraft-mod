package com.persiki84.battlecraft.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "battlecraft")
public final class AdrenalineRush {
    private static final Map<UUID, Deque<Long>> doses = new HashMap<>();
    private static final Map<UUID, Long> rushEnds = new HashMap<>();

    private AdrenalineRush() {}

    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        AdrenalineDose dose = Adrenaline.dose(event.getItem());
        if (dose == null) return;

        long now = player.level().getGameTime();
        if (takeDose(player.getUUID(), now)) punish(player);
        begin(player, dose, now + dose.rushTicks());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || rushEnds.isEmpty()) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        Long endsAt = rushEnds.get(player.getUUID());
        if (endsAt == null || player.level().getGameTime() < endsAt) return;
        end(player);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;

        UUID id = event.getEntity().getUUID();
        doses.remove(id);
        rushEnds.remove(id);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        rushEnds.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        doses.clear();
        rushEnds.clear();
    }

    private static boolean takeDose(UUID id, long now) {
        Deque<Long> taken = doses.computeIfAbsent(id, key -> new ArrayDeque<>());
        while (!taken.isEmpty() && now - taken.peekFirst() >= Adrenaline.WINDOW_TICKS) {
            taken.pollFirst();
        }

        boolean overdose = taken.size() >= Adrenaline.DOSE_LIMIT;
        taken.addLast(now);
        return overdose;
    }

    private static void punish(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.POISON, Adrenaline.POISON_TICKS,
                Adrenaline.OVERDOSE_AMPLIFIER, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, Adrenaline.NAUSEA_TICKS,
                Adrenaline.OVERDOSE_AMPLIFIER, false, true, true));
    }

    private static void begin(ServerPlayer player, AdrenalineDose dose, long endsAt) {
        AttributeInstance recovery = Adrenaline.recovery(player);
        if (recovery == null) return;

        clear(recovery);
        recovery.addTransientModifier(new AttributeModifier(dose.modifierId(), Adrenaline.RUSH_NAME,
                dose.recoveryBonus(), AttributeModifier.Operation.MULTIPLY_TOTAL));
        rushEnds.put(player.getUUID(), endsAt);
    }

    private static void end(ServerPlayer player) {
        rushEnds.remove(player.getUUID());

        AttributeInstance recovery = Adrenaline.recovery(player);
        if (recovery != null) clear(recovery);
    }

    private static void clear(AttributeInstance recovery) {
        for (AdrenalineDose dose : AdrenalineDose.values()) {
            recovery.removeModifier(dose.modifierId());
        }
    }
}
