package com.persiki84.sellmod.client;

import com.persiki84.sellmod.money.MoneyMenuState;
import com.persiki84.shared.economy.CurrencyMode;
import com.persiki84.shared.economy.WalletEntry;
import com.persiki84.shared.economy.WalletEntryKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// WHY: снимок меню «money» разбирается один раз на приход, а не в кадре: экран студии держит этот
// WHY: объект и сравнивает его ссылку как штамп данных
public record MoneySnapshot(CurrencyMode mode, String currency, String name, ItemStack icon, boolean resetOnStart,
                            long startBalance, int deathLossPercent, long limit, int total, List<Account> accounts) {
    public static final long UNKNOWN = -1L;

    public record Account(UUID id, String name, long balance, boolean online, long seen, List<WalletEntry> journal) {
        public boolean known() {
            return balance != UNKNOWN;
        }
    }

    public static MoneySnapshot of(CompoundTag tag) {
        ItemStack icon = tag.contains(MoneyMenuState.ICON, Tag.TAG_COMPOUND)
                ? ItemStack.of(tag.getCompound(MoneyMenuState.ICON)) : ItemStack.EMPTY;
        return new MoneySnapshot(CurrencyMode.byId(tag.getString(MoneyMenuState.MODE)),
                tag.getString(MoneyMenuState.CURRENCY), tag.getString(MoneyMenuState.NAME), icon,
                tag.getBoolean(MoneyMenuState.RESET_ON_START), tag.getLong(MoneyMenuState.START_BALANCE),
                tag.getInt(MoneyMenuState.DEATH_LOSS), tag.getLong(MoneyMenuState.LIMIT),
                tag.getInt(MoneyMenuState.TOTAL), accounts(tag.getList(MoneyMenuState.ACCOUNTS, Tag.TAG_COMPOUND)));
    }

    public boolean iconSet() {
        return !icon.isEmpty();
    }

    private static List<Account> accounts(ListTag list) {
        List<Account> accounts = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            CompoundTag row = list.getCompound(index);
            if (!row.hasUUID(MoneyMenuState.ID)) continue;
            accounts.add(new Account(row.getUUID(MoneyMenuState.ID), row.getString(MoneyMenuState.NAME),
                    row.getLong(MoneyMenuState.BALANCE), row.getBoolean(MoneyMenuState.ONLINE),
                    row.getLong(MoneyMenuState.SEEN), journal(row.getList(MoneyMenuState.JOURNAL,
                    Tag.TAG_COMPOUND))));
        }
        return List.copyOf(accounts);
    }

    private static List<WalletEntry> journal(ListTag list) {
        List<WalletEntry> entries = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            CompoundTag row = list.getCompound(index);
            WalletEntryKind kind = WalletEntryKind.byId(row.getString(WalletEntry.KIND));
            if (kind == null) continue;
            entries.add(new WalletEntry(kind, row.getLong(WalletEntry.DELTA), row.getLong(WalletEntry.AFTER),
                    row.getLong(WalletEntry.TIME), row.getString(WalletEntry.SUBJECT),
                    row.getInt(WalletEntry.COUNT)));
        }
        return List.copyOf(entries);
    }
}
