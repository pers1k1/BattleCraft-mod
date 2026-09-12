package com.persiki84.zones.network;

import com.persiki84.zones.client.ClientZoneData;
import com.persiki84.zones.client.render.VisibleZones;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ZoneRemovePacket {
    private final String zoneId;

    public ZoneRemovePacket(String zoneId) {
        this.zoneId = zoneId;
    }

    public static void encode(ZoneRemovePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.zoneId);
    }

    public static ZoneRemovePacket decode(FriendlyByteBuf buf) {
        return new ZoneRemovePacket(buf.readUtf());
    }

    public static void handle(ZoneRemovePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientZoneData.remove(packet.zoneId);
            VisibleZones.forget(packet.zoneId);
        });
        ctx.get().setPacketHandled(true);
    }
}
