package com.persiki84.shared.menu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MenuStatePacket {
    private final String menuId;
    private final CompoundTag state;
    private final boolean opening;

    public MenuStatePacket(String menuId, CompoundTag state, boolean opening) {
        this.menuId = menuId;
        this.state = state;
        this.opening = opening;
    }

    public static void encode(MenuStatePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.menuId);
        buf.writeNbt(packet.state);
        buf.writeBoolean(packet.opening);
    }

    public static MenuStatePacket decode(FriendlyByteBuf buf) {
        return new MenuStatePacket(buf.readUtf(), buf.readNbt(), buf.readBoolean());
    }

    public static void handle(MenuStatePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.shared.client.menu.MenuData.accept(
                        packet.menuId, packet.state, packet.opening)));
        context.get().setPacketHandled(true);
    }
}
