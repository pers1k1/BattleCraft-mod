package com.persiki84.shared.economy;

import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

public final class WalletAccount {
    public static final int JOURNAL_SIZE = 16;

    private final UUID id;
    private final ArrayDeque<WalletEntry> journal = new ArrayDeque<>(JOURNAL_SIZE);
    private String name;
    private long balance;
    private long granted;
    private long seen;

    WalletAccount(UUID id, String name, long balance, long seen) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.balance = Wallets.clamp(balance);
        this.seen = seen;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public long balance() {
        return balance;
    }

    // WHY: стартовые деньги начисляются каждому новому UUID, а на сервере без проверки входа UUID
    // WHY: выводится из ника: перевод стартовых денег превращал бы каждый новый ник в кошелёк для
    // WHY: основного. Начисленное стартом лежит отдельной долей, тратить его можно, отдать нельзя
    public long granted() {
        return granted;
    }

    public long transferable() {
        return Math.max(0L, balance - granted);
    }

    public long seen() {
        return seen;
    }

    public List<WalletEntry> journal() {
        return List.copyOf(journal);
    }

    void rename(String name) {
        if (name != null && !name.isEmpty()) this.name = name;
    }

    void see(long now) {
        seen = now;
    }

    void balance(long value) {
        balance = Wallets.clamp(value);
        granted = Math.min(granted, balance);
    }

    void grant(long value) {
        granted = Math.max(0L, Math.min(value, balance));
    }

    void record(WalletEntry entry) {
        if (journal.size() >= JOURNAL_SIZE) journal.pollFirst();
        journal.addLast(entry);
    }
}
