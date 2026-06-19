package com.persiki84.minimap.network;

import com.persiki84.minimap.server.ServerMapStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class MapChunkSyncPacket {
    public final String dimension;
    public final List<ChunkData> chunks;

    public MapChunkSyncPacket(String dimension, List<ChunkData> chunks) {
        this.dimension = dimension;
        this.chunks = chunks;
    }

    public MapChunkSyncPacket(FriendlyByteBuf buf) {
        this.dimension = buf.readUtf();
        int size = buf.readVarInt();
        this.chunks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int x = buf.readInt();
            int z = buf.readInt();
            int[] colors = new int[256];
            for (int j = 0; j < 256; j++) {
                colors[j] = buf.readInt();
            }
            chunks.add(new ChunkData(x, z, colors));
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(dimension);
        buf.writeVarInt(chunks.size());
        for (ChunkData chunk : chunks) {
            buf.writeInt(chunk.x);
            buf.writeInt(chunk.z);
            for (int j = 0; j < 256; j++) {
                buf.writeInt(chunk.colors[j]);
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isServer()) {
                net.minecraft.server.level.ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    ServerMapStorage.receiveChunks(dimension, chunks, player);
                }
            } else {
                com.persiki84.minimap.client.ClientMapData.receiveMapChunks(dimension, chunks);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static class ChunkData {
        public final int x;
        public final int z;
        public final int[] colors;

        public ChunkData(int x, int z, int[] colors) {
            this.x = x;
            this.z = z;
            this.colors = colors;
        }
    }
}
