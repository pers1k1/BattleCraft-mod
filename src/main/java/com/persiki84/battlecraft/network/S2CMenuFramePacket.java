package com.persiki84.battlecraft.network;

import com.persiki84.battlecraft.menu.MenuFrames;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class S2CMenuFramePacket {
    public final UUID player;
    public final byte[] image;

    public S2CMenuFramePacket(UUID player, byte[] image) {
        this.player = player;
        this.image = image;
    }

    public static void encode(S2CMenuFramePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.player);
        buffer.writeByteArray(packet.image);
    }

    public static S2CMenuFramePacket decode(FriendlyByteBuf buffer) {
        return new S2CMenuFramePacket(buffer.readUUID(), buffer.readByteArray(MenuFrames.MAX_BYTES));
    }

    public static void handle(S2CMenuFramePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.battlecraft.client.menu.MenuFrameClient.accept(
                        packet.player, packet.image)));
        ctx.get().setPacketHandled(true);
    }
}
