package com.persiki84.battlecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CGogglesPacket {
    public final boolean active;
    public final int mode;
    public final float battery;
    public final boolean zoom;

    public S2CGogglesPacket(boolean active, int mode, float battery, boolean zoom) {
        this.active = active;
        this.mode = mode;
        this.battery = battery;
        this.zoom = zoom;
    }

    public static void encode(S2CGogglesPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.active);
        buffer.writeVarInt(packet.mode);
        buffer.writeFloat(packet.battery);
        buffer.writeBoolean(packet.zoom);
    }

    public static S2CGogglesPacket decode(FriendlyByteBuf buffer) {
        return new S2CGogglesPacket(buffer.readBoolean(), buffer.readVarInt(),
                buffer.readFloat(), buffer.readBoolean());
    }

    public static void handle(S2CGogglesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.battlecraft.client.goggles.GogglesClient.accept(
                        packet.active, packet.mode, packet.battery, packet.zoom)));
        ctx.get().setPacketHandled(true);
    }
}
