package com.persiki84.sellmod.network;

import com.persiki84.sellmod.SellManager;
import com.persiki84.shared.economy.CurrencyMode;
import com.persiki84.shared.economy.EconomySettings;
import com.persiki84.shared.economy.Wallets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SellSyncPacket {
    public final String currencyItem;
    public final Map<String, Integer> prices;
    public final CurrencyMode mode;
    public final String name;
    public final ItemStack icon;
    public final long balance;

    public SellSyncPacket(String currencyItem, Map<String, Integer> prices, CurrencyMode mode, String name,
                          ItemStack icon, long balance) {
        this.currencyItem = currencyItem;
        this.prices = prices;
        this.mode = mode;
        this.name = name;
        this.icon = icon;
        this.balance = balance;
    }

    // WHY: свой баланс едет вместе с настройками, а не отдельным пакетом следом: окно магазина и HUD
    // WHY: иначе кадр видели бы новый режим со старым числом
    public static SellSyncPacket current(ServerPlayer player) {
        Map<String, Integer> prices = new HashMap<>();
        for (SellManager.SellPrice price : SellManager.getSellPrices().values()) {
            prices.put(price.itemName, price.price);
        }
        EconomySettings settings = Wallets.settings();
        return new SellSyncPacket(SellManager.getCurrencyId(), prices, settings.mode(), settings.name(),
                settings.icon(), Wallets.open(player).balance());
    }

    public static void encode(SellSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.currencyItem);
        buffer.writeVarInt(packet.prices.size());
        for (Map.Entry<String, Integer> entry : packet.prices.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeVarInt(entry.getValue());
        }
        buffer.writeUtf(packet.mode.id());
        buffer.writeUtf(packet.name, EconomySettings.NAME_LIMIT * 4);
        buffer.writeItem(packet.icon);
        buffer.writeVarLong(packet.balance);
    }

    public static SellSyncPacket decode(FriendlyByteBuf buffer) {
        String currencyItem = buffer.readUtf();
        int size = buffer.readVarInt();
        Map<String, Integer> prices = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String item = buffer.readUtf();
            prices.put(item, buffer.readVarInt());
        }
        CurrencyMode mode = CurrencyMode.byId(buffer.readUtf());
        String name = EconomySettings.cleanName(buffer.readUtf(EconomySettings.NAME_LIMIT * 4));
        ItemStack icon = buffer.readItem();
        long balance = buffer.readVarLong();
        return new SellSyncPacket(currencyItem, prices, mode, name, icon, balance);
    }

    public static void handle(SellSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> com.persiki84.sellmod.client.ClientSellData.sync(packet));
        ctx.get().setPacketHandled(true);
    }
}
