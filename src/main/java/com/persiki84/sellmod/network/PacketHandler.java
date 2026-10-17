package com.persiki84.sellmod.network;

import com.persiki84.sellmod.SellMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SellMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        INSTANCE.registerMessage(
                packetId++,
                SellSyncPacket.class,
                SellSyncPacket::encode,
                SellSyncPacket::decode,
                SellSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        INSTANCE.registerMessage(
                packetId++,
                WalletBalancePacket.class,
                WalletBalancePacket::encode,
                WalletBalancePacket::decode,
                WalletBalancePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        INSTANCE.registerMessage(
                packetId++,
                WalletPayPacket.class,
                WalletPayPacket::encode,
                WalletPayPacket::decode,
                WalletPayPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }

    public static void syncTo(ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), SellSyncPacket.current(player));
    }

    public static void syncToAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTo(player);
        }
    }

    public static void balanceTo(ServerPlayer player, long balance) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new WalletBalancePacket(balance));
    }
}
