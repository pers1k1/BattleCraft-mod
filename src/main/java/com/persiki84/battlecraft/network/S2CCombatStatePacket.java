package com.persiki84.battlecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CCombatStatePacket {
    public final boolean engaged;

    public S2CCombatStatePacket(boolean engaged) {
        this.engaged = engaged;
    }

    public static void encode(S2CCombatStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.engaged);
    }

    public static S2CCombatStatePacket decode(FriendlyByteBuf buffer) {
        return new S2CCombatStatePacket(buffer.readBoolean());
    }

    public static void handle(S2CCombatStatePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.battlecraft.client.ClientCombatState.accept(packet.engaged)));
        ctx.get().setPacketHandled(true);
    }
}
