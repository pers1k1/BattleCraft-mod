package com.persiki84.sellmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.persiki84.shared.WorldFiles;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import com.persiki84.shared.economy.CurrencyItems;
import com.persiki84.shared.economy.Wallet;
import com.persiki84.shared.economy.WalletEntryKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

public class SellManager {

    private static final String CONFIG_FILE = "sellmod.json";
    private static final String BROKEN_SUFFIX = ".broken-";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, SellPrice> SELL_PRICES = new HashMap<>();
    private static boolean configUnreadable;

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

    // WHY: выручка идёт через кошелёк, а не стопками напрямую: в режиме «Счёт» она ложится на счёт, и
    // WHY: скупка тогда принимает и саму валюту по номиналу. Счёт не растёт выше предела, поэтому
    // WHY: стопка, которая его превысила бы, остаётся у игрока, а не исчезает за бесценок. В режиме
    // WHY: «Предмет» потолок это место под монеты, и оно растёт с каждой проданной стопкой: иначе
    // WHY: дорогая скупка роняла бы под ноги по сущности на стопку, миллионы за один вызов
    public static SellResult sellAllItems(ServerPlayer player) {
        SellResult result = new SellResult();
        boolean accounts = Wallet.accounts();
        Item currency = Wallet.currencyItem();
        SellBudget budget = accounts ? SellBudget.fixed(Wallet.room(player)) : SellBudget.coins(player, currency);
        Item skipped = accounts || currencyGuard ? currency : null;
        Item deposited = accounts ? currency : null;
        sellFrom(player.getInventory().items, true, skipped, deposited, budget, result);
        sellFrom(player.getInventory().offhand, false, skipped, deposited, budget, result);

        if (result.totalCurrencyEarned > 0) {
            Wallet.credit(player, result.totalCurrencyEarned, WalletEntryKind.SELL, "", result.totalItemsSold);
            player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        return result;
    }

    // WHY: цена задаётся без верхней границы, а сумма считалась в int уже после очистки слота:
    // WHY: полный инвентарь дорогих предметов уходил в минус, вещи пропадали, денег не было
    private static void sellFrom(List<ItemStack> slots, boolean mainGrid, Item skipped, Item deposited,
                                 SellBudget budget, SellResult result) {
        for (int slot = 0; slot < slots.size(); slot++) {
            ItemStack stack = slots.get(slot);
            if (stack.isEmpty()) continue;

            long unit = unitPrice(stack, skipped, deposited);
            if (unit <= 0L) continue;

            int count = stack.getCount();
            long earned = result.totalCurrencyEarned + count * unit;
            long ceiling = budget.ceiling + budget.freedBy(stack, mainGrid);
            if (earned > ceiling) continue;

            budget.ceiling = ceiling;
            String itemName = getItemName(stack);
            slots.set(slot, ItemStack.EMPTY);
            result.totalItemsSold += count;
            result.totalCurrencyEarned = earned;
            result.itemsSold.put(itemName, result.itemsSold.getOrDefault(itemName, 0) + count);
        }
    }

    private static long unitPrice(ItemStack stack, Item skipped, Item deposited) {
        if (deposited != null && stack.is(deposited)) return 1L;
        if (skipped != null && stack.is(skipped)) return 0L;

        SellPrice price = SELL_PRICES.get(getItemName(stack));
        return price == null ? 0L : price.price;
    }

    private static String getItemName(ItemStack stack) {
        return ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
    }

    private static String currencyItemName = "minecraft:emerald";
    private static boolean currencyGuard = true;

    private static class SellConfig {
        public String currencyItem = "minecraft:emerald";
        public boolean currencyGuard = true;
        public Map<String, Integer> customPrices = new HashMap<>();
    }

    public static Item getCurrencyItem() {
        ResourceLocation rl = ResourceLocation.tryParse(currencyItemName);
        if (rl != null && ForgeRegistries.ITEMS.containsKey(rl)) {
            return ForgeRegistries.ITEMS.getValue(rl);
        }
        return Items.EMERALD;
    }

    public static String getCurrencyId() {
        return currencyItemName;
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

    public static boolean currencyGuard() {
        return currencyGuard;
    }

    public static void setCurrencyGuard(boolean value) {
        currencyGuard = value;
        saveConfig();
    }

    public static boolean hasPrice(String itemId) {
        return SELL_PRICES.containsKey(itemId);
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
        Path path = configPath();
        if (!Files.exists(path)) {
            saveConfig();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            apply(new Gson().fromJson(reader, SellConfig.class));
        } catch (Exception e) {
            SellMod.LOGGER.error("Failed to load SellMod config", e);
            configUnreadable = true;
        }
    }

    // WHY: таблица чистилась до разбора, и файл, упавший на середине, оставлял магазин без цен:
    // WHY: цены собираются в новую карту и подменяют старые целиком только после разбора
    private static void apply(SellConfig config) {
        if (config == null) return;

        if (config.currencyItem != null) currencyItemName = config.currencyItem;
        currencyGuard = config.currencyGuard;
        if (config.customPrices == null) return;

        Map<String, SellPrice> parsed = parsePrices(config.customPrices);
        SELL_PRICES.clear();
        SELL_PRICES.putAll(parsed);
    }

    // WHY: null вместо цены ронял чтение NPE, а нулевая и отрицательная цена забирали предметы
    // WHY: при продаже, ничего не давая взамен: такие записи отбрасываются
    private static Map<String, SellPrice> parsePrices(Map<String, Integer> written) {
        Map<String, SellPrice> parsed = new HashMap<>();
        for (Map.Entry<String, Integer> entry : written.entrySet()) {
            Integer price = entry.getValue();
            if (entry.getKey() == null || price == null || price <= 0) continue;

            parsed.put(entry.getKey(), new SellPrice(entry.getKey(), price));
        }
        return parsed;
    }

    // WHY: запись шла прямо в файл, и падение посреди неё оставляло обрубок, который при следующем
    // WHY: старте не читался. Нечитаемый файл не перезаписывается: первая правка откладывает его
    // WHY: в сторону, чтобы цены можно было восстановить руками
    public static void saveConfig() {
        Path path = configPath();
        Path temporary = path.resolveSibling(CONFIG_FILE + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            if (configUnreadable) setAside(path);
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(snapshot(), writer);
            }
            WorldFiles.moveIntoPlace(temporary, path);
        } catch (Exception e) {
            SellMod.LOGGER.error("Failed to save SellMod config", e);
        }
    }

    private static SellConfig snapshot() {
        SellConfig config = new SellConfig();
        config.currencyItem = currencyItemName;
        config.currencyGuard = currencyGuard;
        for (Map.Entry<String, SellPrice> entry : SELL_PRICES.entrySet()) {
            config.customPrices.put(entry.getKey(), entry.getValue().price);
        }
        return config;
    }

    private static void setAside(Path path) throws IOException {
        Path spoiled = path.resolveSibling(CONFIG_FILE + BROKEN_SUFFIX + LocalDateTime.now().format(STAMP));
        if (Files.exists(path)) {
            Files.move(path, spoiled, StandardCopyOption.REPLACE_EXISTING);
            SellMod.LOGGER.warn("Unreadable SellMod config moved to {}", spoiled.getFileName());
        }
        configUnreadable = false;
    }

    private static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
    }

    public static Map<String, SellPrice> getSellPrices() {
        return SELL_PRICES;
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
        for (SellPrice price : SELL_PRICES.values()) {
            Component displayName = getDisplayName(price.itemName);
            message = message.copy().append(Component.translatable("sellmod.prices.entry.amount", displayName,
                    Wallet.amountText(price.price)).withStyle(ChatFormatting.WHITE));
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

    private static final class SellBudget {
        private final Item coin;
        private final int perStack;
        private long ceiling;

        private SellBudget(long ceiling, Item coin, int perStack) {
            this.ceiling = ceiling;
            this.coin = coin;
            this.perStack = perStack;
        }

        static SellBudget fixed(long ceiling) {
            return new SellBudget(ceiling, null, 0);
        }

        static SellBudget coins(ServerPlayer player, Item coin) {
            int perStack = CurrencyItems.perStack(player.getInventory(), coin);
            return new SellBudget(CurrencyItems.room(player, coin), coin, perStack);
        }

        long freedBy(ItemStack stack, boolean mainGrid) {
            return coin == null ? 0L : CurrencyItems.freedBy(stack, coin, perStack, mainGrid);
        }
    }

    public static class SellResult {
        public int totalItemsSold = 0;
        public long totalCurrencyEarned = 0L;
        public Map<String, Integer> itemsSold = new HashMap<>();
    }
}
