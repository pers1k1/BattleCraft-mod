package com.persiki84.quarrymod.network;

import com.persiki84.quarrymod.QuarryMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(QuarryMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private PacketHandler() {}

    public static void register() {
        INSTANCE.registerMessage(
                packetId++,
                QuarryFieldPacket.class,
                QuarryFieldPacket::encode,
                QuarryFieldPacket::decode,
                QuarryFieldPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        INSTANCE.registerMessage(
                packetId++,
                QuarryPulsePacket.class,
                QuarryPulsePacket::encode,
                QuarryPulsePacket::decode,
                QuarryPulsePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }
}
