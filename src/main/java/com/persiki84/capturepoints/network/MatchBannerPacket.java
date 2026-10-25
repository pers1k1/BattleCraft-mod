package com.persiki84.capturepoints.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// WHY: причина и итоговый счёт приходят готовыми компонентами: к приходу баннера клиент уже сброшен
// WHY: в лобби и своих цифр матча не держит, а перевод ключей всё равно делается на клиенте
public class MatchBannerPacket {
    public enum Kind { VICTORY, DRAW, STOPPED, SUDDEN }

    public final Kind kind;
    public final String team;
    public final Component detail;
    public final Component score;

    public MatchBannerPacket(Kind kind, String team, Component detail, Component score) {
        this.kind = kind;
        this.team = team == null ? "" : team;
        this.detail = detail;
        this.score = score;
    }

    public static void encode(MatchBannerPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.kind);
        buffer.writeUtf(packet.team);
        buffer.writeComponent(packet.detail);
        buffer.writeComponent(packet.score);
    }

    public static MatchBannerPacket decode(FriendlyByteBuf buffer) {
        return new MatchBannerPacket(buffer.readEnum(Kind.class), buffer.readUtf(), buffer.readComponent(),
                buffer.readComponent());
    }

    public static void handle(MatchBannerPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.capturepoints.client.MatchBanner.accept(packet)));
        ctx.get().setPacketHandled(true);
    }
}
