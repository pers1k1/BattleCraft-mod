package com.persiki84.battlecraft.menu;

import com.persiki84.airdrop.config.AirDropConfig;
import com.persiki84.airdrop.loot.AirDropLootManager;
import com.persiki84.combattimer.CombatTimerMod;
import com.persiki84.immortality.event.ImmortalityHandler;
import com.persiki84.itemmodifiers.ModifierConfig;
import com.persiki84.killreward.KillRewardMod;
import com.persiki84.knockdown.config.KnockdownConfig;
import com.persiki84.quarrymod.QuarryMod;
import com.persiki84.quarrymod.data.QuarryBlock;
import com.persiki84.quarrymod.data.QuarryBlockManager;
import com.persiki84.quarrymod.data.QuarryBlockRule;
import com.persiki84.sellmod.SellManager;
import com.persiki84.battlecraft.rules.GameRule;
import com.persiki84.battlecraft.rules.GameRules;
import com.persiki84.shared.gunsmith.GunSmith;
import com.persiki84.shared.menu.MenuStates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModuleMenuStates {
    public static final String AIRDROP = "airdrop";
    public static final String QUARRY = "quarry";
    public static final String KILL_REWARD = "killreward";
    public static final String IMMORTALITY = "immortality";
    public static final String KNOCKDOWN = "knockdown";
    public static final String COMBAT = "combattimer";
    public static final String SELL = "sellmod";
    public static final String MODIFIERS = "itemmodifiers";
    public static final String SHOP = "shop";
    public static final String GAME_RULES = "gamerules";
    public static final String TEAMS = "teams";
    public static final String MAP = "map";

    private static final ResourceLocation GLOBAL_TABLE = new ResourceLocation("airdrop", "global");
    public static final String TRACKED_ITEMS = "trackedItems";
    public static final String QUARRY_RULES = "quarryRules";
    public static final String QUARRY_BLOCKS = "quarryBlocks";
    public static final String IMMORTAL_PLAYERS = "immortalPlayers";

    private static final int PERCENT = 100;
    private static final int TICKS_PER_SECOND = 20;
    private static final int LISTED_BLOCKS = 200;

    private ModuleMenuStates() {}

    public static void register() {
        MenuStates.register(AIRDROP, 2, player -> airdrop());
        MenuStates.register(QUARRY, 2, player -> quarry());
        MenuStates.register(KILL_REWARD, 2, player -> killReward());
        MenuStates.register(IMMORTALITY, 2, ModuleMenuStates::immortality);
        MenuStates.register(KNOCKDOWN, 2, player -> knockdown());
        MenuStates.register(COMBAT, 2, player -> combat());
        MenuStates.register(SELL, 2, ModuleMenuStates::sell);
        MenuStates.register(MODIFIERS, 2, player -> modifiers());
        MenuStates.register(SHOP, 2, player -> new CompoundTag());
        MenuStates.register(GAME_RULES, 2, player -> gameRules());
        MenuStates.register(TEAMS, 2, ModuleMenuStates::teams);
        MenuStates.register(MAP, 2, ModuleMenuStates::map);
    }

    private static CompoundTag airdrop() {
        AirDropConfig.Server config = AirDropConfig.SERVER;
        CompoundTag tag = new CompoundTag();

        tag.putBoolean("modEnabled", config.modEnabled.get());
        tag.putBoolean("autoSpawnEnabled", config.autoSpawnEnabled.get());
        tag.putBoolean("centerAtWorldSpawn", config.centerAtWorldSpawn.get());
        tag.putInt("centerX", (int) Math.round(config.centerX.get()));
        tag.putInt("centerZ", (int) Math.round(config.centerZ.get()));
        tag.putInt("spawnRadius", config.spawnRadius.get());
        tag.putInt("intervalSeconds", config.intervalSeconds.get());
        tag.putInt("chancePercent", (int) Math.round(config.intervalSpawnChance.get() * PERCENT));
        tag.putInt("flightSeconds", config.flyingAnimTicks.get() / TICKS_PER_SECOND);
        tag.putInt("openDelaySeconds", config.autoOpenDelayTicks.get() / TICKS_PER_SECOND);
        tag.putInt("despawnEmptySeconds", config.despawnEmptySeconds.get());
        tag.putInt("despawnFilledSeconds", config.despawnFilledSeconds.get());
        tag.putInt("warnSeconds", config.notificationSecondsBeforeDespawn.get());
        tag.put("loot", lootEntries());
        return tag;
    }

    private static ListTag lootEntries() {
        ListTag list = new ListTag();
        int index = 0;
        for (AirDropLootManager.LootEntry entry : AirDropLootManager.getTable(GLOBAL_TABLE)) {
            list.add(lootEntry(entry, index++));
        }
        return list;
    }

    private static CompoundTag lootEntry(AirDropLootManager.LootEntry entry, int index) {
        CompoundTag stored = new CompoundTag();
        stored.putInt("index", index);
        stored.putString("item", entry.itemId().toString());
        stored.putInt("min", entry.min());
        stored.putInt("max", entry.max());
        stored.putInt("chance", Math.round(entry.chance() * PERCENT));

        ItemStack stack = AirDropLootManager.stackOf(entry, 1);
        stored.putBoolean("gun", GunSmith.isGun(stack));
        stored.put("stack", stack.save(new CompoundTag()));
        return stored;
    }

    private static CompoundTag teams(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        MinecraftServer server = player.getServer();
        if (server == null) return tag;

        ListTag list = new ListTag();
        for (PlayerTeam team : server.getScoreboard().getPlayerTeams()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("name", team.getName());
            entry.putInt("players", team.getPlayers().size());
            list.add(entry);
        }
        tag.put("teams", list);
        return tag;
    }

    private static CompoundTag map(ServerPlayer player) {
        CompoundTag tag = teams(player);
        tag.putInt("shared", com.persiki84.minimap.server.ServerMapStorage
                .sharedSize(com.persiki84.minimap.server.ServerMapStorage.dimensionKey(player.serverLevel())));
        tag.putInt("scanLeft", com.persiki84.minimap.server.MapScanner.remaining(player));
        tag.putInt("scanTotal", com.persiki84.minimap.server.MapScanner.total(player));
        return tag;
    }

    private static CompoundTag gameRules() {
        CompoundTag tag = new CompoundTag();
        for (GameRule rule : GameRule.values()) {
            tag.putBoolean(rule.id(), GameRules.allows(rule));
        }
        return tag;
    }

    private static CompoundTag quarry() {
        QuarryBlockManager manager = QuarryMod.getInstance().getDataManager().getBlockManager();
        CompoundTag tag = new CompoundTag();

        tag.putInt("globalCooldown", (int) manager.getGlobalCooldown());
        tag.putInt("blocks", manager.getAllQuarryBlocks().size());
        tag.put(QUARRY_RULES, quarryRules(manager));
        tag.put(QUARRY_BLOCKS, quarryBlocks(manager));
        return tag;
    }

    // WHY: карьерных блоков бывают тысячи, а снимок уходит по сети целиком на каждый запрос меню,
    // WHY: поэтому список обрезан, а полное число лежит отдельным полем
    private static ListTag quarryBlocks(QuarryBlockManager manager) {
        ListTag list = new ListTag();
        for (QuarryBlock block : manager.getAllQuarryBlocks().values()) {
            if (list.size() >= LISTED_BLOCKS) break;
            list.add(quarryBlock(manager, block));
        }
        return list;
    }

    private static CompoundTag quarryBlock(QuarryBlockManager manager, QuarryBlock block) {
        CompoundTag entry = new CompoundTag();
        BlockPos pos = block.getPos();
        entry.putString("block", BuiltInRegistries.BLOCK.getKey(block.getOriginalState().getBlock()).toString());
        entry.putString("dimension", block.getDimension());
        entry.putInt("x", pos.getX());
        entry.putInt("y", pos.getY());
        entry.putInt("z", pos.getZ());
        entry.putInt("cooldown", (int) manager.getCustomCooldown(pos, block.getDimension()));
        return entry;
    }

    private static ListTag quarryRules(QuarryBlockManager manager) {
        ListTag list = new ListTag();
        for (QuarryBlockRule rule : manager.rules()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("block", rule.block().toString());
            entry.putInt("cooldown", rule.cooldownSeconds());
            entry.putInt("multiplier", rule.multiplier());
            list.add(entry);
        }
        return list;
    }

    private static CompoundTag killReward() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("modEnabled", KillRewardMod.modEnabled);
        tag.putString("rewardItem", KillRewardMod.rewardItem);
        tag.putInt("rewardAmount", KillRewardMod.rewardAmount);
        tag.putBoolean("rewardTeamKills", KillRewardMod.rewardTeamKills);
        return tag;
    }

    private static CompoundTag immortality(ServerPlayer viewer) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("enabled", ImmortalityHandler.isEnabled());
        tag.putInt("duration", ImmortalityHandler.getDuration());
        tag.put(IMMORTAL_PLAYERS, immortalPlayers(viewer));
        return tag;
    }

    private static ListTag immortalPlayers(ServerPlayer viewer) {
        ListTag list = new ListTag();
        MinecraftServer server = viewer.getServer();
        if (server == null) return list;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("name", player.getName().getString());
            entry.putBoolean("immortal", ImmortalityHandler.isImmortal(player));
            entry.putInt("remaining", ImmortalityHandler.getRemainingTime(player));
            list.add(entry);
        }
        return list;
    }

    private static CompoundTag knockdown() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("bleedTime", KnockdownConfig.BLEED_TIME_SECONDS.get());
        tag.putInt("reviveTime", KnockdownConfig.REVIVE_TIME_SECONDS.get());
        tag.putInt("injectorTime", KnockdownConfig.INJECTOR_TIME_SECONDS.get());
        tag.putInt("cooldownTime", KnockdownConfig.COOLDOWN_TIME_SECONDS.get());
        return tag;
    }

    private static CompoundTag combat() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("duration", CombatTimerMod.combatDuration);
        tag.putBoolean("killOnLogout", CombatTimerMod.killOnLogout);
        return tag;
    }

    private static CompoundTag sell(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putString("currency", SellManager.getCurrencyId());

        ListTag prices = new ListTag();
        for (Map.Entry<String, SellManager.SellPrice> entry : SellManager.getSellPrices().entrySet()) {
            CompoundTag stored = new CompoundTag();
            stored.putString("item", entry.getKey());
            stored.putInt("price", entry.getValue().price);
            prices.add(stored);
        }
        tag.put("prices", prices);
        return tag;
    }

    private static CompoundTag modifiers() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("modEnabled", ModifierConfig.MOD_ENABLED.get());
        tag.putInt("buffWarmup", ModifierConfig.BUFF_WARMUP.get());
        tag.putInt("debuffLinger", ModifierConfig.DEBUFF_LINGER.get());
        tag.putInt("potions", ModifierConfig.getPotionEffects().size());
        tag.putInt("attributes", ModifierConfig.getAttributes().size());
        tag.put(TRACKED_ITEMS, trackedItems());
        return tag;
    }

    private static ListTag trackedItems() {
        Map<String, CompoundTag> tracked = new LinkedHashMap<>();
        for (String entry : ModifierConfig.getPotionEffects()) {
            collectEntry(tracked, entry, "effects");
        }
        for (String entry : ModifierConfig.getAttributes()) {
            collectEntry(tracked, entry, "attributes");
        }

        ListTag list = new ListTag();
        list.addAll(tracked.values());
        return list;
    }

    private static void collectEntry(Map<String, CompoundTag> tracked, String entry, String kind) {
        String[] parts = entry.split("\\|");
        if (parts.length < 2) return;

        CompoundTag item = tracked.computeIfAbsent(parts[0], id -> {
            CompoundTag created = new CompoundTag();
            created.putString("item", id);
            created.put("effects", new ListTag());
            created.put("attributes", new ListTag());
            return created;
        });
        item.getList(kind, net.minecraft.nbt.Tag.TAG_STRING).add(net.minecraft.nbt.StringTag.valueOf(entry));
    }
}
