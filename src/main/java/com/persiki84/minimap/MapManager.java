package com.persiki84.minimap;

import com.persiki84.minimap.network.MapMarkerSyncPacket;
import com.persiki84.minimap.network.MapWorldMarkerSyncPacket;
import com.persiki84.minimap.network.PacketHandler;
import com.persiki84.minimap.network.PlayerPositionSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MapManager {
    private static final Map<UUID, MapMarkerSyncPacket.MarkerData> activePrivateMarkers = new ConcurrentHashMap<>();
    private static final Map<UUID, MapMarkerSyncPacket.MarkerData> activeTeamMarkers = new ConcurrentHashMap<>();
    private static final Map<Integer, MapWorldMarkerSyncPacket.WorldMarker> worldMarkers = new ConcurrentHashMap<>();

    public static void setWorldMarker(int id, double x, double z, String key) {
        worldMarkers.put(id, new MapWorldMarkerSyncPacket.WorldMarker(x, z, key));
    }

    public static void removeWorldMarker(int id) {
        worldMarkers.remove(id);
    }

    public static void syncWorldMarkers(ServerPlayer target) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> target),
                new MapWorldMarkerSyncPacket(new ArrayList<>(worldMarkers.values())));
    }

    public static void handleMarkerUpdate(ServerPlayer player, double x, double z, boolean remove, boolean isTeam) {
        if (isTeam) {
            if (remove) {
                activeTeamMarkers.remove(player.getUUID());
            } else {
                double y = player.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, (int) x, (int) z);
                activeTeamMarkers.put(player.getUUID(), new MapMarkerSyncPacket.MarkerData(
                        player.getUUID(), player.getName().getString(), x, y, z, true
                ));
            }
            net.minecraft.world.scores.Team team = player.getTeam();
            if (team != null) {
                for (ServerPlayer member : player.server.getPlayerList().getPlayers()) {
                    if (team.isAlliedTo(member.getTeam())) {
                        syncMarkers(member);
                    }
                }
            } else {
                syncMarkers(player);
            }
        } else {
            if (remove) {
                activePrivateMarkers.remove(player.getUUID());
            } else {
                double y = player.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, (int) x, (int) z);
                activePrivateMarkers.put(player.getUUID(), new MapMarkerSyncPacket.MarkerData(
                        player.getUUID(), player.getName().getString(), x, y, z, false
                ));
            }
            syncMarkers(player);
        }
    }

    public static void syncMarkers(ServerPlayer target) {
        List<MapMarkerSyncPacket.MarkerData> markers = new ArrayList<>();

        MapMarkerSyncPacket.MarkerData privateMarker = activePrivateMarkers.get(target.getUUID());
        if (privateMarker != null) {
            markers.add(privateMarker);
        }

        net.minecraft.world.scores.Team team = target.getTeam();
        if (team != null) {
            for (ServerPlayer player : target.server.getPlayerList().getPlayers()) {
                if (team.isAlliedTo(player.getTeam())) {
                    MapMarkerSyncPacket.MarkerData teamMarker = activeTeamMarkers.get(player.getUUID());
                    if (teamMarker != null) {
                        markers.add(teamMarker);
                    }
                }
            }
        } else {
            MapMarkerSyncPacket.MarkerData teamMarker = activeTeamMarkers.get(target.getUUID());
            if (teamMarker != null) {
                markers.add(teamMarker);
            }
        }

        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> target), new MapMarkerSyncPacket(markers));
    }

    public static void clearAll() {
        activePrivateMarkers.clear();
        activeTeamMarkers.clear();
        worldMarkers.clear();
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;

        MapWorldMarkerSyncPacket worldPacket = new MapWorldMarkerSyncPacket(new ArrayList<>(worldMarkers.values()));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), worldPacket);
        }

        Map<String, List<ServerPlayer>> teams = new HashMap<>();
        List<ServerPlayer> noTeamPlayers = new ArrayList<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            net.minecraft.world.scores.Team team = player.getTeam();
            if (team != null) {
                teams.computeIfAbsent(team.getName(), k -> new ArrayList<>()).add(player);
            } else {
                noTeamPlayers.add(player);
            }
        }

        for (List<ServerPlayer> members : teams.values()) {
            List<PlayerPositionSyncPacket.PlayerPos> positions = new ArrayList<>(members.size());
            for (ServerPlayer member : members) {
                positions.add(new PlayerPositionSyncPacket.PlayerPos(
                        member.getUUID(),
                        member.getName().getString(),
                        member.getX(),
                        member.getZ(),
                        member.getYRot()
                ));
            }
            PlayerPositionSyncPacket packet = new PlayerPositionSyncPacket(positions);
            for (ServerPlayer member : members) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> member), packet);
            }
        }

        for (ServerPlayer player : noTeamPlayers) {
            List<PlayerPositionSyncPacket.PlayerPos> positions = new ArrayList<>(1);
            positions.add(new PlayerPositionSyncPacket.PlayerPos(
                    player.getUUID(),
                    player.getName().getString(),
                    player.getX(),
                    player.getZ(),
                    player.getYRot()
            ));
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PlayerPositionSyncPacket(positions));
        }
    }
}
