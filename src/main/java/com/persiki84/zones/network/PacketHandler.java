package com.persiki84.zones.network;

import com.persiki84.zones.ZonesMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ZonesMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static void register() {
        registerToClient();
        registerToServer();
    }

    private static void registerToClient() {
        registerZonesToClient();
        registerMarksToClient();
        registerCatalogToClient();
    }

    private static void registerZonesToClient() {
        INSTANCE.registerMessage(
                id(),
                ZoneSyncAllPacket.class,
                ZoneSyncAllPacket::encode,
                ZoneSyncAllPacket::decode,
                ZoneSyncAllPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        INSTANCE.registerMessage(
                id(),
                ZoneUpsertPacket.class,
                ZoneUpsertPacket::encode,
                ZoneUpsertPacket::decode,
                ZoneUpsertPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        INSTANCE.registerMessage(
                id(),
                ZoneRemovePacket.class,
                ZoneRemovePacket::encode,
                ZoneRemovePacket::decode,
                ZoneRemovePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private static void registerMarksToClient() {
        INSTANCE.registerMessage(
                id(),
                MarkSyncAllPacket.class,
                MarkSyncAllPacket::encode,
                MarkSyncAllPacket::decode,
                MarkSyncAllPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private static void registerCatalogToClient() {
        INSTANCE.registerMessage(
                id(),
                ShopSyncPacket.class,
                ShopSyncPacket::encode,
                ShopSyncPacket::decode,
                ShopSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        INSTANCE.registerMessage(
                id(),
                ModifierSyncPacket.class,
                ModifierSyncPacket::encode,
                ModifierSyncPacket::decode,
                ModifierSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private static void registerToServer() {
        INSTANCE.registerMessage(
                id(),
                ShopPurchasePacket.class,
                ShopPurchasePacket::encode,
                ShopPurchasePacket::decode,
                ShopPurchasePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
                id(),
                ShopSellPacket.class,
                ShopSellPacket::encode,
                ShopSellPacket::decode,
                ShopSellPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
                id(),
                ShopRefreshPacket.class,
                ShopRefreshPacket::encode,
                ShopRefreshPacket::decode,
                ShopRefreshPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        INSTANCE.registerMessage(
                id(),
                MarkEditPacket.class,
                MarkEditPacket::encode,
                MarkEditPacket::decode,
                MarkEditPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }
}
