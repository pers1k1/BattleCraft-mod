package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.CapturePointsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CapturePointsMod.MOD_ID, "main"),
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
                CaptureStartPacket.class,
                CaptureStartPacket::encode,
                CaptureStartPacket::decode,
                CaptureStartPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
                id(),
                CaptureUpdatePacket.class,
                CaptureUpdatePacket::encode,
                CaptureUpdatePacket::decode,
                CaptureUpdatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        INSTANCE.registerMessage(
                id(),
                CaptureCompletePacket.class,
                CaptureCompletePacket::encode,
                CaptureCompletePacket::decode,
                CaptureCompletePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        INSTANCE.registerMessage(
                id(),
                CapturePointRemovePacket.class,
                CapturePointRemovePacket::encode,
                CapturePointRemovePacket::decode,
                CapturePointRemovePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        INSTANCE.registerMessage(
                id(),
                CapturePointSyncPacket.class,
                CapturePointSyncPacket::encode,
                CapturePointSyncPacket::decode,
                CapturePointSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        INSTANCE.registerMessage(
                id(),
                FinalPointSyncPacket.class,
                FinalPointSyncPacket::encode,
                FinalPointSyncPacket::decode,
                FinalPointSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        INSTANCE.registerMessage(
                id(),
                FinalPointRemovePacket.class,
                FinalPointRemovePacket::encode,
                FinalPointRemovePacket::decode,
                FinalPointRemovePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        INSTANCE.registerMessage(
                id(),
                GlobalMarkerSyncPacket.class,
                GlobalMarkerSyncPacket::encode,
                GlobalMarkerSyncPacket::decode,
                GlobalMarkerSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        INSTANCE.registerMessage(
                id(),
                LocalMarkerOverridePacket.class,
                LocalMarkerOverridePacket::encode,
                LocalMarkerOverridePacket::decode,
                LocalMarkerOverridePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }
}
