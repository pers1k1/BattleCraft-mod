package com.persiki84.minimap.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class PlayerPositionSyncPacket {
    public static class PlayerPos {
        public final UUID playerId;
        public final String playerName;
        public final double x;
        public final double z;
        public final float yRot;

        public PlayerPos(UUID playerId, String playerName, double x, double z, float yRot) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.x = x;
            this.z = z;
            this.yRot = yRot;
        }
    }

    public final List<PlayerPos> players;

    public PlayerPositionSyncPacket(List<PlayerPos> players) {
        this.players = players;
    }

    public static void encode(PlayerPositionSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.players.size());
        for (PlayerPos p : msg.players) {
            buf.writeUUID(p.playerId);
            buf.writeUtf(p.playerName);
            buf.writeDouble(p.x);
            buf.writeDouble(p.z);
            buf.writeFloat(p.yRot);
        }
    }

    public static PlayerPositionSyncPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<PlayerPos> players = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            players.add(new PlayerPos(buf.readUUID(), buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readFloat()));
        }
        return new PlayerPositionSyncPacket(players);
    }

    public static void handle(PlayerPositionSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.persiki84.minimap.client.ClientMapData.syncPlayers(msg.players);
        });
        ctx.get().setPacketHandled(true);
    }
}
