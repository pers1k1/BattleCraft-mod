package com.persiki84.zones.network;

import com.persiki84.zones.Zone;
import com.persiki84.zones.client.ClientZoneData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ZoneUpsertPacket {
    private final Zone zone;

    public ZoneUpsertPacket(Zone zone) {
        this.zone = zone;
    }

    public static void encode(ZoneUpsertPacket packet, FriendlyByteBuf buf) {
        packet.zone.write(buf);
    }

    public static ZoneUpsertPacket decode(FriendlyByteBuf buf) {
        return new ZoneUpsertPacket(Zone.read(buf));
    }

    public static void handle(ZoneUpsertPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientZoneData.upsert(packet.zone));
        ctx.get().setPacketHandled(true);
    }
}
