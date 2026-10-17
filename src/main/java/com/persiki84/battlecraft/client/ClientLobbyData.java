package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.network.S2CLobbyRosterPacket;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientLobbyData {
    private static List<String> teams = List.of();
    private static List<S2CLobbyRosterPacket.Member> members = List.of();
    private static Map<String, List<S2CLobbyRosterPacket.Member>> byTeam = Map.of();
    private static List<S2CLobbyRosterPacket.Member> undecided = List.of();
    private static int slotsPerTeam;
    private static int readyCount;
    private static int seated;
    private static int version;

    private ClientLobbyData() {}

    // WHY: экран выбора спрашивает состав каждой команды каждый кадр, поэтому раскладка по командам
    // WHY: строится один раз на снимок, а не новым списком на каждый вопрос
    public static void accept(S2CLobbyRosterPacket packet) {
        teams = List.copyOf(packet.teams());
        members = List.copyOf(packet.members());
        slotsPerTeam = packet.slotsPerTeam();
        sort();
        version++;
    }

    public static void clear() {
        teams = List.of();
        members = List.of();
        byTeam = Map.of();
        undecided = List.of();
        slotsPerTeam = 0;
        readyCount = 0;
        seated = 0;
        version++;
    }

    private static void sort() {
        Map<String, List<S2CLobbyRosterPacket.Member>> grouped = new HashMap<>();
        List<S2CLobbyRosterPacket.Member> loose = new ArrayList<>();
        int ready = 0;
        for (S2CLobbyRosterPacket.Member member : members) {
            if (member.team() == null) {
                loose.add(member);
                continue;
            }
            grouped.computeIfAbsent(member.team(), key -> new ArrayList<>()).add(member);
            if (member.ready()) ready++;
        }
        byTeam = grouped;
        undecided = loose;
        readyCount = ready;
        seated = members.size() - loose.size();
    }

    public static int version() {
        return version;
    }

    public static List<String> teams() {
        return teams;
    }

    public static int slotsPerTeam() {
        return slotsPerTeam;
    }

    public static List<S2CLobbyRosterPacket.Member> membersOf(String team) {
        List<S2CLobbyRosterPacket.Member> found = byTeam.get(team);
        return found == null ? List.of() : found;
    }

    public static int sizeOf(String team) {
        return membersOf(team).size();
    }

    public static List<S2CLobbyRosterPacket.Member> undecided() {
        return undecided;
    }

    public static int readyCount() {
        return readyCount;
    }

    public static int seatedCount() {
        return seated;
    }

    public static S2CLobbyRosterPacket.Member self() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return null;

        UUID own = minecraft.player.getUUID();
        for (int index = 0; index < members.size(); index++) {
            S2CLobbyRosterPacket.Member member = members.get(index);
            if (member.uuid().equals(own)) return member;
        }
        return null;
    }

    public static String ownTeam() {
        S2CLobbyRosterPacket.Member own = self();
        return own == null ? null : own.team();
    }

    public static boolean ownReady() {
        S2CLobbyRosterPacket.Member own = self();
        return own != null && own.ready();
    }
}
