package com.persiki84.zones.shop;

import com.google.gson.Gson;
import com.persiki84.shared.WorldFiles;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ShopStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SECTION_LIST = new TypeToken<List<StoredSection>>() {}.getType();
    private static final String FOLDER = "battlecraft";
    private static final String FILE = "shop.json";

    private ShopStorage() {}

    public static List<ShopSection> load(ServerLevel level) {
        List<ShopSection> result = new ArrayList<>();
        Path path = WorldFiles.pathFor(level, FOLDER, FILE);
        if (path == null || !Files.exists(path)) return result;

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<StoredSection> stored = GSON.fromJson(reader, SECTION_LIST);
            if (stored == null) return result;
            for (StoredSection entry : stored) {
                result.add(entry.toSection());
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to read {}", path, e);
        }
        return result;
    }

    public static void save(ServerLevel level, Collection<ShopSection> sections) {
        Path path = WorldFiles.pathFor(level, FOLDER, FILE);
        if (path == null) return;

        List<StoredSection> stored = new ArrayList<>();
        for (ShopSection section : sections) {
            stored.add(StoredSection.of(section));
        }

        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(FILE + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(stored, SECTION_LIST, writer);
            }
            WorldFiles.moveIntoPlace(temporary, path);
        } catch (IOException e) {
            LOGGER.error("Failed to write {}", path, e);
        }
    }

    private static final class StoredSection {
        private String id;
        private String title;
        private List<String> teams;
        private List<StoredSection> children;
        private List<StoredEntry> entries;

        private static StoredSection of(ShopSection section) {
            StoredSection stored = new StoredSection();
            stored.id = section.id();
            stored.title = section.title();
            stored.teams = section.access().list();
            stored.children = new ArrayList<>();
            stored.entries = new ArrayList<>();

            for (ShopSection child : section.children().values()) {
                stored.children.add(of(child));
            }
            for (ShopEntry entry : section.entries().values()) {
                stored.entries.add(StoredEntry.of(entry));
            }
            return stored;
        }

        private ShopSection toSection() {
            ShopSection section = new ShopSection(id, title);
            section.access().restore(teams);
            if (children != null) {
                for (StoredSection child : children) {
                    section.children().put(child.id, child.toSection());
                }
            }
            if (entries != null) {
                for (StoredEntry entry : entries) {
                    ShopEntry built = entry.toEntry();
                    if (built != null) section.entries().put(built.id(), built);
                }
            }
            return section;
        }
    }

    private static long readyAtOf(ShopEntry entry, String key) {
        StockPool pool = entry.pool(key);
        return pool == null ? 0L : pool.readyAt();
    }

    private static final class StoredPool {
        private String key;
        private int available;
        private long readyAt;

        private static List<StoredPool> of(ShopEntry entry) {
            List<StoredPool> stored = new ArrayList<>();
            for (String key : entry.poolKeys()) {
                StockPool pool = entry.pool(key);
                StoredPool line = new StoredPool();
                line.key = key;
                line.available = pool.available();
                line.readyAt = pool.readyAt();
                stored.add(line);
            }
            return stored;
        }
    }

    private static final class StoredEntry {
        private String id;
        private String stackNbt;
        private int price;
        private String description;
        private int stock = ShopEntry.UNLIMITED;
        private int available = ShopEntry.UNLIMITED;
        private int restockSeconds;
        private long readyAt;
        private String scope;
        private List<StoredPool> pools;
        private List<String> teams;

        private static StoredEntry of(ShopEntry entry) {
            StoredEntry stored = new StoredEntry();
            stored.id = entry.id();
            stored.stackNbt = entry.stack().save(new CompoundTag()).toString();
            stored.price = entry.price();
            stored.description = entry.description();
            stored.stock = entry.stock();
            stored.available = entry.available();
            stored.restockSeconds = entry.restockSeconds();
            stored.scope = entry.scope().id();
            stored.pools = StoredPool.of(entry);
            stored.available = entry.availableIn(ShopEntry.OWN_POOL);
            stored.readyAt = readyAtOf(entry, ShopEntry.OWN_POOL);
            stored.teams = entry.access().list();
            return stored;
        }

        // WHY: файл старой версии не знает про склады по командам и игрокам, поэтому пара
        // WHY: available/readyAt в его корне это общий склад, и она читается как единственный пул
        private void restorePools(ShopEntry entry) {
            if (pools == null) {
                entry.restorePool(ShopEntry.OWN_POOL, available, readyAt);
                return;
            }
            for (StoredPool pool : pools) {
                entry.restorePool(pool.key, pool.available, pool.readyAt);
            }
        }

        private ShopEntry toEntry() {
            try {
                ItemStack stack = ItemStack.of(TagParser.parseTag(stackNbt));
                if (stack.isEmpty()) return null;

                ShopEntry entry = new ShopEntry(id, stack, price, description);
                entry.setStock(stock, restockSeconds);
                entry.setScope(StockScope.byId(scope));
                restorePools(entry);
                entry.access().restore(teams);
                return entry;
            } catch (CommandSyntaxException e) {
                LOGGER.warn("Skipping shop entry {} with unreadable item", id, e);
                return null;
            }
        }
    }
}
