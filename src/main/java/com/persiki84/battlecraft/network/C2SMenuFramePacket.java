package com.persiki84.battlecraft.network;

import com.persiki84.battlecraft.menu.MenuFrames;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SMenuFramePacket {
    public final byte[] image;

    public C2SMenuFramePacket(byte[] image) {
        this.image = image;
    }

    public static void encode(C2SMenuFramePacket packet, FriendlyByteBuf buffer) {
        buffer.writeByteArray(packet.image);
    }

    public static C2SMenuFramePacket decode(FriendlyByteBuf buffer) {
        return new C2SMenuFramePacket(buffer.readByteArray(MenuFrames.MAX_BYTES));
    }

    public static void handle(C2SMenuFramePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            MenuFrames.claim(sender, packet.image);
        });
        ctx.get().setPacketHandled(true);
    }
}
