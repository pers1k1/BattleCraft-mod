package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.client.ClientCaptureData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class GlobalMarkerSyncPacket {
    private final boolean captureMarkers;
    private final boolean finalMarkers;

    public GlobalMarkerSyncPacket(boolean captureMarkers, boolean finalMarkers) {
        this.captureMarkers = captureMarkers;
        this.finalMarkers = finalMarkers;
    }

    public static void encode(GlobalMarkerSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.captureMarkers);
        buf.writeBoolean(packet.finalMarkers);
    }

    public static GlobalMarkerSyncPacket decode(FriendlyByteBuf buf) {
        boolean captureMarkers = buf.readBoolean();
        boolean finalMarkers = buf.readBoolean();
        return new GlobalMarkerSyncPacket(captureMarkers, finalMarkers);
    }

    public static void handle(GlobalMarkerSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientCaptureData.setServerMarkerConfig(packet.captureMarkers, packet.finalMarkers);
        });
        ctx.get().setPacketHandled(true);
    }
}
