package com.persiki84.knockdown.network;

import com.persiki84.knockdown.cap.KnockdownCapability;
import com.persiki84.knockdown.cap.KnockdownProvider;
import com.persiki84.knockdown.events.ModEvents;
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
    private static final int TICKS_PER_SECOND = 20;
    private static final int GRACE_TICKS = 5;
    private static final float SELF_REVIVE_HEALTH = 6.0f;

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
            if (player == null || !msg.isPressing || !ReviveRate.allow(player)) return;

            player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> advance(player, cap));
        });
        ctx.get().setPacketHandled(true);
    }

    private static void advance(ServerPlayer player, KnockdownCapability cap) {
        if (!cap.isKnocked()) return;

        if (cap.getInjectorCooldown() > 0) {
            player.displayClientMessage(
                    Component.translatable("knockdown.msg.injector_cooldown").withStyle(ChatFormatting.RED), true);
            return;
        }

        ItemStack injector = heldInjector(player);
        if (injector.isEmpty()) return;

        player.getPersistentData().putInt(ModEvents.REVIVE_GRACE, GRACE_TICKS);
        cap.setSelfReviving(true);
        cap.addReviveProgress(100.0f / (KnockdownConfig.INJECTOR_TIME_SECONDS.get() * TICKS_PER_SECOND));

        if (cap.getReviveProgress() >= 100.0f) {
            stand(player, cap, injector);
            return;
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new PacketSyncKnockdown(player.getId(), true, cap.getReviveProgress(), cap.getDeathTimer(),
                        0, true, cap.getSurrenderProgress(), cap.isSurrendering()));
    }

    private static ItemStack heldInjector(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == ModItems.INJECTOR.get()) return stack;
        }
        if (player.getOffhandItem().getItem() == ModItems.INJECTOR.get()) return player.getOffhandItem();

        return ItemStack.EMPTY;
    }

    private static void stand(ServerPlayer player, KnockdownCapability cap, ItemStack injector) {
        int bleedTicks = KnockdownConfig.BLEED_TIME_SECONDS.get() * TICKS_PER_SECOND;

        cap.setKnocked(false);
        cap.setReviveProgress(0);
        cap.setInjectorCooldown(0);
        cap.setDeathTimer(bleedTicks);
        cap.setNextKnockdownTimer(KnockdownConfig.COOLDOWN_TIME_SECONDS.get() * TICKS_PER_SECOND);

        player.setHealth(SELF_REVIVE_HEALTH);
        player.setPose(Pose.STANDING);
        if (!player.getAbilities().instabuild) injector.shrink(1);

        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new PacketSyncKnockdown(player.getId(), false, 0, bleedTicks, 0, false, 0, false));
    }
}
