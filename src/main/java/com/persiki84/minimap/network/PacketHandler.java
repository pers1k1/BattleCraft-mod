package com.persiki84.minimap.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("minimap", "main"),
            () -> PROTOCOL_VERSION,
            s -> true,
            s -> true
    );

    public static void register() {
        int id = 0;
        INSTANCE.registerMessage(id++, MapMarkerUpdatePacket.class, MapMarkerUpdatePacket::encode, MapMarkerUpdatePacket::decode, MapMarkerUpdatePacket::handle);
        INSTANCE.registerMessage(id++, MapMarkerSyncPacket.class, MapMarkerSyncPacket::encode, MapMarkerSyncPacket::decode, MapMarkerSyncPacket::handle);
        INSTANCE.registerMessage(id++, MapChunkSyncPacket.class, MapChunkSyncPacket::toBytes, MapChunkSyncPacket::new, MapChunkSyncPacket::handle);
        INSTANCE.registerMessage(id++, PlayerPositionSyncPacket.class, PlayerPositionSyncPacket::encode, PlayerPositionSyncPacket::decode, PlayerPositionSyncPacket::handle);
        INSTANCE.registerMessage(id++, MapChunkInvalidatePacket.class, MapChunkInvalidatePacket::encode, MapChunkInvalidatePacket::decode, MapChunkInvalidatePacket::handle);
        INSTANCE.registerMessage(id++, MapWorldMarkerSyncPacket.class, MapWorldMarkerSyncPacket::encode, MapWorldMarkerSyncPacket::decode, MapWorldMarkerSyncPacket::handle);
    }
}
