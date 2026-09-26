package com.persiki84.shared.menu;

import com.persiki84.shared.ActionGate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MenuRequestPacket {
    private static final int MAX_ID_LENGTH = 64;
    private static final String GATE_PREFIX = "menu:";
    private static final int GATE_TICKS = 3;

    private final String menuId;

    public MenuRequestPacket(String menuId) {
        this.menuId = menuId;
    }

    public static void encode(MenuRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.menuId, MAX_ID_LENGTH);
    }

    public static MenuRequestPacket decode(FriendlyByteBuf buf) {
        return new MenuRequestPacket(buf.readUtf(MAX_ID_LENGTH));
    }

    public static void handle(MenuRequestPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> answer(packet.menuId, context.get().getSender()));
        context.get().setPacketHandled(true);
    }

    // WHY: ключ сторожа ложится в данные игрока и уходит на диск, поэтому он заводится только под
    // WHY: зарегистрированное меню: иначе поток выдуманных id раздувал бы файл игрока без предела.
    // WHY: Клиент спрашивает раз в 200 мс, то есть раз в 4 тика, и дрожание сети сжимает промежуток
    // WHY: до 3: сторож на 4 резал бы каждый второй честный опрос
    private static void answer(String menuId, ServerPlayer player) {
        if (player == null || !MenuStates.registered(menuId)) return;
        if (!ActionGate.allow(player, GATE_PREFIX + menuId, GATE_TICKS)) return;

        CompoundTag state = MenuStates.snapshot(menuId, player);
        if (state == null) return;

        MenuNetwork.sendState(player, menuId, state, false);
    }
}
