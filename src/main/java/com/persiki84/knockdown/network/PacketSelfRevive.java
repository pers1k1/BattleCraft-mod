package com.persiki84.knockdown.network;

import com.persiki84.knockdown.cap.KnockdownProvider;
import com.persiki84.knockdown.config.KnockdownConfig;
import com.persiki84.knockdown.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class PacketSelfRevive {
    private final boolean isPressing;

    public PacketSelfRevive(boolean isPressing) {
        this.isPressing = isPressing;
    }

    public static void encode(PacketSelfRevive msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isPressing);
    }

    public static PacketSelfRevive decode(FriendlyByteBuf buf) {
        return new PacketSelfRevive(buf.readBoolean());
    }

    public static void handle(PacketSelfRevive msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !msg.isPressing) return;

            player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
                if (cap.isKnocked()) {
                    if (cap.getInjectorCooldown() > 0) {
                        player.displayClientMessage(Component.translatable("knockdown.msg.injector_cooldown").withStyle(ChatFormatting.RED), true);
                        return;
                    }

                    ItemStack injectorStack = ItemStack.EMPTY;
                    for (ItemStack stack : player.getInventory().items) {
                        if (!stack.isEmpty() && stack.getItem() == ModItems.INJECTOR.get()) {
                            injectorStack = stack;
                            break;
                        }
                    }
                    if (injectorStack.isEmpty() && player.getOffhandItem().getItem() == ModItems.INJECTOR.get()) {
                        injectorStack = player.getOffhandItem();
                    }

                    if (injectorStack.isEmpty()) return;

                    player.getPersistentData().putInt("reviveGrace", 5);

                    cap.setSelfReviving(true);

                    int useSeconds = KnockdownConfig.INJECTOR_TIME_SECONDS.get();
                    float progressPerTick = 100.0f / (useSeconds * 20.0f);

                    cap.addReviveProgress(progressPerTick);

                    if (cap.getReviveProgress() >= 100.0f) {
                        cap.setKnocked(false);
                        cap.setReviveProgress(0);
                        cap.setInjectorCooldown(0);

                        int bleedTicks = KnockdownConfig.BLEED_TIME_SECONDS.get() * 20;
                        cap.setDeathTimer(bleedTicks);

                        int cooldownTicks = KnockdownConfig.COOLDOWN_TIME_SECONDS.get() * 20;
                        cap.setNextKnockdownTimer(cooldownTicks);

                        player.setHealth(6.0f);
                        player.setPose(Pose.STANDING);

                        if (!player.getAbilities().instabuild) {
                            injectorStack.shrink(1);
                        }

                        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                                new PacketSyncKnockdown(player.getId(), false, 0, bleedTicks, 0, false, 0, false));
                    } else {
                        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                                new PacketSyncKnockdown(player.getId(), true, cap.getReviveProgress(), cap.getDeathTimer(), 0, true, cap.getSurrenderProgress(), cap.isSurrendering()));
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
