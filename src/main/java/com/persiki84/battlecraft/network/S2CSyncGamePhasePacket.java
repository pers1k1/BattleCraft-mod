package com.persiki84.battlecraft.network;

import com.persiki84.battlecraft.BattleCraftManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CSyncGamePhasePacket {
    public final BattleCraftManager.GamePhase phase;
    public final boolean softDisabled;
    public final boolean isReady;
    public final int lobbyTimer;
    public final int lobbyMaxTimer;
    public final int graceTicks;
    public final int autoAssignTicks;
    public final int switchCooldownTicks;
    public final int readyCooldownTicks;
    public final int matchTicks;
    public final String missingPlayers;

    public S2CSyncGamePhasePacket(BattleCraftManager.GamePhase phase, boolean softDisabled, boolean isReady,
                                  int lobbyTimer, int lobbyMaxTimer, int graceTicks, int autoAssignTicks,
                                  int switchCooldownTicks, int readyCooldownTicks, int matchTicks,
                                  String missingPlayers) {
        this.phase = phase;
        this.softDisabled = softDisabled;
        this.isReady = isReady;
        this.lobbyTimer = lobbyTimer;
        this.lobbyMaxTimer = lobbyMaxTimer;
        this.graceTicks = graceTicks;
        this.autoAssignTicks = autoAssignTicks;
        this.switchCooldownTicks = switchCooldownTicks;
        this.readyCooldownTicks = readyCooldownTicks;
        this.matchTicks = matchTicks;
        this.missingPlayers = missingPlayers;
    }

    public static void encode(S2CSyncGamePhasePacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.phase);
        buffer.writeBoolean(packet.softDisabled);
        buffer.writeBoolean(packet.isReady);
        buffer.writeVarInt(packet.lobbyTimer);
        buffer.writeVarInt(packet.lobbyMaxTimer);
        buffer.writeVarInt(packet.graceTicks);
        buffer.writeVarInt(packet.autoAssignTicks);
        buffer.writeVarInt(packet.switchCooldownTicks);
        buffer.writeVarInt(packet.readyCooldownTicks);
        buffer.writeVarInt(packet.matchTicks);
        buffer.writeUtf(packet.missingPlayers);
    }

    public static S2CSyncGamePhasePacket decode(FriendlyByteBuf buffer) {
        return new S2CSyncGamePhasePacket(
                buffer.readEnum(BattleCraftManager.GamePhase.class),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf());
    }

    public static void handle(S2CSyncGamePhasePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.battlecraft.client.ClientGameData.updateGamePhase(packet)));
        ctx.get().setPacketHandled(true);
    }
}
