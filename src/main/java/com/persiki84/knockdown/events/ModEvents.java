package com.persiki84.knockdown.events;

import com.persiki84.knockdown.KnockDownMod;
import com.persiki84.knockdown.cap.KnockdownCapability;
import com.persiki84.knockdown.cap.KnockdownProvider;
import com.persiki84.knockdown.command.KnockdownCommand;
import com.persiki84.knockdown.config.KnockdownConfig;
import com.persiki84.knockdown.network.NetworkHandler;
import com.persiki84.knockdown.network.PacketSyncKnockdown;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.resources.ResourceLocation;
import com.persiki84.knockdown.item.ModItems;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = KnockDownMod.MODID)
public class ModEvents {
    public static final String REVIVE_GRACE = "reviveGrace";
    private static final float SURRENDER_STEP = 2.5f;
    private static final float SURRENDER_DONE = 100.0f;
    private static final int INJECTOR_LOCK_TICKS = 200;
    private static final int SYNC_INTERVAL_TICKS = 20;

    private static final Set<UUID> finishing = new HashSet<>();

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            if (!event.getObject().getCapability(KnockdownProvider.KNOCKDOWN_CAP).isPresent()) {
                event.addCapability(new ResourceLocation(KnockDownMod.MODID, "knockdown_cap"), new KnockdownProvider());
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        KnockdownCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) return;

        event.getOriginal().getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(oldCap ->
                event.getEntity().getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(newCap ->
                        newCap.copyFrom(oldCap)));
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity().level().isClientSide) return;

        event.getEntity().getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
            int bleedTicks = KnockdownConfig.BLEED_TIME_SECONDS.get() * 20;
            cap.setKnocked(false);
            cap.setNextKnockdownTimer(0);
            cap.setReviveProgress(0);
            cap.setInjectorCooldown(0);
            cap.setDeathTimer(bleedTicks);

            send(event.getEntity(), new PacketSyncKnockdown(event.getEntity().getId(),
                    false, 0, bleedTicks, 0, false, 0, false));
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        if (!ModuleSwitches.allows(ModuleId.KNOCKDOWN)) return;
        if (finishing.contains(player.getUUID())) return;
        if (bypassesKnockdown(event.getSource())) return;

        player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> handleLethal(event, player, cap));
    }

    private static boolean bypassesKnockdown(DamageSource source) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) return true;
        if (source.is(DamageTypes.LAVA)) return true;
        if (source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.PLAYER_EXPLOSION)) return true;
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) return true;
        return source.is(DamageTypes.DROWN) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING);
    }

    private static void handleLethal(LivingDeathEvent event, Player player, KnockdownCapability cap) {
        if (cap.isKnocked()) {
            cap.setKnocked(false);
            cap.setNextKnockdownTimer(0);
            send(player, new PacketSyncKnockdown(player.getId(), false, 0, 0, 0, false, 0, false));
            return;
        }

        if (cap.getNextKnockdownTimer() > 0) {
            player.displayClientMessage(Component.translatable("knockdown.msg.too_weak").withStyle(ChatFormatting.RED), true);
            cap.setNextKnockdownTimer(0);
            return;
        }

        knockDown(event, player, cap);
    }

    private static void knockDown(LivingDeathEvent event, Player player, KnockdownCapability cap) {
        event.setCanceled(true);
        player.setHealth(1.0f);

        cap.setKnocked(true);
        cap.setReviveProgress(0);
        cap.setLastAttackerUUID(event.getSource().getEntity() instanceof Player attacker ? attacker.getUUID() : null);

        int bleedTicks = KnockdownConfig.BLEED_TIME_SECONDS.get() * 20;
        cap.setDeathTimer(bleedTicks);

        player.displayClientMessage(Component.translatable("knockdown.msg.heavily_wounded").withStyle(ChatFormatting.RED), true);
        send(player, new PacketSyncKnockdown(player.getId(), true, 0, bleedTicks, 0, false, 0, false));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ModuleSwitches.allows(ModuleId.KNOCKDOWN)) return;

        Player player = event.player;
        player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> tickPlayer(player, cap));
    }

    private static void tickPlayer(Player player, KnockdownCapability cap) {
        if (!cap.isKnocked() && cap.getNextKnockdownTimer() > 0) {
            cap.setNextKnockdownTimer(cap.getNextKnockdownTimer() - 1);
        }
        if (cap.getInjectorCooldown() > 0) {
            cap.setInjectorCooldown(cap.getInjectorCooldown() - 1);
        }
        if (!cap.isKnocked()) return;

        if (!player.level().isClientSide && !tickKnocked(player, cap)) return;
        restrainPose(player);
    }

    private static boolean tickKnocked(Player player, KnockdownCapability cap) {
        if (!reviveGraceRunning(player) && !tickBleed(player, cap)) return false;

        if (player.tickCount % SYNC_INTERVAL_TICKS == 0) {
            syncKnocked(player, cap);
        }
        return true;
    }

    private static boolean reviveGraceRunning(Player player) {
        int grace = player.getPersistentData().getInt(REVIVE_GRACE);
        if (grace <= 0) return false;

        player.getPersistentData().putInt(REVIVE_GRACE, grace - 1);
        return true;
    }

    private static boolean tickBleed(Player player, KnockdownCapability cap) {
        int timer = cap.getDeathTimer();
        if (timer <= 0) {
            finishOff(player, cap);
            return false;
        }
        cap.setDeathTimer(timer - 1);

        if (cap.getReviveProgress() > 0) {
            cap.setReviveProgress(0);
            cap.setInjectorCooldown(INJECTOR_LOCK_TICKS);
            send(player, new PacketSyncKnockdown(player.getId(), true, 0, timer, INJECTOR_LOCK_TICKS,
                    false, cap.getSurrenderProgress(), cap.isSurrendering()));
        }
        return !cap.isSurrendering() || tickSurrender(player, cap);
    }

    private static boolean tickSurrender(Player player, KnockdownCapability cap) {
        cap.addSurrenderProgress(SURRENDER_STEP);
        syncKnocked(player, cap);
        if (cap.getSurrenderProgress() < SURRENDER_DONE) return true;

        finishOff(player, cap);
        return false;
    }

    // WHY: сбитие снимается до удара, и если удар отменит чужая защита (база, лобби), жертва
    // WHY: встанет с 1 HP бесплатно; поэтому неудавшийся удар всегда дожимается kill()
    private static void finishOff(Player player, KnockdownCapability cap) {
        cap.setKnocked(false);
        cap.setNextKnockdownTimer(0);
        send(player, new PacketSyncKnockdown(player.getId(), false, 0, 0, 0, false, 0, false));

        finishing.add(player.getUUID());
        try {
            player.hurt(finishingBlow(player, cap), Float.MAX_VALUE);
            if (player.isAlive()) player.kill();
        } finally {
            finishing.remove(player.getUUID());
        }
    }

    // WHY: GENERIC_KILL пробивает неуязвимость зон и бессмертия, а атакующий в источнике
    // WHY: сохраняет зачёт убийства для награды и доли захвата
    private static DamageSource finishingBlow(Player player, KnockdownCapability cap) {
        UUID attackerId = cap.getLastAttackerUUID();
        Player attacker = attackerId == null ? null : player.level().getPlayerByUUID(attackerId);
        if (attacker == null) return player.damageSources().genericKill();

        return new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.GENERIC_KILL), attacker);
    }

    // WHY: выключенный модуль перестаёт тикать сбитых, и без подъёма они навсегда лежат с 1 HP
    public static void reviveEveryone(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
                if (cap.isKnocked()) standUp(player, cap);
            });
        }
    }

    private static void standUp(ServerPlayer player, KnockdownCapability cap) {
        int bleedTicks = KnockdownConfig.BLEED_TIME_SECONDS.get() * 20;
        cap.setKnocked(false);
        cap.setReviveProgress(0);
        cap.setSelfReviving(false);
        cap.setSurrendering(false);
        cap.setSurrenderProgress(0);
        cap.setDeathTimer(bleedTicks);
        player.getPersistentData().remove(REVIVE_GRACE);
        player.setSwimming(false);
        player.setPose(Pose.STANDING);
        send(player, new PacketSyncKnockdown(player.getId(), false, 0, bleedTicks, 0, false, 0, false));
    }

    private static void restrainPose(Player player) {
        player.setPose(Pose.SWIMMING);
        player.setSwimming(true);
        player.setSprinting(false);
        if (player.level().isClientSide) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(0, 0, 0));
        }
    }

    private static void syncKnocked(Player player, KnockdownCapability cap) {
        send(player, new PacketSyncKnockdown(player.getId(), true, cap.getReviveProgress(), cap.getDeathTimer(),
                cap.getInjectorCooldown(), cap.isSelfReviving(), cap.getSurrenderProgress(), cap.isSurrendering()));
    }

    private static void send(Player player, PacketSyncKnockdown packet) {
        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> (ServerPlayer) player), packet);
    }

    // WHY: запреты сбитого обязаны гаснуть вместе с модулем, иначе игрок с флагом, оставшимся
    // WHY: от включённого модуля, не может ни бить, ни лечиться, ни открыть дверь
    private static boolean knockedWhileEnabled(Entity entity) {
        if (!(entity instanceof Player player) || !ModuleSwitches.allows(ModuleId.KNOCKDOWN)) return false;

        KnockdownCapability cap = player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).orElse(null);
        return cap != null && cap.isKnocked();
    }

    @SubscribeEvent
    public static void onKnockedStrike(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof Player striker) || striker == event.getEntity()) return;
        if (finishing.contains(event.getEntity().getUUID())) return;

        if (knockedWhileEnabled(striker)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (knockedWhileEnabled(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (knockedWhileEnabled(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteractBlockLeft(PlayerInteractEvent.LeftClickBlock event) {
        if (knockedWhileEnabled(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (knockedWhileEnabled(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (knockedWhileEnabled(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteractItem(PlayerInteractEvent.RightClickItem event) {
        if (!knockedWhileEnabled(event.getEntity())) return;
        if (event.getItemStack().getItem() != ModItems.INJECTOR.get()) event.setCanceled(true);
    }
}
