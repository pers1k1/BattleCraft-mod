package com.persiki84.minimap.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class MapWorldMarkerSyncPacket {
    public static class WorldMarker {
        public final double x;
        public final double z;
        public final String key;

        public WorldMarker(double x, double z, String key) {
            this.x = x;
            this.z = z;
            this.key = key;
        }
    }

    public final List<WorldMarker> markers;

    public MapWorldMarkerSyncPacket(List<WorldMarker> markers) {
        this.markers = markers;
    }

    public static void encode(MapWorldMarkerSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.markers.size());
        for (WorldMarker marker : msg.markers) {
            buf.writeDouble(marker.x);
            buf.writeDouble(marker.z);
            buf.writeUtf(marker.key);
        }
    }

    public static MapWorldMarkerSyncPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<WorldMarker> markers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            markers.add(new WorldMarker(buf.readDouble(), buf.readDouble(), buf.readUtf()));
        }
        return new MapWorldMarkerSyncPacket(markers);
    }

    public static void handle(MapWorldMarkerSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.persiki84.minimap.client.ClientMapData.syncWorldMarkers(msg.markers);
        });
        ctx.get().setPacketHandled(true);
    }
}
