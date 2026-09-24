package com.persiki84.airdrop.network;

import com.persiki84.airdrop.AirDropMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AirDropMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId;

    private PacketHandler() {}

    public static void register() {
        INSTANCE.registerMessage(packetId++, CacheFieldPacket.class,
                CacheFieldPacket::encode, CacheFieldPacket::decode, CacheFieldPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
