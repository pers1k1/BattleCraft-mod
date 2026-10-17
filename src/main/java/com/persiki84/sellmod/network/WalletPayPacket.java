package com.persiki84.sellmod.network;

import com.persiki84.sellmod.money.MoneyPayments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

// WHY: заявка на перевод из окна магазина. Клиент называет только получателя и сумму, всё остальное
// WHY: (кто в сети, хватает ли денег, есть ли место, не слишком ли часто) сервер считает сам
public class WalletPayPacket {
    public final UUID target;
    public final long amount;

    public WalletPayPacket(UUID target, long amount) {
        this.target = target;
        this.amount = amount;
    }

    public static void encode(WalletPayPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.target);
        buffer.writeVarLong(packet.amount);
    }

    public static WalletPayPacket decode(FriendlyByteBuf buffer) {
        return new WalletPayPacket(buffer.readUUID(), buffer.readVarLong());
    }

    public static void handle(WalletPayPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender == null) return;
            MoneyPayments.pay(sender, sender.server.getPlayerList().getPlayer(packet.target), packet.amount);
        });
        context.setPacketHandled(true);
    }
}
