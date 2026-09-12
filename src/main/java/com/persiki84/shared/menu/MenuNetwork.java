package com.persiki84.shared.menu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class MenuNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("battlecraft", "menu"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId;
    private static boolean registered;

    private MenuNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;

        INSTANCE.registerMessage(packetId++, MenuStatePacket.class,
                MenuStatePacket::encode, MenuStatePacket::decode, MenuStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(packetId++, MenuRequestPacket.class,
                MenuRequestPacket::encode, MenuRequestPacket::decode, MenuRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendState(ServerPlayer player, String menuId, CompoundTag state, boolean opening) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new MenuStatePacket(menuId, state, opening));
    }

    public static boolean open(ServerPlayer player, String menuId) {
        CompoundTag state = MenuStates.snapshot(menuId, player);
        if (state == null) return false;

        sendState(player, menuId, state, true);
        return true;
    }

    public static void request(String menuId) {
        INSTANCE.sendToServer(new MenuRequestPacket(menuId));
    }
}
