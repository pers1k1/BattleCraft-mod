package com.persiki84.shared.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.persiki84.shared.WorldFiles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class WalletStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FOLDER = "battlecraft";
    private static final String FILE = "wallets.json";
    private static final int VERSION = 1;

    private WalletStore() {}

    static Path pathOf(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FOLDER).resolve(FILE);
    }

    static void read(Path path, EconomySettings settings, Map<UUID, WalletAccount> into) {
        if (!Files.exists(path)) return;

        StoredWallets stored = parse(path);
        if (stored == null) return;

        if (stored.settings != null) stored.settings.applyTo(settings);
        int lost = 0;
        for (StoredAccount row : stored.accounts == null ? List.<StoredAccount>of() : stored.accounts) {
            WalletAccount account = row == null ? null : row.toAccount();
            if (account == null) {
                lost++;
                continue;
            }
            into.put(account.id(), account);
        }
        if (lost > 0) WorldFiles.keepCopy(path, LOGGER);
    }

    private static StoredWallets parse(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            StoredWallets stored = GSON.fromJson(reader, StoredWallets.class);
            return stored == null ? new StoredWallets() : stored;
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Failed to read {}, setting it aside", path, failure);
            WorldFiles.setAside(path, LOGGER);
            return null;
        }
    }

    static void write(Path path, EconomySettings settings, Collection<WalletAccount> accounts) {
        StoredWallets stored = new StoredWallets();
        stored.settings = StoredSettings.of(settings);
        stored.accounts = new ArrayList<>();
        for (WalletAccount account : accounts) {
            stored.accounts.add(StoredAccount.of(account));
        }
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(FILE + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(stored, writer);
            }
            WorldFiles.moveIntoPlace(temporary, path);
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Failed to write {}", path, failure);
        }
    }

    static ItemStack readIcon(String snbt) {
        if (snbt == null || snbt.isEmpty()) return ItemStack.EMPTY;
        try {
            return ItemStack.of(TagParser.parseTag(snbt));
        } catch (Exception failure) {
            LOGGER.warn("Skipping unreadable currency icon {}", snbt, failure);
            return ItemStack.EMPTY;
        }
    }

    private static final class StoredWallets {
        private int version = VERSION;
        private StoredSettings settings;
        private List<StoredAccount> accounts;
    }

    private static final class StoredSettings {
        private String mode;
        private String name;
        private String icon;
        private Boolean resetOnStart;
        private Long startBalance;
        private Integer deathLossPercent;

        private static StoredSettings of(EconomySettings settings) {
            StoredSettings stored = new StoredSettings();
            stored.mode = settings.mode().id();
            stored.name = settings.name();
            stored.icon = settings.iconSet() ? settings.icon().save(new CompoundTag()).toString() : "";
            stored.resetOnStart = settings.resetOnStart();
            stored.startBalance = settings.startBalance();
            stored.deathLossPercent = settings.deathLossPercent();
            return stored;
        }

        // WHY: отсутствующий ключ это заводское значение: файл старой версии не знает о новых настройках
        private void applyTo(EconomySettings settings) {
            settings.mode(CurrencyMode.byId(mode));
            settings.name(name);
            settings.icon(readIcon(icon));
            settings.resetOnStart(resetOnStart == null ? EconomySettings.RESET_ON_START_DEFAULT : resetOnStart);
            settings.startBalance(startBalance == null ? 0L : startBalance);
            settings.deathLossPercent(deathLossPercent == null ? 0 : deathLossPercent);
        }
    }

    private static final class StoredAccount {
        private String id;
        private String name;
        private long balance;
        private Long granted;
        private long seen;
        private List<StoredEntry> journal;

        private static StoredAccount of(WalletAccount account) {
            StoredAccount stored = new StoredAccount();
            stored.id = account.id().toString();
            stored.name = account.name();
            stored.balance = account.balance();
            stored.granted = account.granted();
            stored.seen = account.seen();
            stored.journal = new ArrayList<>();
            for (WalletEntry entry : account.journal()) {
                stored.journal.add(StoredEntry.of(entry));
            }
            return stored;
        }

        private WalletAccount toAccount() {
            UUID parsed = parseId(id);
            if (parsed == null) return null;

            WalletAccount account = new WalletAccount(parsed, name, balance, seen);
            if (granted != null) account.grant(granted);
            if (journal == null) return account;
            for (StoredEntry row : journal) {
                WalletEntry entry = row == null ? null : row.toEntry();
                if (entry != null) account.record(entry);
            }
            return account;
        }

        private static UUID parseId(String id) {
            if (id == null) return null;
            try {
                return UUID.fromString(id);
            } catch (IllegalArgumentException malformed) {
                return null;
            }
        }
    }

    private static final class StoredEntry {
        private String kind;
        private long delta;
        private long after;
        private long time;
        private String subject;
        private int count;

        private static StoredEntry of(WalletEntry entry) {
            StoredEntry stored = new StoredEntry();
            stored.kind = entry.kind().id();
            stored.delta = entry.delta();
            stored.after = entry.after();
            stored.time = entry.time();
            stored.subject = entry.subject();
            stored.count = entry.count();
            return stored;
        }

        private WalletEntry toEntry() {
            WalletEntryKind known = WalletEntryKind.byId(kind);
            return known == null ? null : new WalletEntry(known, delta, after, time, subject, count);
        }
    }
}
