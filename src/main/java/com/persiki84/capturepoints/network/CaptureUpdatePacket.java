package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.client.ClientCaptureData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class CaptureUpdatePacket {
    private final String pointName;
    private final boolean active;
    private final boolean running;
    private final float progress;
    private final boolean decaying;
    private final boolean held;
    private final int armingSeconds;
    private final String attackerTeam;
    private final int attackerCount;
    private final int rivalCount;
    private final List<UUID> members;

    public CaptureUpdatePacket(String pointName, boolean active, boolean running, float progress, boolean decaying,
                               boolean held, int armingSeconds, String attackerTeam, int attackerCount,
                               int rivalCount, List<UUID> members) {
        this.pointName = pointName;
        this.active = active;
        this.running = running;
        this.progress = progress;
        this.decaying = decaying;
        this.held = held;
        this.armingSeconds = armingSeconds;
        this.attackerTeam = attackerTeam;
        this.attackerCount = attackerCount;
        this.rivalCount = rivalCount;
        this.members = members;
    }

    public static void encode(CaptureUpdatePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.pointName);
        buf.writeBoolean(packet.active);
        buf.writeBoolean(packet.running);
        buf.writeFloat(packet.progress);
        buf.writeBoolean(packet.decaying);
        buf.writeBoolean(packet.held);
        buf.writeVarInt(packet.armingSeconds);
        buf.writeUtf(packet.attackerTeam == null ? "" : packet.attackerTeam);
        buf.writeVarInt(packet.attackerCount);
        buf.writeVarInt(packet.rivalCount);
        buf.writeVarInt(packet.members.size());
        for (UUID member : packet.members) {
            buf.writeUUID(member);
        }
    }

    public static CaptureUpdatePacket decode(FriendlyByteBuf buf) {
        String pointName = buf.readUtf();
        boolean active = buf.readBoolean();
        boolean running = buf.readBoolean();
        float progress = buf.readFloat();
        boolean decaying = buf.readBoolean();
        boolean held = buf.readBoolean();
        int armingSeconds = buf.readVarInt();
        String attackerTeam = buf.readUtf();
        int attackerCount = buf.readVarInt();
        int rivalCount = buf.readVarInt();

        int size = buf.readVarInt();
        List<UUID> members = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            members.add(buf.readUUID());
        }
        return new CaptureUpdatePacket(pointName, active, running, progress, decaying, held, armingSeconds,
                attackerTeam.isEmpty() ? null : attackerTeam, attackerCount, rivalCount, members);
    }

    public static void handle(CaptureUpdatePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientCaptureData.updateCapture(packet.pointName, packet.active, packet.running,
                packet.progress, packet.decaying, packet.held, packet.armingSeconds, packet.attackerTeam,
                packet.attackerCount, packet.rivalCount, packet.members));
        ctx.get().setPacketHandled(true);
    }
}
