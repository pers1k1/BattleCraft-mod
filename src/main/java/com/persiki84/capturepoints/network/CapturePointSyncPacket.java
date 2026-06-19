package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.client.ClientCaptureData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class CapturePointSyncPacket {
    private final Map<String, PointSyncData> pointData;

    public CapturePointSyncPacket(Map<String, PointSyncData> pointData) {
        this.pointData = pointData;
    }

    public static void encode(CapturePointSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.pointData.size());
        for (Map.Entry<String, PointSyncData> entry : packet.pointData.entrySet()) {
            buf.writeUtf(entry.getKey());
            String owner = entry.getValue().owner;
            buf.writeUtf(owner != null ? owner : "");
            buf.writeBlockPos(entry.getValue().pos);
        }
    }

    public static CapturePointSyncPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        Map<String, PointSyncData> pointData = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String pointName = buf.readUtf();
            String owner = buf.readUtf();
            BlockPos pos = buf.readBlockPos();
            pointData.put(pointName, new PointSyncData(owner.isEmpty() ? null : owner, pos));
        }
        return new CapturePointSyncPacket(pointData);
    }

    public static void handle(CapturePointSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientCaptureData.syncPoints(packet.pointData);
        });
        ctx.get().setPacketHandled(true);
    }
}
