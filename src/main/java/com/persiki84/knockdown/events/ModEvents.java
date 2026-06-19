package com.persiki84.knockdown.events;

import com.persiki84.knockdown.KnockDownMod;
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
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
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

@Mod.EventBusSubscriber(modid = KnockDownMod.MODID)
public class ModEvents {

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
        if (!event.isWasDeath()) {
            event.getOriginal().getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(oldCap -> {
                event.getEntity().getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(newCap -> {
                    newCap.copyFrom(oldCap);
                });
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.getEntity().level().isClientSide) {
            event.getEntity().getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
                cap.setKnocked(false);
                cap.setNextKnockdownTimer(0);
                cap.setReviveProgress(0);
                cap.setInjectorCooldown(0);
                cap.setDeathTimer(KnockdownConfig.BLEED_TIME_SECONDS.get() * 20);

                NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> (ServerPlayer)event.getEntity()),
                        new PacketSyncKnockdown(event.getEntity().getId(), false, 0, KnockdownConfig.BLEED_TIME_SECONDS.get() * 20, 0, false, 0, false));
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            DamageSource source = event.getSource();

            if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) return;
            if (source.is(DamageTypes.LAVA)) return;
            if (source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.PLAYER_EXPLOSION)) return;
            if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) return;
            if (source.is(DamageTypes.DROWN) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING)) return;

            player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
                if (cap.isKnocked()) {
                    cap.setKnocked(false);
                    cap.setNextKnockdownTimer(0);
                    NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> (ServerPlayer) player),
                            new PacketSyncKnockdown(player.getId(), false, 0, 0, 0, false, 0, false));
                    return;
                }

                if (cap.getNextKnockdownTimer() > 0) {
                    player.displayClientMessage(Component.translatable("knockdown.msg.too_weak").withStyle(ChatFormatting.RED), true);
                    cap.setNextKnockdownTimer(0);
                    return;
                }

                event.setCanceled(true);
                player.setHealth(1.0f);

                cap.setKnocked(true);
                cap.setReviveProgress(0);

                if (source.getEntity() instanceof Player attacker) {
                    cap.setLastAttackerUUID(attacker.getUUID());
                } else {
                    cap.setLastAttackerUUID(null);
                }

                int bleedTicks = KnockdownConfig.BLEED_TIME_SECONDS.get() * 20;
                cap.setDeathTimer(bleedTicks);

                player.displayClientMessage(Component.translatable("knockdown.msg.heavily_wounded").withStyle(ChatFormatting.RED), true);

                NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> (ServerPlayer) player),
                        new PacketSyncKnockdown(player.getId(), true, 0, bleedTicks, 0, false, 0, false));
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Player player = event.player;
            player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {

                if (!cap.isKnocked() && cap.getNextKnockdownTimer() > 0) {
                    cap.setNextKnockdownTimer(cap.getNextKnockdownTimer() - 1);
                }

                if (cap.getInjectorCooldown() > 0) cap.setInjectorCooldown(cap.getInjectorCooldown() - 1);

                if (cap.isKnocked()) {
                    if (!player.level().isClientSide) {
                        int reviveGrace = player.getPersistentData().getInt("reviveGrace");
                        boolean isActivelyReviving = false;

                        if (reviveGrace > 0) {
                            player.getPersistentData().putInt("reviveGrace", reviveGrace - 1);
                            isActivelyReviving = true;
                        }

                        if (!isActivelyReviving) {
                            int timer = cap.getDeathTimer();
                            if (timer > 0) {
                                cap.setDeathTimer(timer - 1);
                            } else {
                                cap.setKnocked(false);
                                cap.setNextKnockdownTimer(0);
                                NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> (ServerPlayer) player),
                                        new PacketSyncKnockdown(player.getId(), false, 0, 0, 0, false, 0, false));

                                Player attacker = null;
                                if (cap.getLastAttackerUUID() != null) {
                                    attacker = player.level().getPlayerByUUID(cap.getLastAttackerUUID());
                                }

                                if (attacker != null) {
                                    player.hurt(player.damageSources().playerAttack(attacker), Float.MAX_VALUE);
                                } else {
                                    player.kill();
                                }
                                return;
                            }

                            if (cap.getReviveProgress() > 0) {
                                cap.setReviveProgress(0);
                                cap.setInjectorCooldown(200);
                                NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> (ServerPlayer) player),
                                        new PacketSyncKnockdown(player.getId(), true, 0, timer, 200, false, cap.getSurrenderProgress(), cap.isSurrendering()));
                            }

                            if (cap.isSurrendering()) {
                                cap.addSurrenderProgress(2.5f);
                                
                                NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> (ServerPlayer) player),
                                        new PacketSyncKnockdown(player.getId(), true, cap.getReviveProgress(), cap.getDeathTimer(), cap.getInjectorCooldown(), cap.isSelfReviving(), cap.getSurrenderProgress(), cap.isSurrendering()));

                                if (cap.getSurrenderProgress() >= 100.0f) {
                                    cap.setKnocked(false);
                                    cap.setNextKnockdownTimer(0);
                                    NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> (ServerPlayer) player),
                                            new PacketSyncKnockdown(player.getId(), false, 0, 0, 0, false, 0, false));

                                    Player attacker = null;
                                    if (cap.getLastAttackerUUID() != null) {
                                        attacker = player.level().getPlayerByUUID(cap.getLastAttackerUUID());
                                    }

                                    if (attacker != null) {
                                        player.hurt(player.damageSources().playerAttack(attacker), Float.MAX_VALUE);
                                    } else {
                                        player.kill();
                                    }
                                    return;
                                }
                            }
                        }

                        if (player.tickCount % 20 == 0) {
                            NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> (ServerPlayer) player),
                                    new PacketSyncKnockdown(player.getId(), true, cap.getReviveProgress(), cap.getDeathTimer(), cap.getInjectorCooldown(), cap.isSelfReviving(), cap.getSurrenderProgress(), cap.isSurrendering()));
                        }
                    }

                    player.setPose(Pose.SWIMMING);
                    player.setSwimming(true);
                    player.setSprinting(false);
                    if (player.level().isClientSide) {
                        player.setDeltaMovement(player.getDeltaMovement().multiply(0, 0, 0));
                    }
                }
            });
        }
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
