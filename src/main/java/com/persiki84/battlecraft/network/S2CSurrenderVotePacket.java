package com.persiki84.battlecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CSurrenderVotePacket {
    public final boolean active;
    public final String team;
    public final int yesCount;
    public final int required;
    public final int remainingTicks;

    public S2CSurrenderVotePacket(boolean active, String team, int yesCount, int required, int remainingTicks) {
        this.active = active;
        this.team = team;
        this.yesCount = yesCount;
        this.required = required;
        this.remainingTicks = remainingTicks;
    }

    public static void encode(S2CSurrenderVotePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.active);
        if (!packet.active) return;

        buffer.writeUtf(packet.team);
        buffer.writeVarInt(packet.yesCount);
        buffer.writeVarInt(packet.required);
        buffer.writeVarInt(packet.remainingTicks);
    }

    public static S2CSurrenderVotePacket decode(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) return new S2CSurrenderVotePacket(false, "", 0, 0, 0);

        return new S2CSurrenderVotePacket(true, buffer.readUtf(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(S2CSurrenderVotePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.battlecraft.client.ClientGameData.updateVote(packet)));
        ctx.get().setPacketHandled(true);
    }
}
