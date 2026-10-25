package com.persiki84.capturepoints.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FinalOpenSyncPacket {
    private final boolean forced;

    public FinalOpenSyncPacket(boolean forced) {
        this.forced = forced;
    }

    public static void encode(FinalOpenSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.forced);
    }

    public static FinalOpenSyncPacket decode(FriendlyByteBuf buf) {
        return new FinalOpenSyncPacket(buf.readBoolean());
    }

    public static void handle(FinalOpenSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.capturepoints.client.ClientCaptureData.finalForcedOpen(packet.forced)));
        ctx.get().setPacketHandled(true);
    }
}
