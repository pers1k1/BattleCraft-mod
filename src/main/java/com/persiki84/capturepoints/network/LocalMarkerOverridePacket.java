package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.client.ClientCaptureData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LocalMarkerOverridePacket {
    private final boolean enabled;

    public LocalMarkerOverridePacket(boolean enabled) {
        this.enabled = enabled;
    }

    public static void encode(LocalMarkerOverridePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.enabled);
    }

    public static LocalMarkerOverridePacket decode(FriendlyByteBuf buf) {
        return new LocalMarkerOverridePacket(buf.readBoolean());
    }

    public static void handle(LocalMarkerOverridePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientCaptureData.setLocalMarkersEnabled(packet.enabled);
        });
        ctx.get().setPacketHandled(true);
    }
}
