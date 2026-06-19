package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.client.ClientCaptureData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CaptureCompletePacket {
    private final String pointName;
    private final String teamName;

    public CaptureCompletePacket(String pointName, String teamName) {
        this.pointName = pointName;
        this.teamName = teamName;
    }

    public static void encode(CaptureCompletePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.pointName);
        buf.writeUtf(packet.teamName);
    }

    public static CaptureCompletePacket decode(FriendlyByteBuf buf) {
        return new CaptureCompletePacket(buf.readUtf(), buf.readUtf());
    }

    public static void handle(CaptureCompletePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientCaptureData.completeCapture(packet.pointName, packet.teamName);
        });
        ctx.get().setPacketHandled(true);
    }
}
