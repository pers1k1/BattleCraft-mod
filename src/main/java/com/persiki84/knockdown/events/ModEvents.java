package com.persiki84.knockdown.events;

import com.persiki84.knockdown.KnockDownMod;
import com.persiki84.knockdown.cap.KnockdownCapability;
import com.persiki84.knockdown.cap.KnockdownProvider;
import com.persiki84.knockdown.command.KnockdownCommand;
import com.persiki84.knockdown.config.KnockdownConfig;
import com.persiki84.knockdown.network.NetworkHandler;
import com.persiki84.knockdown.network.PacketSyncKnockdown;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

    private static void finishOff(Player player, KnockdownCapability cap) {
        cap.setKnocked(false);
        cap.setNextKnockdownTimer(0);
        send(player, new PacketSyncKnockdown(player.getId(), false, 0, 0, 0, false, 0, false));

        UUID attackerId = cap.getLastAttackerUUID();
        Player attacker = attackerId == null ? null : player.level().getPlayerByUUID(attackerId);

        finishing.add(player.getUUID());
        try {
            if (attacker != null) {
                player.hurt(player.damageSources().playerAttack(attacker), Float.MAX_VALUE);
            } else {
                player.kill();
            }
        } finally {
            finishing.remove(player.getUUID());
        }
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

    @SubscribeEvent
    public static void onKnockedStrike(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof Player striker) || striker == event.getEntity()) return;
        if (finishing.contains(event.getEntity().getUUID())) return;

        striker.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(c -> { if (c.isKnocked()) event.setCanceled(true); });
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        event.getEntity().getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(c -> { if (c.isKnocked()) event.setCanceled(true); });
    }
    @SubscribeEvent
    public static void onInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        event.getEntity().getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(c -> { if (c.isKnocked()) event.setCanceled(true); });
    }
    @SubscribeEvent
    public static void onInteractBlockLeft(PlayerInteractEvent.LeftClickBlock event) {
        event.getEntity().getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(c -> { if (c.isKnocked()) event.setCanceled(true); });
    }
    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        event.getEntity().getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(c -> { if (c.isKnocked()) event.setCanceled(true); });
    }
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof Player p) p.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(c -> { if (c.isKnocked()) event.setCanceled(true); });
    }
    @SubscribeEvent
    public static void onInteractItem(PlayerInteractEvent.RightClickItem event) {
        event.getEntity().getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
            if (cap.isKnocked()) {
                if (event.getItemStack().getItem() != ModItems.INJECTOR.get()) event.setCanceled(true);
            }
        });
    }
}
