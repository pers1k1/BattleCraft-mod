package com.persiki84.knockdown.network;

import com.persiki84.knockdown.cap.KnockdownProvider;
import com.persiki84.knockdown.config.KnockdownConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Supplier;

public class PacketReviveAction {
    private final boolean isPressing;

    public PacketReviveAction(boolean isPressing) {
        this.isPressing = isPressing;
    }

    public static void encode(PacketReviveAction msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isPressing);
    }

    public static PacketReviveAction decode(FriendlyByteBuf buf) {
        return new PacketReviveAction(buf.readBoolean());
    }

    public static void handle(PacketReviveAction msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer healer = ctx.get().getSender();
            if (healer == null) return;

            if (msg.isPressing) {
                List<Player> targets = healer.level().getEntitiesOfClass(Player.class, healer.getBoundingBox().inflate(3.0));

                for (Player target : targets) {
                    if (target == healer) continue;

                    target.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
                        if (cap.isKnocked()) {
                            target.getPersistentData().putInt("reviveGrace", 5);

                            cap.setSelfReviving(false);

                            int reviveSeconds = KnockdownConfig.REVIVE_TIME_SECONDS.get();
                            float progressPerTick = 100.0f / (reviveSeconds * 20.0f);

                            cap.addReviveProgress(progressPerTick);

                            if (cap.getReviveProgress() >= 100.0f) {
                                cap.setKnocked(false);
                                cap.setReviveProgress(0);
                                int bleedTicks = KnockdownConfig.BLEED_TIME_SECONDS.get() * 20;
                                cap.setDeathTimer(bleedTicks);
                                int cooldownTicks = KnockdownConfig.COOLDOWN_TIME_SECONDS.get() * 20;
                                cap.setNextKnockdownTimer(cooldownTicks);

                                target.setHealth(4.0f);
                                target.setPose(Pose.STANDING);

                                NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> (ServerPlayer)target),
                                        new PacketSyncKnockdown(target.getId(), false, 0, bleedTicks, 0, false, 0, false));
                            } else {
                                NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> (ServerPlayer)target),
                                        new PacketSyncKnockdown(target.getId(), true, cap.getReviveProgress(), cap.getDeathTimer(), 0, false, cap.getSurrenderProgress(), cap.isSurrendering()));
                            }
                        }
                    });
                    break;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
