package com.persiki84.knockdown.network;

import com.persiki84.knockdown.cap.KnockdownProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class PacketSurrenderAction {
    private final boolean isSurrendering;

    public PacketSurrenderAction(boolean isSurrendering) {
        this.isSurrendering = isSurrendering;
    }

    public static void encode(PacketSurrenderAction msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isSurrendering);
    }

    public static PacketSurrenderAction decode(FriendlyByteBuf buf) {
        return new PacketSurrenderAction(buf.readBoolean());
    }

    public static void handle(PacketSurrenderAction msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
                    if (cap.isKnocked()) {
                        cap.setSurrendering(msg.isSurrendering);
                        if (!msg.isSurrendering && cap.getSurrenderProgress() > 0) {
                            cap.setSurrenderProgress(0);
                            NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                                    new PacketSyncKnockdown(player.getId(), true, cap.getReviveProgress(), cap.getDeathTimer(), cap.getInjectorCooldown(), cap.isSelfReviving(), 0, false));
                        }
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
