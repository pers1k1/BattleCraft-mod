package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.client.ClientCaptureData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

public class FinalPointSyncPacket {
    private final Map<String, PointSyncData> pointData;

    public FinalPointSyncPacket(Map<String, PointSyncData> pointData) {
        this.pointData = pointData;
    }

    public static void encode(FinalPointSyncPacket packet, FriendlyByteBuf buf) {
        PointSyncData.writeMap(buf, packet.pointData);
    }

    public static FinalPointSyncPacket decode(FriendlyByteBuf buf) {
        return new FinalPointSyncPacket(PointSyncData.readMap(buf));
    }

    public static void handle(FinalPointSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientCaptureData.syncFinalPoints(packet.pointData));
        ctx.get().setPacketHandled(true);
    }
}
