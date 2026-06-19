package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.client.ClientCaptureData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class CaptureUpdatePacket {
    private final UUID playerId;
    private final String pointName;
    private final float progress;
    private final boolean active;

    public CaptureUpdatePacket(UUID playerId, String pointName, float progress, boolean active) {
        this.playerId = playerId;
        this.pointName = pointName;
        this.progress = progress;
        this.active = active;
    }

    public static void encode(CaptureUpdatePacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.playerId);
        buf.writeUtf(packet.pointName);
        buf.writeFloat(packet.progress);
        buf.writeBoolean(packet.active);
    }

    public static CaptureUpdatePacket decode(FriendlyByteBuf buf) {
        return new CaptureUpdatePacket(
                buf.readUUID(),
                buf.readUtf(),
                buf.readFloat(),
                buf.readBoolean()
        );
    }

    public static void handle(CaptureUpdatePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientCaptureData.updateCapture(packet.playerId, packet.pointName,
                    packet.progress, packet.active);
        });
        ctx.get().setPacketHandled(true);
    }
}
