package com.persiki84.dmgndctr.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

import com.persiki84.dmgndctr.client.ClientPacketHandler;

public class DamagePacket {
    public final float damage;
    public final double x, y, z;
    public final boolean isCrit;

    public DamagePacket(float damage, double x, double y, double z, boolean isCrit) {
        this.damage = damage;
        this.x = x; this.y = y; this.z = z;
        this.isCrit = isCrit;
    }

    public static void encode(DamagePacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.damage);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeBoolean(msg.isCrit);
    }

    public static DamagePacket decode(FriendlyByteBuf buf) {
        return new DamagePacket(buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
    }

    public static void handle(DamagePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handle(msg));
        });
        ctx.get().setPacketHandled(true);
    }
}
