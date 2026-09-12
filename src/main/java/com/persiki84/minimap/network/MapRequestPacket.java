package com.persiki84.minimap.network;

import com.persiki84.minimap.server.ServerMapStorage;
import com.persiki84.shared.ActionGate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MapRequestPacket {
    private static final String GATE = "minimapRequest";
    private static final int GATE_TICKS = 100;

    public static void encode(MapRequestPacket packet, FriendlyByteBuf buf) {
    }

    public static MapRequestPacket decode(FriendlyByteBuf buf) {
        return new MapRequestPacket();
    }

    // WHY: одна заявка это полная карта команды пачками по 50, поэтому частота заявок держится
    // WHY: сервером: без сторожа кнопка в настройках раздаёт мегабайты на каждый клик
    public static void handle(MapRequestPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || !ActionGate.allow(sender, GATE, GATE_TICKS)) return;

            ServerMapStorage.syncFullMap(sender);
        });
        ctx.get().setPacketHandled(true);
    }
}
