package com.persiki84.capturepoints.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MatchVictoryPacket {
    public final String team;

    public MatchVictoryPacket(String team) {
        this.team = team;
    }

    public static void encode(MatchVictoryPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.team);
    }

    public static MatchVictoryPacket decode(FriendlyByteBuf buffer) {
        return new MatchVictoryPacket(buffer.readUtf());
    }

    public static void handle(MatchVictoryPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> com.persiki84.capturepoints.client.ObjectiveHud.showVictory(packet.team));
        ctx.get().setPacketHandled(true);
    }
}
