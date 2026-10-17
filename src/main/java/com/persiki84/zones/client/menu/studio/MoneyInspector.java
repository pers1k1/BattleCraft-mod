package com.persiki84.zones.client.menu.studio;

import com.persiki84.sellmod.client.CurrencyIcon;
import com.persiki84.sellmod.client.MoneyRequests;
import com.persiki84.sellmod.client.MoneySnapshot;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.ItemRow;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.menu.TickerLines;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.economy.CurrencyMode;
import com.persiki84.shared.economy.EconomySettings;
import com.persiki84.shared.economy.Money;
import com.persiki84.shared.economy.WalletEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

// WHY: колонка справа у узла «Валюта» говорит о том, что выбрано: без выбора это настройки валюты,
// WHY: с одним счётом его баланс, быстрые суммы и журнал, с группой общая сумма. Все строки заведены
// WHY: один раз и только переставляются: анимации живут в самих строках и не рвутся на снимке
final class MoneyInspector {
    private static final int ROW = 22;
    private static final int CONTROL = 16;
    private static final int GAP = 4;
    private static final int NOTE_HEIGHT = 46;
    private static final int EMPTY_HEIGHT = 24;
    private static final int HISTORY_LIMIT = 16;
    private static final int LIMIT = (int) Money.LIMIT;
    private static final int BALANCE_STEP = 100;
    private static final int QUICK_STEP = 10;
    private static final int START_STEP = 50;
    private static final int LOSS_STEP = 5;
    private static final int NAME_LIMIT = 24;
    private static final int SETTINGS_ART = 54;
    private static final int ACCOUNT_ART = 52;
    private static final float ART_ICON = 26.0f;
    private static final float HEAD_SCALE = 2.2f;
    private static final float LINE_SCALE = 0.72f;
    private static final float CONFIRM_SLIDE = 10.0f;
    private static final CurrencyMode[] MODES = CurrencyMode.values();
    private static final String NAME = "money:name";
    private static final String RESET_KEY = "money:reset";
    private static final String ZERO_KEY = "money:zero";
    private static final Component SURE = Component.translatable("studio.sure");
    private static final Component APPLY = Component.translatable("sellmod.studio.money.mode.apply");
    private static final Component NO_CURRENCY = Component.translatable("sellmod.studio.money.currency.none");
    private static final Component COIN = Component.translatable("sellmod.studio.money.icon.coin");
    private static final Component CLEAR_ICON = Component.translatable("sellmod.studio.money.icon.clear.value");
    private static final Component RESET_ALL = Component.translatable("sellmod.studio.money.reset_all.value");
    private static final Component ZERO = Component.translatable("sellmod.studio.money.zero.value");
    private static final Component CANCEL = Component.translatable("sellmod.studio.money.pick.cancel.value");
    private static final Component ACCOUNT_ONLY = Component.translatable("sellmod.studio.money.account_only");

    private final ShopStudioScreen screen;
    private final MoneyStudio money;
    private final List<WalletEntryRow> history = new ArrayList<>(HISTORY_LIMIT);
    private HeadingRow currencyHeading;
    private PickRow modeRow;
    private MoneyNoteRow modeNote;
    private ActionRow modeConfirm;
    private ItemRow currencyRow;
    private ItemRow iconRow;
    private ActionRow clearIconRow;
    private FieldRow nameRow;
    private HeadingRow rulesHeading;
    private NumberRow startRow;
    private NumberRow lossRow;
    private ToggleRow resetOnStartRow;
    private HeadingRow resetHeading;
    private ActionRow resetAllRow;
    private HeadingRow accountHeading;
    private NumberRow balanceRow;
    private NumberRow amountRow;
    private UiButton creditButton;
    private UiButton debitButton;
    private ActionRow zeroRow;
    private HeadingRow historyHeading;
    private MoneyNoteRow historyEmpty;
    private HeadingRow bulkHeading;
    private NumberRow bulkAmountRow;
    private HeadingRow shelfHeading;
    private ActionRow shelfCancel;
    private boolean filling;
    private String filledFor;
    private long historyFor = Long.MIN_VALUE;
    private int historyShown;
    private long artFor = Long.MIN_VALUE;
    private Object artSubject;
    private ItemStack currencyStack = ItemStack.EMPTY;
    private Component artTitle = Component.empty();
    private Component artFirst = Component.empty();
    private Component artSecond = Component.empty();
    private final TickerLines firstLines = new TickerLines(0.5f);
    private final TickerLines secondLines = new TickerLines(0.5f);
    private CurrencyMode warnedFor;
    private Component cachedWarning = Component.empty();
    private Object historySubject;
    private boolean warned;
    private long warnedSignature = Long.MIN_VALUE;

    MoneyInspector(ShopStudioScreen screen, MoneyStudio money) {
        this.screen = screen;
        this.money = money;
        createSettingsRows();
        createRuleRows();
        createAccountRows();
        createGroupRows();
        for (int index = 0; index < HISTORY_LIMIT; index++) {
            history.add(new WalletEntryRow(ROW));
        }
    }

    private void createSettingsRows() {
        currencyHeading = heading("sellmod.studio.money.group.currency");
        modeRow = new PickRow(0, 0, 10, ROW, Component.translatable("sellmod.studio.money.mode"), modeLabels(),
                this::modeIndex, picked -> money.propose(MODES[picked]));
        modeRow.hint("sellmod.studio.money.mode.hint");
        modeNote = new MoneyNoteRow(NOTE_HEIGHT);
        modeConfirm = hinted(new ActionRow(0, 0, 10, ROW, Component.translatable("sellmod.studio.money.mode.confirm"),
                () -> APPLY, money::confirmMode).alerting(),
                "sellmod.studio.money.mode.confirm");
        currencyRow = hinted(new ItemRow(0, 0, 10, ROW, Component.translatable("sellmod.studio.money.currency"),
                () -> currencyStack, () -> NO_CURRENCY,
                () -> money.pickFor(MoneyStudio.Picking.CURRENCY)), "sellmod.studio.money.currency");
        iconRow = hinted(new ItemRow(0, 0, 10, ROW, Component.translatable("sellmod.studio.money.icon"),
                this::snapshotIcon, () -> COIN,
                () -> money.pickFor(MoneyStudio.Picking.ICON)), "sellmod.studio.money.icon");
        clearIconRow = new ActionRow(0, 0, 10, ROW, Component.translatable("sellmod.studio.money.icon.clear"),
                () -> CLEAR_ICON,
                () -> screen.money(MoneyRequests.clearIcon()));
        nameRow = hinted(new FieldRow(0, 0, 10, ROW, Component.translatable("sellmod.studio.money.name"),
                Component.translatable(Money.DEFAULT_NAME_KEY), "", NAME_LIMIT, this::nameTyped),
                "sellmod.studio.money.name");
    }

    private void createRuleRows() {
        rulesHeading = heading("sellmod.studio.money.group.rules");
        startRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("sellmod.studio.money.start"),
                () -> settingsValue(shot -> (int) shot.startBalance()),
                value -> screen.later("money:start", MoneyRequests.startBalance(value)), 0, LIMIT, START_STEP),
                "sellmod.studio.money.start");
        lossRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("sellmod.studio.money.death"),
                () -> settingsValue(MoneySnapshot::deathLossPercent),
                value -> screen.later("money:death", MoneyRequests.deathLoss(value)), 0,
                EconomySettings.DEATH_LOSS_MAX, LOSS_STEP)
                .floorLabel(Component.translatable("sellmod.studio.money.death.off")), "sellmod.studio.money.death");
        resetOnStartRow = hinted(new ToggleRow(0, 0, 10, ROW, Component.translatable("sellmod.studio.money.reset_start"),
                () -> money.snapshot() != null && money.snapshot().resetOnStart(),
                value -> screen.money(MoneyRequests.resetOnStart(value))), "sellmod.studio.money.reset_start");
        resetHeading = heading("sellmod.studio.money.group.reset");
        resetAllRow = hinted(new ActionRow(0, 0, 10, ROW, Component.translatable("sellmod.studio.money.reset_all"),
                () -> screen.isArmed(RESET_KEY) ? SURE : RESET_ALL,
                this::pressResetAll).alerting(), "sellmod.studio.money.reset_all");
    }

    private void createAccountRows() {
        accountHeading = heading("sellmod.studio.money.group.account");
        balanceRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("sellmod.studio.money.balance"),
                this::chosenBalance, this::balanceTyped, 0, LIMIT, BALANCE_STEP), "sellmod.studio.money.balance");
        amountRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("sellmod.studio.money.amount"),
                money::quick, money::quick, 1, LIMIT, QUICK_STEP), "sellmod.studio.money.amount");
        creditButton = new UiButton(0, 0, 10, CONTROL, Component.translatable("sellmod.studio.money.credit"),
                pressed -> money.credit(chosenOnly(), money.quick())).hint("sellmod.studio.money.credit.hint");
        debitButton = new UiButton(0, 0, 10, CONTROL, Component.translatable("sellmod.studio.money.debit"),
                pressed -> money.debit(chosenOnly(), money.quick())).hint("sellmod.studio.money.debit.hint");
        zeroRow = new ActionRow(0, 0, 10, ROW, Component.translatable("sellmod.studio.money.zero"),
                () -> screen.isArmed(ZERO_KEY) ? SURE : ZERO,
                this::pressZero).alerting();
        historyHeading = heading("sellmod.studio.money.group.history");
        historyEmpty = new MoneyNoteRow(EMPTY_HEIGHT);
        historyEmpty.show(Component.translatable("sellmod.studio.money.history.empty"), false);
    }

    private void createGroupRows() {
        bulkHeading = heading("studio.bulk.group");
        bulkAmountRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("sellmod.studio.money.bulk.amount"),
                money::quick, money::quick, 1, LIMIT, QUICK_STEP), "sellmod.studio.money.bulk.amount");
        shelfHeading = heading("sellmod.studio.money.group.pick");
        shelfCancel = new ActionRow(0, 0, 10, ROW, Component.translatable("sellmod.studio.money.pick.cancel"),
                () -> CANCEL,
                () -> money.pickFor(MoneyStudio.Picking.NONE));
    }

    private static HeadingRow heading(String key) {
        return new HeadingRow(0, 0, 10, ROW, Component.translatable(key));
    }

    private static <T extends MenuRow> T hinted(T row, String key) {
        row.hint(key + ".hint");
        return row;
    }

    private static List<Component> modeLabels() {
        List<Component> labels = new ArrayList<>(MODES.length);
        for (CurrencyMode mode : MODES) {
            labels.add(Component.translatable(mode.translationKey()));
        }
        return labels;
    }

    private int modeIndex() {
        CurrencyMode proposed = money.proposed();
        if (proposed != null) return proposed.ordinal();
        MoneySnapshot shot = money.snapshot();
        return shot == null ? CurrencyMode.DEFAULT.ordinal() : shot.mode().ordinal();
    }

    private ItemStack snapshotIcon() {
        MoneySnapshot shot = money.snapshot();
        return shot == null ? ItemStack.EMPTY : shot.icon();
    }

    private int settingsValue(java.util.function.ToIntFunction<MoneySnapshot> read) {
        MoneySnapshot shot = money.snapshot();
        return shot == null ? 0 : read.applyAsInt(shot);
    }

    private int chosenBalance() {
        AccountTile tile = money.chosenTile();
        return tile == null || !tile.account().known() ? 0 : (int) tile.account().balance();
    }

    private List<AccountTile> chosenOnly() {
        AccountTile tile = money.chosenTile();
        return tile == null ? List.of() : List.of(tile);
    }

    private void balanceTyped(int value) {
        AccountTile tile = money.chosenTile();
        if (tile != null && tile.account().known()) money.balance(tile, value);
    }

    private void nameTyped(String text) {
        if (!filling) screen.later(NAME, MoneyRequests.name(text));
    }

    private void pressResetAll() {
        if (screen.confirmed(RESET_KEY)) money.resetAll();
    }

    private void pressZero() {
        if (screen.confirmed(ZERO_KEY)) money.zero(chosenOnly());
    }

    void build(StudioStack stack, int x, int width, boolean group) {
        if (money.shelfOpen()) {
            shelf(stack, x, width);
        } else if (group) {
            stack.add(sized(bulkHeading, width), x);
            stack.add(sized(bulkAmountRow, width), x);
        } else if (money.chosenTile() != null) {
            account(stack, x, width, money.chosenTile());
        } else if (money.snapshot() != null) {
            settings(stack, x, width, money.snapshot());
        }
    }

    private void shelf(StudioStack stack, int x, int width) {
        shelfHeading.setMessage(Component.translatable(money.picking() == MoneyStudio.Picking.CURRENCY
                ? "sellmod.studio.money.group.pick.currency" : "sellmod.studio.money.group.pick.icon"));
        stack.add(sized(shelfHeading, width), x);
        stack.add(sized(shelfCancel, width), x);
        stack.skip(GAP);
    }

    private void settings(StudioStack stack, int x, int width, MoneySnapshot shot) {
        boolean accounts = shot.mode() == CurrencyMode.ACCOUNT;
        fill(shot);
        stack.add(sized(currencyHeading, width), x);
        stack.add(sized(modeRow, width), x);
        boolean proposing = money.proposed() != null;
        if (proposing && !warned) modeConfirm.stagger(1, CONFIRM_SLIDE);
        warned = proposing;
        if (proposing) {
            modeNote.show(warning(shot), true);
            stack.add(sized(modeNote, width), x);
            stack.add(sized(modeConfirm, width), x);
        }
        stack.add(sized(currencyRow, width), x);
        stack.add(sized(gate(iconRow, accounts), width), x);
        if (accounts && shot.iconSet()) stack.add(sized(clearIconRow, width), x);
        stack.add(sized(gate(nameRow, accounts), width), x);
        stack.add(sized(rulesHeading, width), x, GAP);
        stack.add(sized(gate(startRow, accounts), width), x);
        stack.add(sized(gate(lossRow, accounts), width), x);
        stack.add(sized(gate(resetOnStartRow, accounts), width), x);
        if (!accounts) return;
        stack.add(sized(resetHeading, width), x, GAP);
        stack.add(sized(resetAllRow, width), x);
    }

    private static <T extends MenuRow> T gate(T row, boolean allowed) {
        return gate(row, allowed, ACCOUNT_ONLY);
    }

    private static <T extends MenuRow> T gate(T row, boolean allowed, Component reason) {
        if (allowed) {
            row.unblock();
        } else {
            row.block(reason);
        }
        return row;
    }

    // WHY: смена режима ничего не переводит, и это нужно сказать до нажатия: в «Предмете» суммы на
    // WHY: счетах лежат без дела, а в «Счёте» валюта в инвентарях остаётся предметами
    private Component warning(MoneySnapshot shot) {
        if (money.proposed() == warnedFor && money.signature() == warnedSignature) return cachedWarning;
        warnedFor = money.proposed();
        warnedSignature = money.signature();
        long held = MoneyStudio.held(shot);
        cachedWarning = shot.mode() == CurrencyMode.ACCOUNT
                ? Component.translatable("sellmod.studio.money.warn.to_item", amount(shot, held))
                : Component.translatable("sellmod.studio.money.warn.to_account", amount(shot, held));
        return cachedWarning;
    }

    private static Component amount(MoneySnapshot shot, long value) {
        return Money.amount(value, shot.mode(), shot.name(), itemOf(shot.currency()).getDescription());
    }

    private static Item itemOf(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        Item item = location == null ? null : ForgeRegistries.ITEMS.getValue(location);
        return item == null ? Items.AIR : item;
    }

    private void account(StudioStack stack, int x, int width, AccountTile tile) {
        boolean known = tile.account().known();
        Component offline = known ? null : Component.translatable("sellmod.money.error.offline_items", tile.address());
        stack.add(sized(accountHeading, width), x);
        stack.add(sized(gate(balanceRow, known, offline), width), x);
        stack.add(sized(amountRow, width), x);
        int half = (width - GAP) / 2;
        creditButton.active = known;
        debitButton.active = known && tile.account().balance() > 0L;
        stack.add(sized(creditButton, half), x);
        stack.beside(sized(debitButton, width - half - GAP), x + half + GAP);
        stack.add(sized(gate(zeroRow, known, offline), width), x, GAP);
        placeHistory(stack, x, width, tile);
    }

    // WHY: журнал приходит старыми записями вперёд, а читают его с последней: строки идут от новой к старой
    private void placeHistory(StudioStack stack, int x, int width, AccountTile tile) {
        List<WalletEntry> journal = tile.account().journal();
        if (historyFor != money.signature() || !money.subject().equals(historySubject)) {
            historyFor = money.signature();
            historySubject = money.subject();
            long now = System.currentTimeMillis();
            historyShown = Math.min(HISTORY_LIMIT, journal.size());
            for (int index = 0; index < historyShown; index++) {
                history.get(index).show(journal.get(journal.size() - 1 - index), now);
            }
        }
        stack.add(sized(historyHeading, width), x, GAP);
        if (historyShown == 0) {
            stack.add(sized(historyEmpty, width), x);
            return;
        }
        for (int index = 0; index < historyShown; index++) {
            stack.add(sized(history.get(index), width), x);
        }
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    // WHY: поле имени заполняется из снимка только при смене выбранного или когда по нему нет
    // WHY: отложенной правки: снимок, пришедший до отправки, вернул бы старое имя под пальцы
    void fill(MoneySnapshot shot) {
        String subject = String.valueOf(money.subject());
        boolean fresh = !subject.equals(filledFor);
        filledFor = subject;
        refreshCurrency(shot);
        if (nameRow.capturing() || (!fresh && screen.pending(NAME))) return;
        filling = true;
        try {
            if (!nameRow.value().equals(shot.name())) nameRow.box().setValue(shot.name());
        } finally {
            filling = false;
        }
    }

    private void refreshCurrency(MoneySnapshot shot) {
        Item item = itemOf(shot.currency());
        if (!currencyStack.is(item)) currencyStack = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    int artHeight(boolean group) {
        if (group || money.shelfOpen()) return 0;
        if (money.chosenTile() != null) return ACCOUNT_ART;
        return money.snapshot() == null ? 0 : SETTINGS_ART;
    }

    void renderArt(GuiGraphics graphics, float left, float top, float width, float appear) {
        MoneySnapshot shot = money.snapshot();
        if (shot == null || money.shelfOpen()) return;
        relabelArt(shot);
        AccountTile tile = money.chosenTile();
        if (tile != null) {
            renderAccountArt(graphics, tile, left, top, width, appear);
            return;
        }
        float iconLeft = left + (width - ART_ICON) / 2.0f;
        CurrencyIcon.draw(graphics, iconLeft, top, ART_ICON, UiAccent.color(), appear);
        line(graphics, artTitle, left, width, top + ART_ICON + 3.0f, 0.95f, UiAccent.text(), appear);
        firstLines.of(money.subject()).box(graphics, font(), artFirst, left, top + ART_ICON + 15.0f, 10.0f, width,
                LINE_SCALE, UiTheme.alpha(UiAccent.textDim(), appear));
    }

    private void renderAccountArt(GuiGraphics graphics, AccountTile tile, float left, float top, float width,
                                  float appear) {
        float headSize = 16.0f * HEAD_SCALE;
        CurrencyIcon.item(graphics, tile.icon(), left, top, headSize, appear);
        float textLeft = left + headSize + 8.0f;
        float room = left + width - textLeft;
        UiRender.textTrackedFit(graphics, font(), tile.name(), textLeft + room / 2.0f, top, 13.0f, room, 0.95f,
                0.0f, UiTheme.alpha(UiAccent.text(), appear), false);
        firstLines.of(money.subject()).box(graphics, font(), artFirst, textLeft, top + 14.0f, 12.0f, room, 0.85f,
                UiTheme.alpha(UiAccent.color(), appear));
        secondLines.of(money.subject()).box(graphics, font(), artSecond, textLeft, top + 27.0f, 10.0f, room,
                LINE_SCALE, UiTheme.alpha(UiAccent.textDim(), appear));
    }

    // WHY: подписи над колонкой собираются на смене данных или выбранного, а не в кадре
    private void relabelArt(MoneySnapshot shot) {
        Object subject = money.subject();
        if (money.signature() == artFor && subject.equals(artSubject)) return;
        artFor = money.signature();
        artSubject = subject;
        AccountTile tile = money.chosenTile();
        if (tile == null) {
            artTitle = Money.unit(shot.mode(), shot.name(), itemOf(shot.currency()).getDescription());
            artFirst = Component.translatable("sellmod.studio.money.art.settings", shot.total(),
                    MoneyStudio.online(shot), amount(shot, MoneyStudio.held(shot)));
            return;
        }
        MoneySnapshot.Account account = tile.account();
        artFirst = account.known() ? amount(shot, account.balance())
                : Component.translatable("sellmod.studio.money.art.unknown");
        artSecond = account.online() ? Component.translatable("sellmod.studio.money.art.online")
                : Component.translatable("sellmod.studio.money.art.seen",
                WalletEntryRow.ago(System.currentTimeMillis() - account.seen()));
    }

    private static void line(GuiGraphics graphics, Component text, float left, float width, float y, float scale,
                             int color, float appear) {
        UiRender.textTrackedFit(graphics, font(), text, left + width / 2.0f, y, 10.0f, width, scale, 0.0f,
                UiTheme.alpha(color, appear), false);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
