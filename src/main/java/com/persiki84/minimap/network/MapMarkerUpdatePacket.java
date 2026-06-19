package com.persiki84.minimap.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MapMarkerUpdatePacket {
    public final double x;
    public final double z;
    public final boolean remove;
    public final boolean isTeam;

    public MapMarkerUpdatePacket(double x, double z, boolean remove, boolean isTeam) {
        this.x = x;
        this.z = z;
        this.remove = remove;
        this.isTeam = isTeam;
    }

    public static void encode(MapMarkerUpdatePacket msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.z);
        buf.writeBoolean(msg.remove);
        buf.writeBoolean(msg.isTeam);
    }

    public static MapMarkerUpdatePacket decode(FriendlyByteBuf buf) {
        return new MapMarkerUpdatePacket(buf.readDouble(), buf.readDouble(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(MapMarkerUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                com.persiki84.minimap.MapManager.handleMarkerUpdate(sender, msg.x, msg.z, msg.remove, msg.isTeam);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
