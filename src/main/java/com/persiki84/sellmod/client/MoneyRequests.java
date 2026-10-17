package com.persiki84.sellmod.client;

import com.persiki84.sellmod.network.PacketHandler;
import com.persiki84.sellmod.network.WalletPayPacket;
import com.persiki84.shared.economy.CurrencyMode;
import com.persiki84.shared.economy.Money;

import java.util.UUID;

// WHY: заявки клиента о деньгах собраны здесь, чтобы экраны не повторяли строки команд: перевод идёт
// WHY: пакетом, правки оператора командами /battlecraft money, где права и пределы сверяет сервер
public final class MoneyRequests {
    public static final String ROOT = "battlecraft money ";

    private MoneyRequests() {}

    public static boolean pay(UUID target, long amount) {
        if (target == null || amount <= 0L || amount > Money.LIMIT) return false;
        PacketHandler.INSTANCE.sendToServer(new WalletPayPacket(target, amount));
        return true;
    }

    public static String set(String account, long amount) {
        return ROOT + "set " + account + " " + Money.clamp(amount);
    }

    public static String add(String account, long amount) {
        return ROOT + "add " + account + " " + Money.clamp(amount);
    }

    public static String take(String account, long amount) {
        return ROOT + "take " + account + " " + Money.clamp(amount);
    }

    public static String resetAll() {
        return ROOT + "reset";
    }

    public static String mode(CurrencyMode mode) {
        return ROOT + "mode " + mode.id();
    }

    public static String name(String name) {
        return name == null || name.isBlank() ? ROOT + "name" : ROOT + "name " + name.strip();
    }

    public static String iconFromSlot(int slot) {
        return ROOT + "icon slot " + slot;
    }

    public static String iconFromSpec(String itemSpec) {
        return ROOT + "icon item " + itemSpec;
    }

    public static String clearIcon() {
        return ROOT + "icon clear";
    }

    public static String startBalance(long amount) {
        return ROOT + "startbalance " + Money.clamp(amount);
    }

    public static String deathLoss(int percent) {
        return ROOT + "deathloss " + Math.max(0, Math.min(100, percent));
    }

    public static String resetOnStart(boolean enabled) {
        return ROOT + "resetonstart " + enabled;
    }
}
