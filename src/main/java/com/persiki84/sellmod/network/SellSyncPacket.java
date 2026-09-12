package com.persiki84.sellmod.network;

import com.persiki84.sellmod.SellManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SellSyncPacket {
    public final String currencyItem;
    public final Map<String, Integer> prices;

    public SellSyncPacket(String currencyItem, Map<String, Integer> prices) {
        this.currencyItem = currencyItem;
        this.prices = prices;
    }

    public static SellSyncPacket current() {
        Map<String, Integer> prices = new HashMap<>();
        for (SellManager.SellPrice price : SellManager.getSellPrices().values()) {
            prices.put(price.itemName, price.price);
        }
        return new SellSyncPacket(SellManager.getCurrencyId(), prices);
    }

    public static void encode(SellSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.currencyItem);
        buffer.writeVarInt(packet.prices.size());
        for (Map.Entry<String, Integer> entry : packet.prices.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeVarInt(entry.getValue());
        }
    }

    public static SellSyncPacket decode(FriendlyByteBuf buffer) {
        String currencyItem = buffer.readUtf();
        int size = buffer.readVarInt();
        Map<String, Integer> prices = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String item = buffer.readUtf();
            prices.put(item, buffer.readVarInt());
        }
        return new SellSyncPacket(currencyItem, prices);
    }

    public static void handle(SellSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> com.persiki84.sellmod.client.ClientSellData.sync(packet));
        ctx.get().setPacketHandled(true);
    }
}
