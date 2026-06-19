package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.client.ClientCaptureData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CapturePointRemovePacket {
    private final String pointName;

    public CapturePointRemovePacket(String pointName) {
        this.pointName = pointName;
    }

    public static void encode(CapturePointRemovePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.pointName);
    }

    public static CapturePointRemovePacket decode(FriendlyByteBuf buf) {
        return new CapturePointRemovePacket(buf.readUtf());
    }

    public static void handle(CapturePointRemovePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientCaptureData.removePoint(packet.pointName);
        });
        ctx.get().setPacketHandled(true);
    }
}
