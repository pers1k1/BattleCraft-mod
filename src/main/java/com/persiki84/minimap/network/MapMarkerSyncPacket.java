package com.persiki84.minimap.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class MapMarkerSyncPacket {
    public static class MarkerData {
        public final UUID playerId;
        public final String playerName;
        public final double x;
        public final double y;
        public final double z;
        public final boolean isTeam;

        public MarkerData(UUID playerId, String playerName, double x, double y, double z, boolean isTeam) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.isTeam = isTeam;
        }
    }

    public final List<MarkerData> markers;

    public MapMarkerSyncPacket(List<MarkerData> markers) {
        this.markers = markers;
    }

    public static void encode(MapMarkerSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.markers.size());
        for (MarkerData marker : msg.markers) {
            buf.writeUUID(marker.playerId);
            buf.writeUtf(marker.playerName);
            buf.writeDouble(marker.x);
            buf.writeDouble(marker.y);
            buf.writeDouble(marker.z);
            buf.writeBoolean(marker.isTeam);
        }
    }

    public static MapMarkerSyncPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<MarkerData> markers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            markers.add(new MarkerData(buf.readUUID(), buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean()));
        }
        return new MapMarkerSyncPacket(markers);
    }

    public static void handle(MapMarkerSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.persiki84.minimap.client.ClientMapData.syncMarkers(msg.markers);
        });
        ctx.get().setPacketHandled(true);
    }
}
