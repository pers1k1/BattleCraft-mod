package com.persiki84.shared.economy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// WHY: счета живут в файле мира независимо от режима валюты: смена режима ничего не конвертирует,
// WHY: и вернувшийся «Счёт» находит деньги там, где их оставили
public final class Wallets {
    public static final long LIMIT = Money.LIMIT;

    private static final int SAVE_TICKS = 100;
    private static final Map<UUID, WalletAccount> accounts = new HashMap<>();
    private static final EconomySettings settings = new EconomySettings();
    private static MinecraftServer server;
    private static boolean dirty;
    private static int dirtyTicks;

    private Wallets() {}

    public static EconomySettings settings() {
        return settings;
    }

    public static MinecraftServer server() {
        return server;
    }

    public static Collection<WalletAccount> all() {
        return Collections.unmodifiableCollection(accounts.values());
    }

    public static WalletAccount account(UUID id) {
        return id == null ? null : accounts.get(id);
    }

    public static long clamp(long value) {
        return Money.clamp(value);
    }

    public static void load(MinecraftServer running) {
        forget();
        server = running;
        WalletStore.read(WalletStore.pathOf(running), settings, accounts);
    }

    public static void save() {
        if (server == null) return;

        WalletStore.write(WalletStore.pathOf(server), settings, accounts.values());
        dirty = false;
        dirtyTicks = 0;
    }

    public static void saveIfDirty() {
        if (dirty) save();
    }

    // WHY: покупка, продажа и доход идут десятками за минуту, поэтому файл пишется не на каждую
    // WHY: операцию, а раз в пять секунд, пока есть несохранённое
    public static void tick() {
        if (!dirty || ++dirtyTicks < SAVE_TICKS) return;
        save();
    }

    public static void forget() {
        accounts.clear();
        settings.restoreDefaults();
        server = null;
        dirty = false;
        dirtyTicks = 0;
    }

    public static void touch() {
        dirty = true;
    }

    public static WalletAccount open(ServerPlayer player) {
        WalletAccount account = accounts.get(player.getUUID());
        if (account != null) return account;

        account = new WalletAccount(player.getUUID(), player.getGameProfile().getName(),
                settings.startBalance(), System.currentTimeMillis());
        account.grant(settings.startBalance());
        accounts.put(account.id(), account);
        touch();
        return account;
    }

    public static void arrived(ServerPlayer player) {
        WalletAccount account = open(player);
        account.rename(player.getGameProfile().getName());
        account.see(System.currentTimeMillis());
        touch();
    }

    public static WalletAccount byName(String name) {
        if (name == null || name.isEmpty()) return null;

        ServerPlayer online = server == null ? null : server.getPlayerList().getPlayerByName(name);
        if (online != null) return open(online);
        for (WalletAccount account : accounts.values()) {
            if (account.name().equalsIgnoreCase(name)) return account;
        }
        return null;
    }

    public static ServerPlayer online(UUID id) {
        return server == null || id == null ? null : server.getPlayerList().getPlayer(id);
    }
}
