package com.persiki84.battlecraft.network;

import com.persiki84.battlecraft.rules.ClientAudit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class C2SClientReportPacket {
    public static final int MAX_PACKS = 16;
    public static final int MAX_NAME = 64;

    public final List<String> dropped;
    public final List<String> kept;
    public final boolean glintLoose;

    public C2SClientReportPacket(List<String> dropped, List<String> kept, boolean glintLoose) {
        this.dropped = dropped;
        this.kept = kept;
        this.glintLoose = glintLoose;
    }

    public static void encode(C2SClientReportPacket packet, FriendlyByteBuf buffer) {
        writeNames(buffer, packet.dropped);
        writeNames(buffer, packet.kept);
        buffer.writeBoolean(packet.glintLoose);
    }

    public static C2SClientReportPacket decode(FriendlyByteBuf buffer) {
        return new C2SClientReportPacket(readNames(buffer), readNames(buffer), buffer.readBoolean());
    }

    public static void handle(C2SClientReportPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) ClientAudit.accept(sender, packet.dropped, packet.kept, packet.glintLoose);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void writeNames(FriendlyByteBuf buffer, List<String> names) {
        int count = Math.min(names.size(), MAX_PACKS);
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            buffer.writeUtf(names.get(index), MAX_NAME);
        }
    }

    // WHY: список приходит от клиента, поэтому длина зажимается до чтения строк: иначе заявленный
    // WHY: размер сам по себе становится способом занять поток сервера разбором мусора
    private static List<String> readNames(FriendlyByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), MAX_PACKS);
        List<String> names = new ArrayList<>(Math.max(count, 0));
        for (int index = 0; index < count; index++) {
            names.add(buffer.readUtf(MAX_NAME));
        }
        return names;
    }
}
