package com.persiki84.battlecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class S2CRadioTalkPacket {
    public static final int SIGNAL_STEPS = 16;

    public final UUID speaker;
    public final boolean friend;
    public final int signal;

    public S2CRadioTalkPacket(UUID speaker, boolean friend, int signal) {
        this.speaker = speaker;
        this.friend = friend;
        this.signal = signal;
    }

    public static void encode(S2CRadioTalkPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.speaker);
        buffer.writeBoolean(packet.friend);
        buffer.writeByte(packet.signal);
    }

    public static S2CRadioTalkPacket decode(FriendlyByteBuf buffer) {
        return new S2CRadioTalkPacket(buffer.readUUID(), buffer.readBoolean(), buffer.readByte());
    }

    public static void handle(S2CRadioTalkPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.battlecraft.client.voice.VoiceTraffic.claim(packet.speaker,
                        packet.friend, Math.max(0, Math.min(SIGNAL_STEPS, packet.signal)) / (float) SIGNAL_STEPS)));
        ctx.get().setPacketHandled(true);
    }
}
