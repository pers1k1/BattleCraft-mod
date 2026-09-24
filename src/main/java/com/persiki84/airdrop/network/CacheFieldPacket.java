package com.persiki84.airdrop.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CacheFieldPacket {
    public static final int EMPTY = 1;
    public static final int MISSING = 2;

    public final boolean running;
    public final long[] positions;
    public final byte[] tiers;
    public final byte[] flags;
    public final int[] refillLeft;
    public final int[] refillTotal;
    public final int[] fills;

    public CacheFieldPacket(boolean running, long[] positions, byte[] tiers, byte[] flags,
                            int[] refillLeft, int[] refillTotal, int[] fills) {
        this.running = running;
        this.positions = positions;
        this.tiers = tiers;
        this.flags = flags;
        this.refillLeft = refillLeft;
        this.refillTotal = refillTotal;
        this.fills = fills;
    }

    public int size() {
        return positions.length;
    }

    public static void encode(CacheFieldPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.running);
        buffer.writeVarInt(packet.size());
        for (int index = 0; index < packet.size(); index++) {
            buffer.writeLong(packet.positions[index]);
            buffer.writeByte(packet.tiers[index]);
            buffer.writeByte(packet.flags[index]);
            buffer.writeVarInt(packet.refillLeft[index] + 1);
            buffer.writeVarInt(packet.refillTotal[index]);
            buffer.writeInt(packet.fills[index]);
        }
    }

    public static CacheFieldPacket decode(FriendlyByteBuf buffer) {
        boolean running = buffer.readBoolean();
        int count = Math.min(buffer.readVarInt(), CacheBroadcast.FIELD_LIMIT);
        long[] positions = new long[count];
        byte[] tiers = new byte[count];
        byte[] flags = new byte[count];
        int[] left = new int[count];
        int[] total = new int[count];
        int[] fills = new int[count];
        for (int index = 0; index < count; index++) {
            positions[index] = buffer.readLong();
            tiers[index] = buffer.readByte();
            flags[index] = buffer.readByte();
            left[index] = buffer.readVarInt() - 1;
            total[index] = buffer.readVarInt();
            fills[index] = buffer.readInt();
        }
        return new CacheFieldPacket(running, positions, tiers, flags, left, total, fills);
    }

    public static void handle(CacheFieldPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.airdrop.client.ClientCacheField.accept(packet)));
        context.get().setPacketHandled(true);
    }
}
