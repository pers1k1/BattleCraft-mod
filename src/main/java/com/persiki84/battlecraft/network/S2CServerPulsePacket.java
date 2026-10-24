package com.persiki84.battlecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CServerPulsePacket {
    public final float ticksPerSecond;
    public final float millisPerTick;

    public S2CServerPulsePacket(float ticksPerSecond, float millisPerTick) {
        this.ticksPerSecond = ticksPerSecond;
        this.millisPerTick = millisPerTick;
    }

    public static void encode(S2CServerPulsePacket packet, FriendlyByteBuf buffer) {
        buffer.writeFloat(packet.ticksPerSecond);
        buffer.writeFloat(packet.millisPerTick);
    }

    public static S2CServerPulsePacket decode(FriendlyByteBuf buffer) {
        return new S2CServerPulsePacket(buffer.readFloat(), buffer.readFloat());
    }

    public static void handle(S2CServerPulsePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.battlecraft.client.roster.ServerPulseClient.accept(
                        packet.ticksPerSecond, packet.millisPerTick)));
        ctx.get().setPacketHandled(true);
    }
}
