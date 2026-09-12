package com.persiki84.shared.menu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MenuRequestPacket {
    private final String menuId;

    public MenuRequestPacket(String menuId) {
        this.menuId = menuId;
    }

    public static void encode(MenuRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.menuId);
    }

    public static MenuRequestPacket decode(FriendlyByteBuf buf) {
        return new MenuRequestPacket(buf.readUtf());
    }

    public static void handle(MenuRequestPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> answer(packet.menuId, context.get().getSender()));
        context.get().setPacketHandled(true);
    }

    private static void answer(String menuId, ServerPlayer player) {
        if (player == null) return;

        CompoundTag state = MenuStates.snapshot(menuId, player);
        if (state == null) return;

        MenuNetwork.sendState(player, menuId, state, false);
    }
}
