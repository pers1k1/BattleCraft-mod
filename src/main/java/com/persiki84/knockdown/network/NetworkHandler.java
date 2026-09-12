package com.persiki84.knockdown.network;

import com.persiki84.knockdown.KnockDownMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(KnockDownMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, PacketSyncKnockdown.class, PacketSyncKnockdown::encode, PacketSyncKnockdown::decode,
                PacketSyncKnockdown::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, PacketReviveAction.class, PacketReviveAction::encode, PacketReviveAction::decode,
                PacketReviveAction::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, PacketSelfRevive.class, PacketSelfRevive::encode, PacketSelfRevive::decode,
                PacketSelfRevive::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, PacketSurrenderAction.class, PacketSurrenderAction::encode, PacketSurrenderAction::decode,
                PacketSurrenderAction::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
