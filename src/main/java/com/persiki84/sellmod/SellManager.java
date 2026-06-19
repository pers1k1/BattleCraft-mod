package com.persiki84.sellmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

public class SellManager {

    private static final Map<String, SellPrice> SELL_PRICES = new HashMap<>();

    public static void init() {
        SELL_PRICES.put("minecraft:coal", new SellPrice("minecraft:coal", 1));
        SELL_PRICES.put("minecraft:iron_ingot", new SellPrice("minecraft:iron_ingot", 3));
        SELL_PRICES.put("minecraft:gold_ingot", new SellPrice("minecraft:gold_ingot", 5));
        SELL_PRICES.put("minecraft:diamond", new SellPrice("minecraft:diamond", 8));
        SELL_PRICES.put("minecraft:iron_ore", new SellPrice("minecraft:iron_ore", 3));
        SELL_PRICES.put("minecraft:raw_iron", new SellPrice("minecraft:raw_iron", 3));
        SELL_PRICES.put("minecraft:gold_ore", new SellPrice("minecraft:gold_ore", 5));
        SELL_PRICES.put("minecraft:raw_gold", new SellPrice("minecraft:raw_gold", 5));
        SELL_PRICES.put("minecraft:diamond_ore", new SellPrice("minecraft:diamond_ore", 8));
        SELL_PRICES.put("minecraft:deepslate_coal_ore", new SellPrice("minecraft:deepslate_coal_ore", 1));
        SELL_PRICES.put("minecraft:deepslate_iron_ore", new SellPrice("minecraft:deepslate_iron_ore", 3));
        SELL_PRICES.put("minecraft:deepslate_gold_ore", new SellPrice("minecraft:deepslate_gold_ore", 5));
        SELL_PRICES.put("minecraft:deepslate_diamond_ore", new SellPrice("minecraft:deepslate_diamond_ore", 8));
    }

    public static SellResult sellAllItems(Player player) {
        SellResult result = new SellResult();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            String itemName = getItemName(stack);
            SellPrice price = SELL_PRICES.get(itemName);
            if (price == null) continue;
            int count = stack.getCount();
            int currencyEarned = count * price.price;
            player.getInventory().setItem(i, ItemStack.EMPTY);
            result.totalItemsSold += count;
            result.totalCurrencyEarned += currencyEarned;
            result.itemsSold.put(itemName, result.itemsSold.getOrDefault(itemName, 0) + count);
        }
        if (result.totalCurrencyEarned > 0) {
            giveCurrencyToPlayer(player, result.totalCurrencyEarned);
        }
        return result;
    }

    private static String getItemName(ItemStack stack) {
        return ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
    }

    private static String currencyItemName = "minecraft:emerald";

    private static class SellConfig {
        public String currencyItem = "minecraft:emerald";
        public Map<String, Integer> customPrices = new HashMap<>();
    }

    public static Item getCurrencyItem() {
        ResourceLocation rl = ResourceLocation.tryParse(currencyItemName);
        if (rl != null && ForgeRegistries.ITEMS.containsKey(rl)) {
            return ForgeRegistries.ITEMS.getValue(rl);
        }
        return Items.EMERALD;
    }

    public static boolean setCurrency(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl != null && ForgeRegistries.ITEMS.containsKey(rl)) {
            currencyItemName = itemId;
            saveConfig();
            return true;
        }
        return false;
    }

    public static void setPrice(String itemId, int price) {
        if (price <= 0) {
            SELL_PRICES.remove(itemId);
        } else {
            SELL_PRICES.put(itemId, new SellPrice(itemId, price));
        }
        saveConfig();
    }

    public static void loadConfig() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("sellmod.json");
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                SellConfig config = new Gson().fromJson(reader, SellConfig.class);
                if (config != null) {
                    currencyItemName = config.currencyItem;
                    if (config.customPrices != null) {
                        SELL_PRICES.clear();
                        for (Map.Entry<String, Integer> entry : config.customPrices.entrySet()) {
                            SELL_PRICES.put(entry.getKey(), new SellPrice(entry.getKey(), entry.getValue()));
                        }
                    }
                }
            } catch (Exception e) {
                SellMod.LOGGER.error("Failed to load SellMod config", e);
            }
        } else {
            saveConfig();
        }
    }

    public static void saveConfig() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("sellmod.json");
        try {
            Files.createDirectories(path.getParent());
            SellConfig config = new SellConfig();
            config.currencyItem = currencyItemName;
            for (Map.Entry<String, SellPrice> entry : SELL_PRICES.entrySet()) {
                config.customPrices.put(entry.getKey(), entry.getValue().price);
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(config, writer);
            }
        } catch (Exception e) {
            SellMod.LOGGER.error("Failed to save SellMod config", e);
        }
    }

    private static void giveCurrencyToPlayer(Player player, int amount) {
        Item item = getCurrencyItem();
        ItemStack currencyStack = new ItemStack(item, amount);
        if (!player.getInventory().add(currencyStack)) {
            player.spawnAtLocation(currencyStack);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    public static Map<String, SellPrice> getSellPrices() {
        return SELL_PRICES;
    }

    public static Component createSellMessage(SellResult result) {
        if (result.totalItemsSold == 0) {
            return Component.translatable("sellmod.sell.no_items").withStyle(ChatFormatting.RED);
        }
        Component currencyName = getCurrencyItem().getDescription();
        MutableComponent message = Component.translatable("sellmod.sell.success").withStyle(ChatFormatting.GREEN)
                .append(Component.translatable("sellmod.sell.items_sold", result.totalItemsSold).withStyle(ChatFormatting.YELLOW))
                .append(Component.translatable("sellmod.sell.money_earned", result.totalCurrencyEarned, currencyName).withStyle(ChatFormatting.GREEN));
        for (Map.Entry<String, Integer> entry : result.itemsSold.entrySet()) {
            Component displayName = getDisplayName(entry.getKey());
            message = message.copy()
                    .append(Component.translatable("sellmod.sell.item_detail", displayName, entry.getValue()).withStyle(ChatFormatting.GRAY));
        }
        return message;
    }

    private static Component getDisplayName(String itemName) {
        ResourceLocation rl = ResourceLocation.tryParse(itemName);
        if (rl != null && ForgeRegistries.ITEMS.containsKey(rl)) {
            return ForgeRegistries.ITEMS.getValue(rl).getDescription();
        }
        return Component.literal(itemName);
    }

    public static Component getPriceList() {
        MutableComponent message = Component.translatable("sellmod.prices.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component currencyName = getCurrencyItem().getDescription();
        for (SellPrice price : SELL_PRICES.values()) {
            Component displayName = getDisplayName(price.itemName);
            message = message.copy()
                    .append(Component.translatable("sellmod.prices.entry", displayName, price.price, currencyName).withStyle(ChatFormatting.WHITE));
        }
        return message;
    }

    public static class SellPrice {
        public String itemName;
        public int price;

        public SellPrice(String itemName, int price) {
            this.itemName = itemName;
            this.price = price;
        }
    }

    public static class SellResult {
        public int totalItemsSold = 0;
        public int totalCurrencyEarned = 0;
        public Map<String, Integer> itemsSold = new HashMap<>();
    }
}
