package com.persiki84.zones.shop;

import com.persiki84.sellmod.SellManager;
import com.persiki84.shared.ActionGate;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZonesMod;
import com.persiki84.zones.ZoneLookup;
import com.persiki84.zones.ZoneType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Team;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ShopTransactions {
    private static final int MAX_ITEMS_PER_PURCHASE = 64;
    private static final String DEAL_KEY = "shopDealTick";
    private static final int DEAL_INTERVAL_TICKS = 5;

    private ShopTransactions() {}

    private static void denyModule(ServerPlayer player, ModuleId module) {
        player.sendSystemMessage(Component.translatable("battlecraft.module.disabled",
                Component.translatable(module.label())).copy().withStyle(ChatFormatting.RED));
    }

    public static boolean canShopHere(ServerPlayer player) {
        Zone zone = ZoneLookup.smallestOfTypeAt(ZoneType.SHOP, player.getX(), player.getY(), player.getZ());
        return zone != null;
    }

    private static boolean ready(ServerPlayer player, ModuleId module) {
        if (!ModuleSwitches.allows(module)) {
            denyModule(player, module);
            return false;
        }
        if (!player.isAlive() || !canShopHere(player)) {
            deny(player, "zones.shop.error.not_in_shop");
            return false;
        }
        return ActionGate.allow(player, DEAL_KEY, DEAL_INTERVAL_TICKS);
    }

    public static void purchase(ServerPlayer player, String sectionId, String childId, String entryId, int amount) {
        if (!ready(player, ModuleId.ZONES)) return;

        ShopSection section = ShopCatalog.resolve(sectionId, childId);
        ShopEntry entry = section == null ? null : section.entry(entryId);
        if (entry == null) {
            deny(player, "zones.shop.error.no_entry");
            return;
        }

        if (!allowed(player, ShopCatalog.section(sectionId), section, entry)) {
            deny(player, "zones.shop.error.not_for_team");
            return;
        }

        String pool = ShopViewer.of(player).key(entry.scope());
        if (entry.soldOutIn(pool)) {
            deny(player, "zones.shop.error.sold_out");
            return;
        }

        int balance = countCurrency(player);
        int units = affordableUnits(entry, pool, amount, balance);
        if (units <= 0) {
            deny(player, "zones.shop.error.not_enough", entry.price() - balance);
            return;
        }

        checkout(player, entry, entry.take(pool, units, System.currentTimeMillis()));
    }

    // WHY: клиентский пакет это заявка: ограничение по командам обязано проверяться здесь,
    // WHY: а не тем, что скрытый отдел не доехал до чужого клиента
    private static boolean allowed(ServerPlayer player, ShopSection parent, ShopSection section, ShopEntry entry) {
        Team team = player.getTeam();
        String name = team == null ? null : team.getName();

        if (parent != null && !parent.access().visibleTo(name)) return false;
        if (section != parent && !section.access().visibleTo(name)) return false;
        return entry.access().visibleTo(name);
    }

    private static void checkout(ServerPlayer player, ShopEntry entry, int units) {
        int cost = entry.price() * units;
        takeCurrency(player, cost);
        for (int given = 0; given < units; given++) {
            givePurchase(player, entry.stack().copy());
        }

        announce(player, entry, units, cost);
        if (!entry.limited()) return;

        // WHY: покупка меняет на диске только остаток склада, поэтому у безлимитного товара нечего
        // WHY: писать и нечего рассылать: каталог уходил всем и переписывался файлом на каждый чек
        ShopCatalog.persist();
        ZonesMod.syncShopAfterPurchase(player, entry.scope());
    }

    private static int affordableUnits(ShopEntry entry, String pool, int amount, int balance) {
        int ceiling = Math.max(1, MAX_ITEMS_PER_PURCHASE / entry.bundle());
        int wanted = Math.max(1, Math.min(amount, ceiling));
        if (entry.limited()) wanted = Math.min(wanted, entry.availableIn(pool));
        if (entry.price() <= 0) return wanted;
        return Math.min(wanted, balance / entry.price());
    }

    private static void announce(ServerPlayer player, ShopEntry entry, int units, int cost) {
        int items = units * entry.bundle();
        Component message = items > 1
                ? Component.translatable("zones.shop.bought.many", entry.stack().getHoverName(), items, cost)
                : Component.translatable("zones.shop.bought", entry.stack().getHoverName(), cost);
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.GREEN));
    }

    public static void sell(ServerPlayer player) {
        if (!ready(player, ModuleId.SELL)) return;

        SellManager.SellResult result = SellManager.sellAllItems(player);
        if (result.totalItemsSold == 0) {
            deny(player, "zones.shop.error.nothing_to_sell");
            return;
        }

        player.sendSystemMessage(Component.translatable("zones.shop.sold",
                result.totalItemsSold, result.totalCurrencyEarned).withStyle(ChatFormatting.GREEN));
    }

    private static int countCurrency(ServerPlayer player) {
        Item currency = SellManager.getCurrencyItem();
        int total = count(player.getInventory().items, currency);
        return total + count(player.getInventory().offhand, currency);
    }

    private static int count(List<ItemStack> slots, Item currency) {
        int total = 0;
        for (ItemStack stack : slots) {
            if (stack.is(currency)) total += stack.getCount();
        }
        return total;
    }

    private static void takeCurrency(ServerPlayer player, int amount) {
        Item currency = SellManager.getCurrencyItem();
        int remaining = take(player.getInventory().items, currency, amount);
        take(player.getInventory().offhand, currency, remaining);
    }

    private static int take(List<ItemStack> slots, Item currency, int amount) {
        int remaining = amount;
        for (ItemStack stack : slots) {
            if (remaining <= 0) break;
            if (!stack.is(currency)) continue;

            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
        return remaining;
    }

    private static void givePurchase(ServerPlayer player, ItemStack purchase) {
        if (!player.getInventory().add(purchase)) {
            player.spawnAtLocation(purchase);
        }
    }

    private static void deny(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED));
    }
}
