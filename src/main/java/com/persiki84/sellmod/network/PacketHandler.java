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
    private static final String PROTOCOL_VERSION = "1";
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
    }

    public static void syncTo(ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), SellSyncPacket.current());
    }

    public static void syncToAll(MinecraftServer server) {
        if (server == null) return;
        INSTANCE.send(PacketDistributor.ALL.noArg(), SellSyncPacket.current());
    }
}
