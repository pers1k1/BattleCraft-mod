package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.network.S2CLobbyRosterPacket;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public final class ClientLobbyData {
    private static List<String> teams = List.of();
    private static List<S2CLobbyRosterPacket.Member> members = List.of();
    private static int slotsPerTeam;

    private ClientLobbyData() {}

    public static void accept(S2CLobbyRosterPacket packet) {
        teams = packet.teams();
        members = packet.members();
        slotsPerTeam = packet.slotsPerTeam();
    }

    public static void clear() {
        teams = List.of();
        members = List.of();
        slotsPerTeam = 0;
    }

    public static List<String> teams() {
        return teams;
    }

    public static int slotsPerTeam() {
        return slotsPerTeam;
    }

    public static List<S2CLobbyRosterPacket.Member> membersOf(String team) {
        List<S2CLobbyRosterPacket.Member> result = new ArrayList<>();
        for (S2CLobbyRosterPacket.Member member : members) {
            if (team.equals(member.team())) result.add(member);
        }
        return result;
    }

    public static List<S2CLobbyRosterPacket.Member> undecided() {
        List<S2CLobbyRosterPacket.Member> result = new ArrayList<>();
        for (S2CLobbyRosterPacket.Member member : members) {
            if (member.team() == null) result.add(member);
        }
        return result;
    }

    public static String ownTeam() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return null;

        for (S2CLobbyRosterPacket.Member member : members) {
            if (member.uuid().equals(minecraft.player.getUUID())) return member.team();
        }
        return null;
    }

    public static boolean ownReady() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;

        for (S2CLobbyRosterPacket.Member member : members) {
            if (member.uuid().equals(minecraft.player.getUUID())) return member.ready();
        }
        return false;
    }
}
