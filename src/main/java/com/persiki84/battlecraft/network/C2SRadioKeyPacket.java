package com.persiki84.battlecraft.network;

import com.persiki84.battlecraft.radio.RadioAir;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// WHY: заявка, а не разрешение: клиент говорит только «клавиша эфира зажата», а есть ли рация,
// WHY: включена ли она и кто это слышит, сервер считает сам - правленый клиент иначе вещал бы
// WHY: на всю карту без рации вовсе
public class C2SRadioKeyPacket {
    public final boolean talking;

    public C2SRadioKeyPacket(boolean talking) {
        this.talking = talking;
    }

    public static void encode(C2SRadioKeyPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.talking);
    }

    public static C2SRadioKeyPacket decode(FriendlyByteBuf buffer) {
        return new C2SRadioKeyPacket(buffer.readBoolean());
    }

    public static void handle(C2SRadioKeyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            RadioAir.set(sender, packet.talking);
        });
        ctx.get().setPacketHandled(true);
    }
}
