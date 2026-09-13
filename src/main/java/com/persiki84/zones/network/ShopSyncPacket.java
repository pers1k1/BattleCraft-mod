package com.persiki84.zones.network;

import com.persiki84.zones.client.ClientShopData;
import com.persiki84.zones.shop.ShopSection;
import com.persiki84.zones.shop.ShopViewer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class ShopSyncPacket {
    private final List<ShopSection> sections = new ArrayList<>();
    private final ShopViewer viewer;
    private final boolean full;

    public ShopSyncPacket(Collection<ShopSection> sections, ShopViewer viewer, boolean full) {
        this.viewer = viewer;
        this.full = full;
        for (ShopSection section : sections) {
            if (full || section.access().visibleTo(viewer.team())) this.sections.add(section);
        }
    }

    private ShopSyncPacket(List<ShopSection> decoded) {
        this.sections.addAll(decoded);
        this.viewer = ShopViewer.NOBODY;
        this.full = true;
    }

    public static void encode(ShopSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.sections.size());
        for (ShopSection section : packet.sections) {
            section.write(buf, packet.viewer, packet.full);
        }
    }

    public static ShopSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<ShopSection> sections = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            sections.add(ShopSection.read(buf));
        }
        return new ShopSyncPacket(sections);
    }

    public static void handle(ShopSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientShopData.replaceAll(packet.sections));
        ctx.get().setPacketHandled(true);
    }
}
