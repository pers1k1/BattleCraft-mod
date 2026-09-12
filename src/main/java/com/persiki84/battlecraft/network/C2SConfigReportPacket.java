package com.persiki84.battlecraft.network;

import com.persiki84.battlecraft.rules.ConfigAudit;
import com.persiki84.battlecraft.rules.ConfigManifest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SConfigReportPacket {
    public final long[] hashes;

    public C2SConfigReportPacket(long[] hashes) {
        this.hashes = hashes;
    }

    public static void encode(C2SConfigReportPacket packet, FriendlyByteBuf buffer) {
        int count = Math.min(packet.hashes.length, ConfigManifest.MAX_FILES);
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            buffer.writeLong(packet.hashes[index]);
        }
    }

    // WHY: длину объявляет клиент, поэтому она зажимается до чтения: заявленный размер сам по себе
    // WHY: не должен становиться способом занять поток сервера разбором мусора
    public static C2SConfigReportPacket decode(FriendlyByteBuf buffer) {
        int count = Math.max(0, Math.min(buffer.readVarInt(), ConfigManifest.MAX_FILES));
        long[] hashes = new long[count];
        for (int index = 0; index < count; index++) {
            hashes[index] = buffer.readLong();
        }
        return new C2SConfigReportPacket(hashes);
    }

    public static void handle(C2SConfigReportPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) ConfigAudit.accept(sender, packet.hashes);
        });
        ctx.get().setPacketHandled(true);
    }
}
