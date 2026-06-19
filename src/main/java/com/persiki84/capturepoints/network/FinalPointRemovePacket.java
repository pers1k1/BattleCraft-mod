package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.client.ClientCaptureData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FinalPointRemovePacket {
    private final String pointName;

    public FinalPointRemovePacket(String pointName) {
        this.pointName = pointName;
    }

    public static void encode(FinalPointRemovePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.pointName);
    }

    public static FinalPointRemovePacket decode(FriendlyByteBuf buf) {
        return new FinalPointRemovePacket(buf.readUtf());
    }

    public static void handle(FinalPointRemovePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientCaptureData.removeFinalPoint(packet.pointName);
        });
        ctx.get().setPacketHandled(true);
    }
}
