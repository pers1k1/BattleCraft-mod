package com.persiki84.minimap.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MapChunkInvalidatePacket {
    public final int chunkX;
    public final int chunkZ;

    public MapChunkInvalidatePacket(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public static void encode(MapChunkInvalidatePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.chunkX);
        buf.writeInt(msg.chunkZ);
    }

    public static MapChunkInvalidatePacket decode(FriendlyByteBuf buf) {
        return new MapChunkInvalidatePacket(buf.readInt(), buf.readInt());
    }

    public static void handle(MapChunkInvalidatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (!ctx.get().getDirection().getReceptionSide().isServer()) {
                ChunkPos cp = new ChunkPos(msg.chunkX, msg.chunkZ);
                com.persiki84.minimap.client.ClientMapData.chunkData.remove(cp);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
