package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.client.ClientCaptureData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

public class CapturePointSyncPacket {
    private final Map<String, PointSyncData> pointData;

    public CapturePointSyncPacket(Map<String, PointSyncData> pointData) {
        this.pointData = pointData;
    }

    public static void encode(CapturePointSyncPacket packet, FriendlyByteBuf buf) {
        PointSyncData.writeMap(buf, packet.pointData);
    }

    public static CapturePointSyncPacket decode(FriendlyByteBuf buf) {
        return new CapturePointSyncPacket(PointSyncData.readMap(buf));
    }

    public static void handle(CapturePointSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientCaptureData.syncPoints(packet.pointData));
        ctx.get().setPacketHandled(true);
    }
}
