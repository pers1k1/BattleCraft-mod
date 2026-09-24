package com.persiki84.battlecraft.menu;

import com.persiki84.airdrop.cache.LootCache;
import com.persiki84.airdrop.cache.LootCaches;
import com.persiki84.airdrop.config.AirDropConfig;
import com.persiki84.airdrop.loot.LootTable;
import com.persiki84.airdrop.loot.LootTables;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import static com.persiki84.airdrop.config.AirDropLimits.PERCENT;
import static com.persiki84.airdrop.config.AirDropLimits.TICKS_PER_SECOND;

public final class AirDropMenuState {

    private AirDropMenuState() {}

    public static CompoundTag airdrop() {
        CompoundTag tag = new CompoundTag();
        putSwitches(tag);
        putArea(tag);
        putTiming(tag);
        tag.putString("lootTable", AirDropConfig.airdropTable());
        tag.put("tables", tableNames());
        tag.put("caches", caches());
        return tag;
    }

    private static void putSwitches(CompoundTag tag) {
        AirDropConfig.Server config = AirDropConfig.SERVER;
        tag.putBoolean("modEnabled", config.modEnabled.get());
        tag.putBoolean("autoSpawnEnabled", config.autoSpawnEnabled.get());
        tag.putBoolean("matchOnly", config.matchOnly.get());
        tag.putBoolean("clearOnMatchEnd", config.clearOnMatchEnd.get());
        tag.putBoolean("announceCoords", config.announceCoords.get());
        tag.putBoolean("cacheClear", config.cacheClearOnMatchEnd.get());
        tag.putBoolean("cacheLock", config.cacheLockOutsideMatch.get());
    }

    private static void putArea(CompoundTag tag) {
        AirDropConfig.Server config = AirDropConfig.SERVER;
        tag.putBoolean("centerAtWorldSpawn", config.centerAtWorldSpawn.get());
        tag.putInt("centerX", (int) Math.round(config.centerX.get()));
        tag.putInt("centerZ", (int) Math.round(config.centerZ.get()));
        tag.putInt("spawnRadius", config.spawnRadius.get());
        tag.putInt("height", config.maxSpawnY.get());
    }

    private static void putTiming(CompoundTag tag) {
        AirDropConfig.Server config = AirDropConfig.SERVER;
        tag.putInt("intervalSeconds", config.intervalSeconds.get());
        tag.putInt("chancePercent", (int) Math.round(config.intervalSpawnChance.get() * PERCENT));
        tag.putInt("flightSeconds", config.flyingAnimTicks.get() / TICKS_PER_SECOND);
        tag.putInt("openDelaySeconds", config.autoOpenDelayTicks.get() / TICKS_PER_SECOND);
        tag.putInt("despawnEmptySeconds", config.despawnEmptySeconds.get());
        tag.putInt("despawnFilledSeconds", config.despawnFilledSeconds.get());
        tag.putInt("warnSeconds", config.notificationSecondsBeforeDespawn.get());
    }

    private static ListTag tableNames() {
        ListTag list = new ListTag();
        for (String name : LootTables.names()) {
            list.add(StringTag.valueOf(name));
        }
        return list;
    }

    private static ListTag caches() {
        ListTag list = new ListTag();
        for (LootCache cache : LootCaches.view()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("id", cache.id());
            entry.putLong("pos", cache.pos().asLong());
            entry.putString("dimension", cache.dimension());
            entry.putString("table", cache.table());
            entry.putString("tier", cache.tier().id());
            entry.putString("refill", cache.refill().id());
            entry.putInt("refillSeconds", cache.refillSeconds());
            entry.putBoolean("missing", cache.missing());
            entry.putBoolean("empty", cache.empty());
            list.add(entry);
        }
        return list;
    }

    public static CompoundTag loot() {
        CompoundTag tag = new CompoundTag();
        String airdropTable = AirDropConfig.airdropTable();
        ListTag list = new ListTag();
        for (LootTable table : LootTables.all()) {
            CompoundTag stored = table.toTag();
            stored.putBoolean("airdrop", table.name().equals(airdropTable));
            stored.putInt("caches", LootCaches.usingTable(table.name()));
            list.add(stored);
        }
        tag.put("tables", list);
        return tag;
    }
}
