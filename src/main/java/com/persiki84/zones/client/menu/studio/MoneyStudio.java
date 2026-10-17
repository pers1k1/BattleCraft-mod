package com.persiki84.zones.client.menu.studio;

import com.persiki84.sellmod.client.MoneyRequests;
import com.persiki84.sellmod.client.MoneySnapshot;
import com.persiki84.sellmod.client.MoneyText;
import com.persiki84.sellmod.money.MoneyMenuState;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.pick.ItemShelf;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.economy.CurrencyMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// WHY: узел «Валюта» студии магазина: счета игроков плитками, правка выбранного справа, настройки
// WHY: валюты без выбора. Данные приходят снимком меню «money», правки уходят командами
// WHY: /battlecraft money, где права и пределы сверяет сервер, поэтому здесь нет ни одной проверки прав
final class MoneyStudio {
    static final String KEY = "money";
    static final String HEADING = "money-heading";

    private static final int POLL_TICKS = 10;
    private static final int QUICK_DEFAULT = 100;
    private static final String SETTINGS = "money:settings";
    private static final String ACCOUNT = "money:account:";
    private static final String SHELF_CURRENCY = "money:shelf:currency";
    private static final String SHELF_ICON = "money:shelf:icon";

    enum Picking { NONE, CURRENCY, ICON }

    private final ShopStudioScreen screen;
    private CompoundTag parsedFrom;
    private MoneySnapshot snapshot;
    private long signature;
    private long builtFor = Long.MIN_VALUE;
    private List<AccountTile> tiles = List.of();
    private UUID chosen;
    private int quick = QUICK_DEFAULT;
    private Picking picking = Picking.NONE;
    private CurrencyMode proposed;
    private String subjectKey;
    private String subjectText = SETTINGS;

    MoneyStudio(ShopStudioScreen screen) {
        this.screen = screen;
    }

    void tick(int ticks, boolean shown) {
        if (shown && ticks % POLL_TICKS == 0) MenuData.request(MoneyMenuState.MENU_ID);
    }

    void open() {
        chosen = null;
        picking = Picking.NONE;
        proposed = null;
        MenuData.invalidate(MoneyMenuState.MENU_ID);
        MenuData.request(MoneyMenuState.MENU_ID);
    }

    MoneySnapshot snapshot() {
        CompoundTag state = MenuData.state(MoneyMenuState.MENU_ID);
        if (state == parsedFrom) return snapshot;

        parsedFrom = state;
        snapshot = state.isEmpty() ? null : MoneySnapshot.of(state);
        signature = snapshot == null ? 0L : sign(snapshot);
        return snapshot;
    }

    long signature() {
        snapshot();
        return signature;
    }

    // WHY: снимок приходит заново на каждый опрос, даже без перемен: подпись из значимых полей
    // WHY: отличает настоящую правку, и только она перестраивает плитки и колонку справа
    private static long sign(MoneySnapshot shot) {
        long hash = shot.mode().ordinal();
        hash = hash * 31L + shot.currency().hashCode();
        hash = hash * 31L + shot.name().hashCode();
        hash = hash * 31L + iconHash(shot.icon());
        hash = hash * 31L + (shot.resetOnStart() ? 1L : 0L);
        hash = hash * 31L + shot.startBalance();
        hash = hash * 31L + shot.deathLossPercent();
        hash = hash * 31L + shot.total();
        for (MoneySnapshot.Account account : shot.accounts()) {
            hash = hash * 31L + account.id().hashCode();
            hash = hash * 31L + account.name().hashCode();
            hash = hash * 31L + account.balance();
            hash = hash * 31L + (account.online() ? 1L : 0L);
            hash = hash * 31L + account.journal().size();
            if (!account.journal().isEmpty()) hash = hash * 31L + account.journal().get(account.journal().size() - 1).time();
        }
        return hash;
    }

    private static long iconHash(ItemStack icon) {
        if (icon.isEmpty()) return 0L;
        long hash = BuiltInRegistries.ITEM.getKey(icon.getItem()).hashCode();
        return icon.getTag() == null ? hash : hash * 31L + icon.getTag().hashCode();
    }

    boolean ready() {
        return snapshot() != null;
    }

    List<AccountTile> tiles() {
        MoneySnapshot shot = snapshot();
        if (shot == null) return List.of();
        if (builtFor == signature) return tiles;

        builtFor = signature;
        UUID self = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        List<AccountTile> built = new ArrayList<>(shot.accounts().size());
        for (MoneySnapshot.Account account : shot.accounts()) {
            built.add(AccountTile.of(account, account.id().equals(self)));
        }
        tiles = built;
        return tiles;
    }

    List<StudioNav.Node> nodes() {
        MoneySnapshot shot = snapshot();
        Component count = shot == null ? Component.empty() : Component.literal(String.valueOf(shot.total()));
        return List.of(StudioNav.Node.heading(HEADING, Component.translatable("sellmod.studio.money.group")),
                StudioNav.Node.item(KEY, Component.translatable("sellmod.studio.money.node"), count, 0, false));
    }

    Component stats() {
        MoneySnapshot shot = snapshot();
        if (shot == null) return Component.translatable("battlecraft.menu.waiting");
        return Component.translatable("sellmod.studio.money.stats", Component.translatable(shot.mode()
                .translationKey()), shot.total(), online(shot));
    }

    static int online(MoneySnapshot shot) {
        int online = 0;
        for (MoneySnapshot.Account account : shot.accounts()) {
            if (account.online()) online++;
        }
        return online;
    }

    static long held(MoneySnapshot shot) {
        long total = 0L;
        for (MoneySnapshot.Account account : shot.accounts()) {
            if (account.known()) total += account.balance();
        }
        return total;
    }

    // WHY: предмет колонки спрашивается каждый кадр, поэтому строка собирается только на смене выбора
    Object subject() {
        if (picking != Picking.NONE) return picking == Picking.CURRENCY ? SHELF_CURRENCY : SHELF_ICON;
        AccountTile tile = chosenTile();
        if (tile == null) return SETTINGS;
        if (tile.key() != subjectKey) {
            subjectKey = tile.key();
            subjectText = ACCOUNT + subjectKey;
        }
        return subjectText;
    }

    AccountTile chosenTile() {
        if (chosen == null) return null;
        List<AccountTile> shown = tiles();
        for (int index = 0; index < shown.size(); index++) {
            if (shown.get(index).account().id().equals(chosen)) return shown.get(index);
        }
        return null;
    }

    int selectedTile() {
        if (chosen == null) return -1;
        List<AccountTile> shown = tiles();
        for (int index = 0; index < shown.size(); index++) {
            if (shown.get(index).account().id().equals(chosen)) return index;
        }
        return -1;
    }

    void selectTile(int index) {
        List<AccountTile> shown = tiles();
        chosen = index < 0 || index >= shown.size() ? null : shown.get(index).account().id();
        picking = Picking.NONE;
    }

    String tileKey(int index) {
        List<AccountTile> shown = tiles();
        return index >= 0 && index < shown.size() ? shown.get(index).key() : null;
    }

    List<AccountTile> tilesAt(List<Integer> indices) {
        List<AccountTile> shown = tiles();
        List<AccountTile> picked = new ArrayList<>(indices.size());
        for (int index : indices) {
            if (index >= 0 && index < shown.size()) picked.add(shown.get(index));
        }
        return picked;
    }

    int quick() {
        return quick;
    }

    void quick(int value) {
        quick = Math.max(1, value);
    }

    void credit(List<AccountTile> targets, long amount) {
        for (AccountTile tile : targets) {
            if (tile.account().known()) screen.money(MoneyRequests.add(tile.address(), amount));
        }
    }

    void debit(List<AccountTile> targets, long amount) {
        for (AccountTile tile : targets) {
            if (tile.account().known()) screen.money(MoneyRequests.take(tile.address(), amount));
        }
    }

    void zero(List<AccountTile> targets) {
        for (AccountTile tile : targets) {
            if (tile.account().known()) screen.money(MoneyRequests.set(tile.address(), 0L));
        }
    }

    void balance(AccountTile tile, int value) {
        screen.later("money:balance:" + tile.key(), MoneyRequests.set(tile.address(), value));
    }

    void resetAll() {
        screen.money(MoneyRequests.resetAll());
    }

    List<StudioMenu.Action> tileActions(int index) {
        AccountTile tile = index >= 0 && index < tiles().size() ? tiles().get(index) : null;
        if (tile == null) return List.of();
        boolean known = tile.account().known();
        List<AccountTile> one = List.of(tile);
        String amount = MoneyText.digits(quick);
        return List.of(
                action(Component.translatable("sellmod.studio.money.menu.credit", amount), () -> credit(one, quick), known),
                action(Component.translatable("sellmod.studio.money.menu.debit", amount), () -> debit(one, quick), known),
                new StudioMenu.Action(Component.translatable("sellmod.studio.money.menu.zero"), () -> zero(one),
                        true, true, known && tile.account().balance() > 0L));
    }

    // WHY: подписи действий над группой не несут суммы: кнопки колонки живут по подписи, и каждое
    // WHY: новое число заводило бы новую кнопку. Сумма стоит строкой прямо над ними
    List<StudioMenu.Action> bulkActions(List<Integer> indices) {
        List<AccountTile> group = tilesAt(indices);
        int count = group.size();
        return List.of(
                action(Component.translatable("sellmod.studio.money.bulk.credit", count), () -> credit(group, quick), true),
                action(Component.translatable("sellmod.studio.money.bulk.debit", count), () -> debit(group, quick), true),
                new StudioMenu.Action(Component.translatable("sellmod.studio.money.bulk.zero", count),
                        () -> zero(group), true, true, true));
    }

    private static StudioMenu.Action action(Component label, Runnable run, boolean enabled) {
        return new StudioMenu.Action(label, run, false, false, enabled);
    }

    List<StudioMenu.Action> canvasActions() {
        boolean accounts = snapshot() != null && snapshot().mode() == CurrencyMode.ACCOUNT;
        return List.of(StudioMenu.Action.of("sellmod.studio.money.menu.settings", this::showSettings),
                StudioMenu.Action.careful("sellmod.studio.money.menu.reset_all", this::resetAll).when(accounts));
    }

    List<StudioMenu.Action> nodeActions() {
        return canvasActions();
    }

    private void showSettings() {
        chosen = null;
        picking = Picking.NONE;
        screen.relayout();
    }

    boolean shelfOpen() {
        return picking != Picking.NONE;
    }

    Picking picking() {
        return picking;
    }

    void pickFor(Picking target) {
        picking = target;
        screen.relayout();
    }

    // WHY: предмет валюты сервер хранит идентификатором, а значок счёта целым стаком с тегами: тег
    // WHY: несёт вид предмета у модовых вещей, поэтому значок уходит со слотом или полной записью
    void pick(ItemShelf.Pick pick) {
        if (picking == Picking.CURRENCY) {
            screen.money("sell setcurrency " + BuiltInRegistries.ITEM.getKey(pick.stack().getItem()));
        } else if (picking == Picking.ICON) {
            screen.money(pick.fromInventory() ? MoneyRequests.iconFromSlot(pick.slot())
                    : MoneyRequests.iconFromSpec(ItemShelf.spec(pick.stack())));
        }
        picking = Picking.NONE;
        screen.relayout();
    }

    CurrencyMode proposed() {
        return proposed;
    }

    void propose(CurrencyMode mode) {
        MoneySnapshot shot = snapshot();
        proposed = shot == null || mode == shot.mode() ? null : mode;
        screen.relayout();
    }

    void confirmMode() {
        if (proposed == null) return;
        screen.money(MoneyRequests.mode(proposed));
        proposed = null;
        screen.relayout();
    }

    void dropProposal() {
        proposed = null;
    }
}
