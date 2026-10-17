package com.persiki84.sellmod.money;

import com.persiki84.sellmod.SellManager;
import com.persiki84.shared.economy.EconomySettings;
import com.persiki84.shared.economy.WalletAccount;
import com.persiki84.shared.economy.WalletAdmin;
import com.persiki84.shared.economy.WalletEntry;
import com.persiki84.shared.economy.Wallets;
import com.persiki84.shared.menu.MenuStates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// WHY: студия магазина получает счета снимком общего механизма меню, как и прочие разделы: запрос
// WHY: идёт по таймеру экрана, а правки уходят командами /battlecraft money, где права сверяет сервер
public final class MoneyMenuState {
    public static final String MENU_ID = "money";
    public static final String MODE = "mode";
    public static final String CURRENCY = "currency";
    public static final String NAME = "name";
    public static final String ICON = "icon";
    public static final String RESET_ON_START = "resetOnStart";
    public static final String START_BALANCE = "startBalance";
    public static final String DEATH_LOSS = "deathLoss";
    public static final String LIMIT = "limit";
    public static final String ACCOUNTS = "accounts";
    public static final String TOTAL = "total";
    public static final String ID = "id";
    public static final String BALANCE = "balance";
    public static final String ONLINE = "online";
    public static final String SEEN = "seen";
    public static final String JOURNAL = "journal";

    private static final int LISTED_ACCOUNTS = 100;
    private static final int LISTED_ENTRIES = 800;
    private static final Comparator<WalletAccount> ORDER = Comparator
            .comparing((WalletAccount account) -> Wallets.online(account.id()) == null)
            .thenComparing(WalletAccount::seen, Comparator.reverseOrder())
            .thenComparing(WalletAccount::name, String.CASE_INSENSITIVE_ORDER);

    private MoneyMenuState() {}

    public static void register() {
        MenuStates.register(MENU_ID, 2, MoneyMenuState::snapshot);
    }

    private static CompoundTag snapshot(ServerPlayer viewer) {
        for (ServerPlayer player : viewer.server.getPlayerList().getPlayers()) {
            Wallets.open(player);
        }
        CompoundTag tag = settings(Wallets.settings());
        List<WalletAccount> sorted = new ArrayList<>(Wallets.all());
        sorted.sort(ORDER);
        tag.put(ACCOUNTS, accounts(sorted));
        tag.putInt(TOTAL, sorted.size());
        return tag;
    }

    private static CompoundTag settings(EconomySettings settings) {
        CompoundTag tag = new CompoundTag();
        tag.putString(MODE, settings.mode().id());
        tag.putString(CURRENCY, SellManager.getCurrencyId());
        tag.putString(NAME, settings.name());
        if (settings.iconSet()) tag.put(ICON, settings.icon().save(new CompoundTag()));
        tag.putBoolean(RESET_ON_START, settings.resetOnStart());
        tag.putLong(START_BALANCE, settings.startBalance());
        tag.putInt(DEATH_LOSS, settings.deathLossPercent());
        tag.putLong(LIMIT, Wallets.LIMIT);
        return tag;
    }

    // WHY: снимок уходит по сети целиком на каждый запрос меню, поэтому список и журналы обрезаны:
    // WHY: игроки в сети и недавние идут первыми, полное число лежит отдельным полем
    private static ListTag accounts(List<WalletAccount> sorted) {
        ListTag list = new ListTag();
        int entries = 0;
        for (WalletAccount account : sorted) {
            if (list.size() >= LISTED_ACCOUNTS) break;
            CompoundTag row = account(account);
            entries += journal(row, account, LISTED_ENTRIES - entries);
            list.add(row);
        }
        return list;
    }

    private static CompoundTag account(WalletAccount account) {
        CompoundTag row = new CompoundTag();
        row.putUUID(ID, account.id());
        row.putString(NAME, account.name());
        row.putLong(BALANCE, WalletAdmin.balanceOf(account));
        row.putBoolean(ONLINE, Wallets.online(account.id()) != null);
        row.putLong(SEEN, account.seen());
        return row;
    }

    private static int journal(CompoundTag row, WalletAccount account, int room) {
        List<WalletEntry> entries = account.journal();
        ListTag list = new ListTag();
        for (int index = Math.max(0, entries.size() - room); index < entries.size(); index++) {
            list.add(entries.get(index).toTag());
        }
        row.put(JOURNAL, list);
        return list.size();
    }
}
