package com.persiki84.battlecraft.mode;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CModeLobbyPacket {
    public static final int NONE = -1;

    public final int active;
    public final boolean locked;
    public final int mine;
    public final int[] votes;

    public S2CModeLobbyPacket(int active, boolean locked, int mine, int[] votes) {
        this.active = active;
        this.locked = locked;
        this.mine = mine;
        this.votes = votes;
    }

    public static void encode(S2CModeLobbyPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.active);
        buffer.writeBoolean(packet.locked);
        buffer.writeVarInt(packet.mine);
        buffer.writeVarInt(packet.votes.length);
        for (int count : packet.votes) buffer.writeVarInt(count);
    }

    // WHY: число режимов ограничивается до выделения массива: пакет с огромной длиной не роняет клиента
    public static S2CModeLobbyPacket decode(FriendlyByteBuf buffer) {
        int active = buffer.readVarInt();
        boolean locked = buffer.readBoolean();
        int mine = buffer.readVarInt();
        int length = Math.max(0, Math.min(ModeId.values().length, buffer.readVarInt()));
        int[] votes = new int[ModeId.values().length];
        for (int index = 0; index < length; index++) votes[index] = Math.max(0, buffer.readVarInt());
        return new S2CModeLobbyPacket(active, locked, mine, votes);
    }

    public static void handle(S2CModeLobbyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.battlecraft.client.ClientMatchMode.acceptLobby(packet)));
        ctx.get().setPacketHandled(true);
    }
}
