package com.persiki84.battlecraft.network;

import com.persiki84.battlecraft.rules.ConfigAudit;
import com.persiki84.battlecraft.rules.ConfigManifest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class C2SConfigSnapshotPacket {
    public static final int MAX_BATCH = 64;

    public final List<String> paths;
    public final long[] hashes;
    public final boolean last;

    public C2SConfigSnapshotPacket(List<String> paths, long[] hashes, boolean last) {
        this.paths = paths;
        this.hashes = hashes;
        this.last = last;
    }

    public static void encode(C2SConfigSnapshotPacket packet, FriendlyByteBuf buffer) {
        int count = Math.min(Math.min(packet.paths.size(), packet.hashes.length), MAX_BATCH);
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            buffer.writeUtf(packet.paths.get(index), ConfigManifest.MAX_PATH);
            buffer.writeLong(packet.hashes[index]);
        }
        buffer.writeBoolean(packet.last);
    }

    // WHY: пачка приходит от клиента, поэтому и число записей, и длина каждой строки зажимаются
    // WHY: до чтения: ванильный C2S ограничен 32767 байтами, и разбор обязан укладываться в него
    public static C2SConfigSnapshotPacket decode(FriendlyByteBuf buffer) {
        int count = Math.max(0, Math.min(buffer.readVarInt(), MAX_BATCH));
        List<String> paths = new ArrayList<>(count);
        long[] hashes = new long[count];
        for (int index = 0; index < count; index++) {
            paths.add(buffer.readUtf(ConfigManifest.MAX_PATH));
            hashes[index] = buffer.readLong();
        }
        return new C2SConfigSnapshotPacket(paths, hashes, buffer.readBoolean());
    }

    public static void handle(C2SConfigSnapshotPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) ConfigAudit.acceptSnapshot(sender, packet.paths, packet.hashes, packet.last);
        });
        ctx.get().setPacketHandled(true);
    }
}
