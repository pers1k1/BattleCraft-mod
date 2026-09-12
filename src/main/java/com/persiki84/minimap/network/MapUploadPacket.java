package com.persiki84.minimap.network;

import com.persiki84.minimap.server.MapShareScope;
import com.persiki84.minimap.server.ServerMapStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class MapUploadPacket {
    public static final int PERMISSION_LEVEL = 2;
    private static final int TARGET_LIMIT = 64;
    public static final int MAX_CHUNKS = 30;

    private final MapShareScope scope;
    private final String target;
    private final String dimension;
    private final List<MapChunkSyncPacket.ChunkData> chunks;
    private final boolean last;

    public MapUploadPacket(MapShareScope scope, String target, String dimension,
                           List<MapChunkSyncPacket.ChunkData> chunks, boolean last) {
        this.scope = scope;
        this.target = target;
        this.dimension = dimension;
        this.chunks = chunks;
        this.last = last;
    }

    public static void encode(MapUploadPacket packet, FriendlyByteBuf buf) {
        if (packet.chunks.size() > MAX_CHUNKS) throw new IllegalArgumentException("Map upload is too large");
        buf.writeByte(packet.scope.ordinal());
        buf.writeUtf(packet.target, TARGET_LIMIT);
        buf.writeUtf(packet.dimension, MapChunkSyncPacket.DIMENSION_LIMIT);
        buf.writeBoolean(packet.last);
        buf.writeVarInt(packet.chunks.size());
        for (MapChunkSyncPacket.ChunkData chunk : packet.chunks) {
            buf.writeInt(chunk.x);
            buf.writeInt(chunk.z);
            for (int pixel = 0; pixel < MapChunkSyncPacket.CHUNK_COLORS; pixel++) {
                buf.writeInt(chunk.colors[pixel]);
            }
        }
    }

    public static MapUploadPacket decode(FriendlyByteBuf buf) {
        MapShareScope scope = MapShareScope.byIndex(buf.readByte());
        String target = buf.readUtf(TARGET_LIMIT);
        String dimension = buf.readUtf(MapChunkSyncPacket.DIMENSION_LIMIT);
        boolean last = buf.readBoolean();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_CHUNKS) throw new IllegalArgumentException("Invalid map upload size");

        List<MapChunkSyncPacket.ChunkData> chunks = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            int x = buf.readInt();
            int z = buf.readInt();
            int[] colors = new int[MapChunkSyncPacket.CHUNK_COLORS];
            for (int pixel = 0; pixel < MapChunkSyncPacket.CHUNK_COLORS; pixel++) {
                colors[pixel] = buf.readInt();
            }
            chunks.add(new MapChunkSyncPacket.ChunkData(x, z, colors));
        }
        return new MapUploadPacket(scope, target, dimension, chunks, last);
    }

    // WHY: загрузка карты это админская операция, поэтому право проверяется на сервере заново,
    // WHY: а не выводится из того, что клиент показал кнопку
    public static void handle(MapUploadPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || !sender.hasPermissions(PERMISSION_LEVEL)) return;

            ServerMapStorage.receiveUpload(sender, packet.scope, packet.target, packet.dimension,
                    packet.chunks, packet.last);
        });
        ctx.get().setPacketHandled(true);
    }
}
