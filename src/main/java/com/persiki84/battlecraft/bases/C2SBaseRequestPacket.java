package com.persiki84.battlecraft.bases;

import com.persiki84.shared.ActionGate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// WHY: экран смерти и окно перелёта шлют заявку своим пакетом, а не командой: сервер отбрасывает
// WHY: команды игрока со скрытым чатом, и выбор базы молча пропадал. Пакет и команда идут одним путём,
// WHY: и в очередь сервера пакет встаёт раньше ванильного возрождения, отправленного следом
public final class C2SBaseRequestPacket {
    private static final String GATE_KEY = "battlecraft_base_request";
    private static final int GATE_TICKS = 5;
    private static final int ID_LIMIT = 160;

    public enum Kind { SPAWN, TRAVEL }

    public final Kind kind;
    public final String id;

    public C2SBaseRequestPacket(Kind kind, String id) {
        this.kind = kind;
        this.id = id;
    }

    public static void encode(C2SBaseRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.kind);
        buffer.writeUtf(packet.id, ID_LIMIT);
    }

    public static C2SBaseRequestPacket decode(FriendlyByteBuf buffer) {
        return new C2SBaseRequestPacket(buffer.readEnum(Kind.class), buffer.readUtf(ID_LIMIT));
    }

    public static void handle(C2SBaseRequestPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        context.get().enqueueWork(() -> {
            if (player == null || !ActionGate.allow(player, GATE_KEY, GATE_TICKS)) return;
            Component refusal = packet.kind == Kind.SPAWN ? RespawnChoices.choose(player, packet.id)
                    : BaseTravel.start(player, packet.id);
            if (refusal != null) BaseTravel.tell(player, refusal.copy().withStyle(ChatFormatting.RED));
        });
        context.get().setPacketHandled(true);
    }
}
