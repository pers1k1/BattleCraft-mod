package com.persiki84.knockdown.network;

import com.persiki84.knockdown.cap.KnockdownProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncKnockdown {
    private final int entityId;
    private final boolean isKnocked;
    private final float reviveProgress;
    private final int deathTimer;
    private final int injectorCooldown;
    private final boolean isSelfReviving;
    private final float surrenderProgress;
    private final boolean isSurrendering;

    public PacketSyncKnockdown(int entityId, boolean isKnocked, float reviveProgress, int deathTimer, int injectorCooldown, boolean isSelfReviving, float surrenderProgress, boolean isSurrendering) {
        this.entityId = entityId;
        this.isKnocked = isKnocked;
        this.reviveProgress = reviveProgress;
        this.deathTimer = deathTimer;
        this.injectorCooldown = injectorCooldown;
        this.isSelfReviving = isSelfReviving;
        this.surrenderProgress = surrenderProgress;
        this.isSurrendering = isSurrendering;
    }

    public static void encode(PacketSyncKnockdown msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeBoolean(msg.isKnocked);
        buf.writeFloat(msg.reviveProgress);
        buf.writeInt(msg.deathTimer);
        buf.writeInt(msg.injectorCooldown);
        buf.writeBoolean(msg.isSelfReviving);
        buf.writeFloat(msg.surrenderProgress);
        buf.writeBoolean(msg.isSurrendering);
    }

    public static PacketSyncKnockdown decode(FriendlyByteBuf buf) {
        return new PacketSyncKnockdown(buf.readInt(), buf.readBoolean(), buf.readFloat(), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(PacketSyncKnockdown msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handle(msg));
        });
        ctx.get().setPacketHandled(true);
    }

    private static class ClientPacketHandler {
        public static void handle(PacketSyncKnockdown msg) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null) {
                Player localPlayer = (Player) mc.level.getEntity(msg.entityId);
                if (localPlayer != null) {
                    localPlayer.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
                        cap.setKnocked(msg.isKnocked);
                        cap.setReviveProgress(msg.reviveProgress);
                        cap.setDeathTimer(msg.deathTimer);
                        cap.setInjectorCooldown(msg.injectorCooldown);
                        cap.setSelfReviving(msg.isSelfReviving);
                        cap.setSurrenderProgress(msg.surrenderProgress);
                        cap.setSurrendering(msg.isSurrendering);
                    });
                }
            }
        }
    }
}
