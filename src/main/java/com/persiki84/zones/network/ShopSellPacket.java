package com.persiki84.zones.network;

import com.persiki84.zones.shop.ShopTransactions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShopSellPacket {

    public ShopSellPacket() {}

    public static void encode(ShopSellPacket packet, FriendlyByteBuf buf) {
    }

    public static ShopSellPacket decode(FriendlyByteBuf buf) {
        return new ShopSellPacket();
    }

    public static void handle(ShopSellPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ShopTransactions.sell(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
