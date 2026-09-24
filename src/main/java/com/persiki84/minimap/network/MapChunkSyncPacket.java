package com.persiki84.minimap.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class MapChunkSyncPacket {
    public static final int MAX_CHUNKS = 64;
    public static final int DIMENSION_LIMIT = 64;
    public static final int CHUNK_COLORS = 256;

    public final String dimension;
    public final List<ChunkData> chunks;

    public MapChunkSyncPacket(String dimension, List<ChunkData> chunks) {
        this.dimension = dimension;
        this.chunks = chunks;
    }

    // WHY: размер приходит от клиента и до чтения тела задавал ёмкость списка: одним пакетом
    // WHY: с varint на полтора миллиарда сервер уходил в OutOfMemory, поэтому размер зажат
    public MapChunkSyncPacket(FriendlyByteBuf buf) {
        this.dimension = buf.readUtf(DIMENSION_LIMIT);
        int size = Math.min(Math.max(buf.readVarInt(), 0), MAX_CHUNKS);
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
        ctx.get().enqueueWork(() -> com.persiki84.minimap.client.ClientMapData.receiveMapChunks(dimension, chunks));
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
