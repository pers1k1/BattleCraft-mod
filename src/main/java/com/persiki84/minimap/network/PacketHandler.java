package com.persiki84.minimap.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("minimap", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextId;

    private static int id() {
        return nextId++;
    }

    public static void register() {
        INSTANCE.registerMessage(id(), MapMarkerUpdatePacket.class, MapMarkerUpdatePacket::encode,
                MapMarkerUpdatePacket::decode, MapMarkerUpdatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id(), MapMarkerSyncPacket.class, MapMarkerSyncPacket::encode,
                MapMarkerSyncPacket::decode, MapMarkerSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id(), MapChunkSyncPacket.class, MapChunkSyncPacket::toBytes,
                MapChunkSyncPacket::new, MapChunkSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id(), PlayerPositionSyncPacket.class, PlayerPositionSyncPacket::encode,
                PlayerPositionSyncPacket::decode, PlayerPositionSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id(), MapWorldMarkerSyncPacket.class, MapWorldMarkerSyncPacket::encode,
                MapWorldMarkerSyncPacket::decode, MapWorldMarkerSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id(), MapUploadPacket.class, MapUploadPacket::encode,
                MapUploadPacket::decode, MapUploadPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id(), MapRequestPacket.class, MapRequestPacket::encode,
                MapRequestPacket::decode, MapRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        INSTANCE.registerMessage(id(), MapTeleportPacket.class, MapTeleportPacket::encode,
                MapTeleportPacket::decode, MapTeleportPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
