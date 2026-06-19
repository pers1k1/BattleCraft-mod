package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.client.ClientCaptureData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class FinalPointSyncPacket {
    private final Map<String, PointSyncData> pointData;

    public FinalPointSyncPacket(Map<String, PointSyncData> pointData) {
        this.pointData = pointData;
    }

    public static void encode(FinalPointSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.pointData.size());
        for (Map.Entry<String, PointSyncData> entry : packet.pointData.entrySet()) {
            buf.writeUtf(entry.getKey());
            String owner = entry.getValue().owner;
            buf.writeUtf(owner != null ? owner : "");
            buf.writeBlockPos(entry.getValue().pos);
        }
    }

    public static FinalPointSyncPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        Map<String, PointSyncData> pointData = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String pointName = buf.readUtf();
            String owner = buf.readUtf();
            BlockPos pos = buf.readBlockPos();
            pointData.put(pointName, new PointSyncData(owner.isEmpty() ? null : owner, pos));
        }
        return new FinalPointSyncPacket(pointData);
    }

    public static void handle(FinalPointSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientCaptureData.syncFinalPoints(packet.pointData);
        });
        ctx.get().setPacketHandled(true);
    }
}
