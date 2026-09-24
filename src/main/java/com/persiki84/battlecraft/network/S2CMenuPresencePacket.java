package com.persiki84.battlecraft.network;

import com.persiki84.shared.menu.MenuFace;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class S2CMenuPresencePacket {
    public final UUID player;
    public final MenuFace face;

    public S2CMenuPresencePacket(UUID player, MenuFace face) {
        this.player = player;
        this.face = face;
    }

    public static void encode(S2CMenuPresencePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.player);
        MenuFace.write(packet.face, buffer);
    }

    public static S2CMenuPresencePacket decode(FriendlyByteBuf buffer) {
        return new S2CMenuPresencePacket(buffer.readUUID(), MenuFace.read(buffer));
    }

    public static void handle(S2CMenuPresencePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.battlecraft.client.menu.MenuPresenceClient.accept(
                        packet.player, packet.face)));
        ctx.get().setPacketHandled(true);
    }
}
