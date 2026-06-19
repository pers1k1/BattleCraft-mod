package com.persiki84.battlecraft.network;

import com.persiki84.battlecraft.BattleCraftManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CSyncGamePhasePacket {
    public final BattleCraftManager.GamePhase phase;
    public final boolean hasActiveVote;
    public final String voteTeam;
    public final int yesCount;
    public final int totalRequired;
    public final long endTime;
    public final boolean softDisabled;
    public final int lobbyTimer;
    public final int lobbyMaxTimer;
    public final String missingPlayers;
    public final boolean isReady;

    public S2CSyncGamePhasePacket(BattleCraftManager.GamePhase phase, boolean hasActiveVote, String voteTeam, int yesCount, int totalRequired, long endTime, boolean softDisabled, int lobbyTimer, int lobbyMaxTimer, String missingPlayers, boolean isReady) {
        this.phase = phase;
        this.hasActiveVote = hasActiveVote;
        this.voteTeam = voteTeam;
        this.yesCount = yesCount;
        this.totalRequired = totalRequired;
        this.endTime = endTime;
        this.softDisabled = softDisabled;
        this.lobbyTimer = lobbyTimer;
        this.lobbyMaxTimer = lobbyMaxTimer;
        this.missingPlayers = missingPlayers;
        this.isReady = isReady;
    }

    public static void encode(S2CSyncGamePhasePacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.phase);
        buffer.writeBoolean(packet.hasActiveVote);
        if (packet.hasActiveVote) {
            buffer.writeUtf(packet.voteTeam);
            buffer.writeInt(packet.yesCount);
            buffer.writeInt(packet.totalRequired);
            buffer.writeLong(packet.endTime);
        }
        buffer.writeBoolean(packet.softDisabled);
        buffer.writeInt(packet.lobbyTimer);
        buffer.writeInt(packet.lobbyMaxTimer);
        buffer.writeUtf(packet.missingPlayers);
        buffer.writeBoolean(packet.isReady);
    }

    public static S2CSyncGamePhasePacket decode(FriendlyByteBuf buffer) {
        BattleCraftManager.GamePhase phase = buffer.readEnum(BattleCraftManager.GamePhase.class);
        boolean hasActiveVote = buffer.readBoolean();
        String voteTeam = "";
        int yesCount = 0;
        int totalRequired = 0;
        long endTime = 0;
        if (hasActiveVote) {
            voteTeam = buffer.readUtf();
            yesCount = buffer.readInt();
            totalRequired = buffer.readInt();
            endTime = buffer.readLong();
        }
        boolean softDisabled = buffer.readBoolean();
        int lobbyTimer = buffer.readInt();
        int lobbyMaxTimer = buffer.readInt();
        String missingPlayers = buffer.readUtf();
        boolean isReady = buffer.readBoolean();
        return new S2CSyncGamePhasePacket(phase, hasActiveVote, voteTeam, yesCount, totalRequired, endTime, softDisabled, lobbyTimer, lobbyMaxTimer, missingPlayers, isReady);
    }

    public static void handle(S2CSyncGamePhasePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null) {
                com.persiki84.battlecraft.client.ClientGameData.updateGamePhase(packet);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
