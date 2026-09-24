package com.persiki84.minimap;

import com.persiki84.minimap.network.MapMarkerSyncPacket;
import com.persiki84.minimap.network.MapWorldMarkerSyncPacket;
import com.persiki84.minimap.network.PacketHandler;
import com.persiki84.minimap.network.PlayerPositionSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MapManager {
    private static final Map<UUID, MapMarkerSyncPacket.MarkerData> activePrivateMarkers = new ConcurrentHashMap<>();
    private static final Map<UUID, MapMarkerSyncPacket.MarkerData> activeTeamMarkers = new ConcurrentHashMap<>();
    private static final Map<Integer, MapWorldMarkerSyncPacket.WorldMarker> worldMarkers = new ConcurrentHashMap<>();

    private static boolean worldDirty;

    public static void setWorldMarker(int id, double x, double z, String key) {
        worldMarkers.put(id, new MapWorldMarkerSyncPacket.WorldMarker(x, z, key));
        worldDirty = true;
    }

    public static void removeWorldMarker(int id) {
        if (worldMarkers.remove(id) != null) worldDirty = true;
    }

    public static void syncWorldMarkers(ServerPlayer target) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> target),
                new MapWorldMarkerSyncPacket(new ArrayList<>(worldMarkers.values())));
    }

    private static double surfaceY(ServerPlayer player, double x, double z) {
        net.minecraft.core.BlockPos column = net.minecraft.core.BlockPos.containing(x, player.getY(), z);
        if (!player.level().hasChunkAt(column)) return player.getY();

        return player.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                column.getX(), column.getZ());
    }

    public static void handleMarkerUpdate(ServerPlayer player, double x, double z, boolean remove, boolean isTeam) {
        Map<UUID, MapMarkerSyncPacket.MarkerData> markers = isTeam ? activeTeamMarkers : activePrivateMarkers;
        if (remove) {
            markers.remove(player.getUUID());
        } else {
            markers.put(player.getUUID(), new MapMarkerSyncPacket.MarkerData(player.getUUID(),
                    player.getName().getString(), x, surfaceY(player, x, z), z, isTeam,
                    player.level().dimension().location()));
        }
        if (isTeam) {
            syncTeam(player);
        } else {
            syncMarkers(player);
        }
    }

    private static void syncTeam(ServerPlayer player) {
        net.minecraft.world.scores.Team team = player.getTeam();
        if (team == null) {
            syncMarkers(player);
            return;
        }
        for (ServerPlayer member : player.server.getPlayerList().getPlayers()) {
            if (team.isAlliedTo(member.getTeam())) syncMarkers(member);
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

        broadcastWorldMarkers();
        Map<String, List<ServerPlayer>> teams = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            net.minecraft.world.scores.Team team = player.getTeam();
            if (team == null) {
                sendPositions(List.of(player));
            } else {
                teams.computeIfAbsent(team.getName(), key -> new ArrayList<>()).add(player);
            }
        }
        teams.values().forEach(MapManager::sendPositions);
    }

    private static void broadcastWorldMarkers() {
        if (!worldDirty) return;

        worldDirty = false;
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new MapWorldMarkerSyncPacket(new ArrayList<>(worldMarkers.values())));
    }

    private static void sendPositions(List<ServerPlayer> members) {
        List<PlayerPositionSyncPacket.PlayerPos> positions = new ArrayList<>(members.size());
        for (ServerPlayer member : members) {
            positions.add(new PlayerPositionSyncPacket.PlayerPos(member.getUUID(), member.getName().getString(),
                    member.getX(), member.getZ(), member.getYRot(), member.level().dimension().location()));
        }
        PlayerPositionSyncPacket packet = new PlayerPositionSyncPacket(positions);
        for (ServerPlayer member : members) {
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> member), packet);
        }
    }
}
