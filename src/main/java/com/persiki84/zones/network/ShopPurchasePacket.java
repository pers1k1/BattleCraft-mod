package com.persiki84.zones.network;

import com.persiki84.zones.shop.ShopTransactions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShopPurchasePacket {
    private final String sectionId;
    private final String childId;
    private final String entryId;
    private final int amount;

    public ShopPurchasePacket(String sectionId, String childId, String entryId, int amount) {
        this.sectionId = sectionId;
        this.childId = childId == null ? "" : childId;
        this.entryId = entryId;
        this.amount = amount;
    }

    public static void encode(ShopPurchasePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.sectionId);
        buf.writeUtf(packet.childId);
        buf.writeUtf(packet.entryId);
        buf.writeVarInt(packet.amount);
    }

    public static ShopPurchasePacket decode(FriendlyByteBuf buf) {
        return new ShopPurchasePacket(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt());
    }

    public static void handle(ShopPurchasePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ShopTransactions.purchase(player, packet.sectionId, packet.childId, packet.entryId, packet.amount);
        });
        ctx.get().setPacketHandled(true);
    }
}
