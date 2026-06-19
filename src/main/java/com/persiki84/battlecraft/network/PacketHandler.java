package com.persiki84.battlecraft.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("battlecraft", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static void register() {
        INSTANCE.registerMessage(
                id(),
                S2CSyncGamePhasePacket.class,
                S2CSyncGamePhasePacket::encode,
                S2CSyncGamePhasePacket::decode,
                S2CSyncGamePhasePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }
}
