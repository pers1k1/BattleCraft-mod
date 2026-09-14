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
        registerPhase();
        registerVote();
        registerRoster();
        registerRules();
        registerModules();
        registerCombatState();
        registerClientReport();
        registerGoggles();
        registerConfigWatch();
        registerConfigScan();
        registerConfigReport();
        registerConfigSnapshot();
        registerAnnounce();
        registerAnnounceRequest();
        registerRadioTalk();
    }

    private static void registerRadioTalk() {
        INSTANCE.registerMessage(
                id(),
                S2CRadioTalkPacket.class,
                S2CRadioTalkPacket::encode,
                S2CRadioTalkPacket::decode,
                S2CRadioTalkPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private static void registerAnnounce() {
        INSTANCE.registerMessage(
                id(),
                S2CAnnouncePacket.class,
                S2CAnnouncePacket::encode,
                S2CAnnouncePacket::decode,
                S2CAnnouncePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private static void registerAnnounceRequest() {
        INSTANCE.registerMessage(
                id(),
                C2SAnnouncePacket.class,
                C2SAnnouncePacket::encode,
                C2SAnnouncePacket::decode,
                C2SAnnouncePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }

    private static void registerConfigWatch() {
        INSTANCE.registerMessage(
                id(),
                S2CConfigWatchPacket.class,
                S2CConfigWatchPacket::encode,
                S2CConfigWatchPacket::decode,
                S2CConfigWatchPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private static void registerConfigScan() {
        INSTANCE.registerMessage(
                id(),
                S2CConfigScanPacket.class,
                S2CConfigScanPacket::encode,
                S2CConfigScanPacket::decode,
                S2CConfigScanPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private static void registerConfigReport() {
        INSTANCE.registerMessage(
                id(),
                C2SConfigReportPacket.class,
                C2SConfigReportPacket::encode,
                C2SConfigReportPacket::decode,
                C2SConfigReportPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }

    private static void registerConfigSnapshot() {
        INSTANCE.registerMessage(
                id(),
                C2SConfigSnapshotPacket.class,
                C2SConfigSnapshotPacket::encode,
                C2SConfigSnapshotPacket::decode,
                C2SConfigSnapshotPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }

    private static void registerGoggles() {
        INSTANCE.registerMessage(
                id(),
                S2CGogglesPacket.class,
                S2CGogglesPacket::encode,
                S2CGogglesPacket::decode,
                S2CGogglesPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private static void registerCombatState() {
        INSTANCE.registerMessage(
                id(),
                S2CCombatStatePacket.class,
                S2CCombatStatePacket::encode,
                S2CCombatStatePacket::decode,
                S2CCombatStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private static void registerPhase() {
        INSTANCE.registerMessage(
                id(),
                S2CSyncGamePhasePacket.class,
                S2CSyncGamePhasePacket::encode,
                S2CSyncGamePhasePacket::decode,
                S2CSyncGamePhasePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private static void registerVote() {
        INSTANCE.registerMessage(
                id(),
                S2CSurrenderVotePacket.class,
                S2CSurrenderVotePacket::encode,
                S2CSurrenderVotePacket::decode,
                S2CSurrenderVotePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private static void registerRoster() {
        INSTANCE.registerMessage(
                id(),
                S2CLobbyRosterPacket.class,
                S2CLobbyRosterPacket::encode,
                S2CLobbyRosterPacket::decode,
                S2CLobbyRosterPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private static void registerModules() {
        INSTANCE.registerMessage(
                id(),
                S2CModulesPacket.class,
                S2CModulesPacket::encode,
                S2CModulesPacket::decode,
                S2CModulesPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private static void registerClientReport() {
        INSTANCE.registerMessage(
                id(),
                C2SClientReportPacket.class,
                C2SClientReportPacket::encode,
                C2SClientReportPacket::decode,
                C2SClientReportPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }

    private static void registerRules() {
        INSTANCE.registerMessage(
                id(),
                S2CGameRulesPacket.class,
                S2CGameRulesPacket::encode,
                S2CGameRulesPacket::decode,
                S2CGameRulesPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }
}
