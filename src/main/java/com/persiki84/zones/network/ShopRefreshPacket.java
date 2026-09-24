package com.persiki84.zones.network;

import com.persiki84.shared.ActionGate;
import com.persiki84.zones.ZonesMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// WHY: каталог отбирается по команде игрока, а вступление в команду не даёт события Forge:
// WHY: без запроса при открытии магазина сменивший команду видел бы прежний ассортимент до перезахода
public class ShopRefreshPacket {
    private static final String GATE_KEY = "zones_shop_refresh";
    private static final int GATE_TICKS = 10;

    public ShopRefreshPacket() {}

    public static void encode(ShopRefreshPacket packet, FriendlyByteBuf buf) {
    }

    public static ShopRefreshPacket decode(FriendlyByteBuf buf) {
        return new ShopRefreshPacket();
    }

    public static void handle(ShopRefreshPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !ActionGate.allow(player, GATE_KEY, GATE_TICKS)) return;
            ZonesMod.syncShopTo(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
