package com.persiki84.battlecraft.network;

import com.persiki84.battlecraft.menu.MenuPresence;
import com.persiki84.shared.menu.MenuFace;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// WHY: заявка, а не факт: клиент сообщает только «у меня открыт такой экран», а кому это видно,
// WHY: как часто принимается и держится ли вообще, решает сервер
public class C2SMenuPresencePacket {
    public final MenuFace face;

    public C2SMenuPresencePacket(MenuFace face) {
        this.face = face;
    }

    public static void encode(C2SMenuPresencePacket packet, FriendlyByteBuf buffer) {
        MenuFace.write(packet.face, buffer);
    }

    public static C2SMenuPresencePacket decode(FriendlyByteBuf buffer) {
        return new C2SMenuPresencePacket(MenuFace.read(buffer));
    }

    public static void handle(C2SMenuPresencePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            MenuPresence.claim(sender, packet.face);
        });
        ctx.get().setPacketHandled(true);
    }
}
