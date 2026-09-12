package com.persiki84.minimap.client;

import com.persiki84.minimap.network.MapChunkSyncPacket;
import com.persiki84.minimap.network.MapUploadPacket;
import com.persiki84.minimap.network.PacketHandler;
import com.persiki84.minimap.server.MapShareScope;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class MapUploader {
    private static final int BATCH_SIZE = MapUploadPacket.MAX_CHUNKS;

    private MapUploader() {}

    public static int known() {
        return ClientMapData.chunkData.size();
    }

    // WHY: карта уезжает пачками по BATCH_SIZE, потому что один пакет не унесёт тысячи чанков;
    // WHY: сервер раздаёт и сохраняет её только на пачке с флагом последней
    public static int upload(MapShareScope scope, String target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientMapData.chunkData.isEmpty()) return 0;

        String dimension = mc.level.dimension().location().toString().replace(":", "_");
        List<MapChunkSyncPacket.ChunkData> all = snapshot();
        List<MapChunkSyncPacket.ChunkData> batch = new ArrayList<>();

        for (int index = 0; index < all.size(); index++) {
            batch.add(all.get(index));
            if (batch.size() < BATCH_SIZE) continue;

            send(scope, target, dimension, batch, index + 1 == all.size());
            batch = new ArrayList<>();
        }
        if (!batch.isEmpty()) send(scope, target, dimension, batch, true);
        return all.size();
    }

    // WHY: пачки резались по живой карте, и на ровном кратном BATCH_SIZE флаг последней пачки
    // WHY: мог не выставиться вовсе - сервер тогда принимал чанки и не раздавал их никому
    private static List<MapChunkSyncPacket.ChunkData> snapshot() {
        List<MapChunkSyncPacket.ChunkData> all = new ArrayList<>(ClientMapData.chunkData.size());
        for (Map.Entry<ChunkPos, int[]> entry : ClientMapData.chunkData.entrySet()) {
            all.add(new MapChunkSyncPacket.ChunkData(entry.getKey().x, entry.getKey().z, entry.getValue()));
        }
        return all;
    }

    private static void send(MapShareScope scope, String target, String dimension,
                             List<MapChunkSyncPacket.ChunkData> batch, boolean last) {
        PacketHandler.INSTANCE.sendToServer(
                new MapUploadPacket(scope, target, dimension, batch, last));
    }
}
