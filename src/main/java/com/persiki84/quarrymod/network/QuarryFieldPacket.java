package com.persiki84.quarrymod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class QuarryFieldPacket {
    public final List<String> ores;
    public final long[] positions;
    public final int[] oreIndices;
    public final int[] multipliers;
    public final int[] remaining;
    public final int[] totals;

    public QuarryFieldPacket(List<String> ores, long[] positions, int[] oreIndices,
                             int[] multipliers, int[] remaining, int[] totals) {
        this.ores = ores;
        this.positions = positions;
        this.oreIndices = oreIndices;
        this.multipliers = multipliers;
        this.remaining = remaining;
        this.totals = totals;
    }

    public static void encode(QuarryFieldPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.ores.size());
        for (String ore : packet.ores) {
            buffer.writeUtf(ore);
        }
        buffer.writeVarInt(packet.positions.length);
        for (int index = 0; index < packet.positions.length; index++) {
            buffer.writeLong(packet.positions[index]);
            buffer.writeVarInt(packet.oreIndices[index]);
            buffer.writeVarInt(packet.multipliers[index]);
            buffer.writeVarInt(packet.remaining[index]);
            buffer.writeVarInt(packet.totals[index]);
        }
    }

    public static QuarryFieldPacket decode(FriendlyByteBuf buffer) {
        int dictionary = buffer.readVarInt();
        List<String> ores = new ArrayList<>(dictionary);
        for (int index = 0; index < dictionary; index++) {
            ores.add(buffer.readUtf());
        }

        int count = buffer.readVarInt();
        long[] positions = new long[count];
        int[] oreIndices = new int[count];
        int[] multipliers = new int[count];
        int[] remaining = new int[count];
        int[] totals = new int[count];
        for (int index = 0; index < count; index++) {
            positions[index] = buffer.readLong();
            oreIndices[index] = buffer.readVarInt();
            multipliers[index] = buffer.readVarInt();
            remaining[index] = buffer.readVarInt();
            totals[index] = buffer.readVarInt();
        }
        return new QuarryFieldPacket(ores, positions, oreIndices, multipliers, remaining, totals);
    }

    public BlockPos position(int index) {
        return BlockPos.of(positions[index]);
    }

    public String ore(int index) {
        int slot = oreIndices[index];
        return slot >= 0 && slot < ores.size() ? ores.get(slot) : "";
    }

    public int size() {
        return positions.length;
    }

    public static void handle(QuarryFieldPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.quarrymod.client.ClientQuarryField.accept(packet)));
        context.get().setPacketHandled(true);
    }
}
