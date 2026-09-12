package com.persiki84.battlecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CConfigScanPacket {

    public S2CConfigScanPacket() {}

    public static void encode(S2CConfigScanPacket packet, FriendlyByteBuf buffer) {}

    public static S2CConfigScanPacket decode(FriendlyByteBuf buffer) {
        return new S2CConfigScanPacket();
    }

    public static void handle(S2CConfigScanPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.battlecraft.client.ConfigGuard.scan()));
        ctx.get().setPacketHandled(true);
    }
}
