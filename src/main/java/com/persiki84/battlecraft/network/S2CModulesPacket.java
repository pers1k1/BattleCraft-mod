package com.persiki84.battlecraft.network;

import com.persiki84.battlecraft.modules.ModuleSwitches;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CModulesPacket {
    public final int enabled;

    public S2CModulesPacket(int enabled) {
        this.enabled = enabled;
    }

    public static S2CModulesPacket current() {
        return new S2CModulesPacket(ModuleSwitches.mask());
    }

    public static void encode(S2CModulesPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.enabled);
    }

    public static S2CModulesPacket decode(FriendlyByteBuf buffer) {
        return new S2CModulesPacket(buffer.readVarInt());
    }

    public static void handle(S2CModulesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.battlecraft.client.ClientModules.accept(packet.enabled)));
        ctx.get().setPacketHandled(true);
    }
}
