package com.persiki84.battlecraft.network;

import com.persiki84.battlecraft.announce.AnnounceStyle;
import com.persiki84.battlecraft.announce.Announcements;
import com.persiki84.battlecraft.client.hud.AnnounceHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CAnnouncePacket {
    public final String text;
    public final AnnounceStyle style;
    public final int seconds;

    public S2CAnnouncePacket(String text, AnnounceStyle style, int seconds) {
        this.text = text;
        this.style = style;
        this.seconds = seconds;
    }

    public static void encode(S2CAnnouncePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.text, Announcements.MAX_LENGTH);
        buffer.writeEnum(packet.style);
        buffer.writeVarInt(packet.seconds);
    }

    public static S2CAnnouncePacket decode(FriendlyByteBuf buffer) {
        return new S2CAnnouncePacket(buffer.readUtf(Announcements.MAX_LENGTH),
                buffer.readEnum(AnnounceStyle.class), buffer.readVarInt());
    }

    public static void handle(S2CAnnouncePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AnnounceHud.show(packet.text, packet.style, packet.seconds)));
        ctx.get().setPacketHandled(true);
    }
}
