package com.persiki84.zones.network;

import com.persiki84.zones.Zone;
import com.persiki84.zones.client.ClientZoneData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class ZoneSyncAllPacket {
    private final List<Zone> zones;

    public ZoneSyncAllPacket(Collection<Zone> zones) {
        this.zones = new ArrayList<>(zones);
    }

    public static void encode(ZoneSyncAllPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.zones.size());
        for (Zone zone : packet.zones) {
            zone.write(buf);
        }
    }

    public static ZoneSyncAllPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<Zone> zones = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            zones.add(Zone.read(buf));
        }
        return new ZoneSyncAllPacket(zones);
    }

    public static void handle(ZoneSyncAllPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientZoneData.replaceAll(packet.zones));
        ctx.get().setPacketHandled(true);
    }
}
