package com.persiki84.dmgndctr.network;

import com.persiki84.dmgndctr.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DamagePacket {
    public static final int MAX_HITS = 64;

    public final int[] targets;
    public final double[] xs;
    public final double[] ys;
    public final double[] zs;
    public final float[] amounts;
    public final boolean[] crits;
    private int count;

    public DamagePacket(int capacity) {
        int size = Math.max(0, Math.min(MAX_HITS, capacity));
        targets = new int[size];
        xs = new double[size];
        ys = new double[size];
        zs = new double[size];
        amounts = new float[size];
        crits = new boolean[size];
    }

    public int count() {
        return count;
    }

    public void add(int target, double x, double y, double z, float amount, boolean crit) {
        if (count >= targets.length) return;

        targets[count] = target;
        xs[count] = x;
        ys[count] = y;
        zs[count] = z;
        amounts[count] = amount;
        crits[count] = crit;
        count++;
    }

    public static void encode(DamagePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.count);
        for (int index = 0; index < msg.count; index++) {
            buf.writeVarInt(msg.targets[index]);
            buf.writeDouble(msg.xs[index]);
            buf.writeDouble(msg.ys[index]);
            buf.writeDouble(msg.zs[index]);
            buf.writeFloat(msg.amounts[index]);
            buf.writeBoolean(msg.crits[index]);
        }
    }

    // WHY: число записей приходит от чужой стороны и зажимается, а лишние записи дочитываются
    // WHY: вхолостую: иначе раздутый счётчик выделял бы массивы любого размера
    public static DamagePacket decode(FriendlyByteBuf buf) {
        int declared = buf.readVarInt();
        DamagePacket packet = new DamagePacket(declared);
        for (int index = 0; index < declared; index++) {
            packet.add(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readBoolean());
        }
        return packet;
    }

    public static void handle(DamagePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handle(msg)));
        ctx.get().setPacketHandled(true);
    }
}
