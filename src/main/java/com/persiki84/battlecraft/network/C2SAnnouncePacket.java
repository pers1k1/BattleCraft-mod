package com.persiki84.battlecraft.network;

import com.persiki84.battlecraft.announce.AnnounceStyle;
import com.persiki84.battlecraft.announce.Announcements;
import com.persiki84.shared.ActionGate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class C2SAnnouncePacket {
    private static final String GATE_KEY = "battlecraft_announce";
    private static final int GATE_TICKS = 20;
    private static final int MAX_TARGETS = 64;
    private static final int MAX_NAME = 32;

    public final boolean everyone;
    public final List<String> teams;
    public final List<String> names;
    public final String text;
    public final AnnounceStyle style;
    public final int seconds;

    public C2SAnnouncePacket(boolean everyone, List<String> teams, List<String> names, String text,
                             AnnounceStyle style, int seconds) {
        this.everyone = everyone;
        this.teams = teams;
        this.names = names;
        this.text = text;
        this.style = style;
        this.seconds = seconds;
    }

    public static void encode(C2SAnnouncePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.everyone);
        writeList(buffer, packet.teams);
        writeList(buffer, packet.names);
        buffer.writeUtf(packet.text, Announcements.MAX_LENGTH);
        buffer.writeEnum(packet.style);
        buffer.writeVarInt(packet.seconds);
    }

    public static C2SAnnouncePacket decode(FriendlyByteBuf buffer) {
        boolean everyone = buffer.readBoolean();
        List<String> teams = readList(buffer);
        List<String> names = readList(buffer);
        return new C2SAnnouncePacket(everyone, teams, names,
                buffer.readUtf(Announcements.MAX_LENGTH), buffer.readEnum(AnnounceStyle.class),
                buffer.readVarInt());
    }

    private static void writeList(FriendlyByteBuf buffer, List<String> values) {
        int size = Math.min(MAX_TARGETS, values.size());
        buffer.writeVarInt(size);
        for (int index = 0; index < size; index++) {
            buffer.writeUtf(values.get(index), MAX_NAME);
        }
    }

    private static List<String> readList(FriendlyByteBuf buffer) {
        int size = Math.min(MAX_TARGETS, buffer.readVarInt());
        List<String> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            values.add(buffer.readUtf(MAX_NAME));
        }
        return values;
    }

    // WHY: заявка клиента, поэтому права, частота и длина проверяются здесь заново: экран мог
    // WHY: устареть, а вручную собранный пакет не проверяется вовсе
    public static void handle(C2SAnnouncePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || !sender.hasPermissions(2)) return;
            if (!ActionGate.allow(sender, GATE_KEY, GATE_TICKS)) return;

            deliver(sender, packet);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void deliver(ServerPlayer sender, C2SAnnouncePacket packet) {
        MinecraftServer server = sender.server;
        int reached = Announcements.deliver(
                Announcements.resolve(server, packet.everyone, packet.teams, packet.names),
                packet.text, packet.style, packet.seconds);
        sender.sendSystemMessage(Announcements.receipt(reached));
    }
}
