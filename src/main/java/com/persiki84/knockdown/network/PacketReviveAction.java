package com.persiki84.knockdown.network;

import com.persiki84.knockdown.cap.KnockdownCapability;
import com.persiki84.knockdown.cap.KnockdownProvider;
import com.persiki84.knockdown.events.ModEvents;
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
    private static final int TICKS_PER_SECOND = 20;
    private static final int GRACE_TICKS = 5;
    private static final double REACH = 3.0;
    private static final float REVIVE_HEALTH = 4.0f;

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
            if (healer == null || !msg.isPressing || !ReviveRate.allow(healer)) return;
            if (knocked(healer) || !healer.isAlive() || healer.isSpectator()) return;

            Player target = nearestKnocked(healer);
            if (target == null) return;

            target.getCapability(KnockdownProvider.KNOCKDOWN_CAP)
                    .ifPresent(cap -> advance((ServerPlayer) target, cap));
        });
        ctx.get().setPacketHandled(true);
    }

    private static boolean sameSide(ServerPlayer healer, Player target) {
        if (healer.getTeam() == null && target.getTeam() == null) return true;
        return healer.isAlliedTo(target);
    }

    private static boolean knocked(ServerPlayer player) {
        KnockdownCapability cap = player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).orElse(null);
        return cap != null && cap.isKnocked();
    }

    // WHY: раньше цикл рвался на первом же соседе, поэтому живой тиммейт рядом с лежащим молча
    // WHY: отменял подъём; берём ближайшего именно сбитого
    private static Player nearestKnocked(ServerPlayer healer) {
        List<Player> nearby = healer.level().getEntitiesOfClass(Player.class,
                healer.getBoundingBox().inflate(REACH));
        Player closest = null;
        double best = Double.MAX_VALUE;

        for (Player target : nearby) {
            if (target == healer || !(target instanceof ServerPlayer) || !sameSide(healer, target)) continue;

            KnockdownCapability cap = target.getCapability(KnockdownProvider.KNOCKDOWN_CAP).orElse(null);
            if (cap == null || !cap.isKnocked()) continue;

            double distance = target.distanceToSqr(healer);
            if (distance >= best) continue;

            best = distance;
            closest = target;
        }
        return closest;
    }

    private static void advance(ServerPlayer target, KnockdownCapability cap) {
        target.getPersistentData().putInt(ModEvents.REVIVE_GRACE, GRACE_TICKS);
        cap.setSelfReviving(false);
        cap.addReviveProgress(100.0f / (KnockdownConfig.REVIVE_TIME_SECONDS.get() * TICKS_PER_SECOND));

        if (cap.getReviveProgress() >= 100.0f) {
            stand(target, cap);
            return;
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new PacketSyncKnockdown(target.getId(), true, cap.getReviveProgress(), cap.getDeathTimer(),
                        0, false, cap.getSurrenderProgress(), cap.isSurrendering()));
    }

    private static void stand(ServerPlayer target, KnockdownCapability cap) {
        int bleedTicks = KnockdownConfig.BLEED_TIME_SECONDS.get() * TICKS_PER_SECOND;

        cap.setKnocked(false);
        cap.setReviveProgress(0);
        cap.setDeathTimer(bleedTicks);
        cap.setNextKnockdownTimer(KnockdownConfig.COOLDOWN_TIME_SECONDS.get() * TICKS_PER_SECOND);

        target.setHealth(REVIVE_HEALTH);
        target.setPose(Pose.STANDING);

        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new PacketSyncKnockdown(target.getId(), false, 0, bleedTicks, 0, false, 0, false));
    }
}
