package com.persiki84.sellmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class WalletBalancePacket {
    public final long balance;

    public WalletBalancePacket(long balance) {
        this.balance = balance;
    }

    public static void encode(WalletBalancePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarLong(packet.balance);
    }

    public static WalletBalancePacket decode(FriendlyByteBuf buffer) {
        return new WalletBalancePacket(buffer.readVarLong());
    }

    public static void handle(WalletBalancePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> com.persiki84.sellmod.client.ClientSellData.balanceArrived(packet.balance));
        ctx.get().setPacketHandled(true);
    }
}
