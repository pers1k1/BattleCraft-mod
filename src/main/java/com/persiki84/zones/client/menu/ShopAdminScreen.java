package com.persiki84.zones.client.menu;

import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.GlidingRow;
import com.persiki84.shared.client.menu.ManagerScreen;
import com.persiki84.shared.client.menu.MenuCommands;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.MenuField;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.menu.pick.ItemPickerScreen;
import com.persiki84.shared.gunsmith.GunSmith;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.zones.client.ClientShopData;
import com.persiki84.zones.shop.ShopAccess;
import com.persiki84.zones.shop.ShopEntry;
import com.persiki84.zones.shop.ShopSection;
import com.persiki84.zones.shop.StockScope;
import com.persiki84.shared.menu.MenuKind;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class ShopAdminScreen extends ManagerScreen {
    public static final String MENU_ID = "shop";

    private static final String COMMAND = "battlecraft shop";
    private static final int LIST_WIDTH = 138;
    private static final int COLUMN_GAP = 8;
    private static final int MAX_PRICE = 1000000;
    private static final int MAX_STOCK = 9999;
    private static final int MAX_RESTOCK = 604800;
    private static final int RESTOCK_STEP = 30;
    private static final int DEFAULT_PRICE = 100;
    private static final int MAX_ITEM_ID = 128;
    private static final int MAX_COUNT = 64;
    private static final int CREATE_ROWS = 3 + MenuField.ROW_EQUIVALENT * 2;
    private static final int NOTE_ROWS = 3 + MenuField.ROW_EQUIVALENT;
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_.-]+");

    private static final ShopAccess EMPTY_ACCESS = new ShopAccess();
    private static final StockScope[] SCOPES = StockScope.values();
    private static final List<Component> SCOPE_LABELS = scopeLabels();

    private int tab;
    private int accessTarget;
    private int layoutTarget;
    private int newParent;
    private String sectionId;
    private int newPrice = DEFAULT_PRICE;
    private int newCount = 1;
    private FieldRow itemIdRow;
    private int pickedNote;
    private String noteEntryId;
    private String noteText = "";
    private MenuField noteField;
    private MenuField idField;
    private MenuField titleField;
    private String newSectionId = "";
    private String newSectionTitle = "";
    private int shownSections = -1;
    private String shownLayout = "";

    private static List<Component> scopeLabels() {
        List<Component> labels = new ArrayList<>();
        for (StockScope scope : SCOPES) {
            labels.add(Component.translatable(scope.label()));
        }
        return labels;
    }

    public ShopAdminScreen() {
        super(Component.translatable("zones.shopadmin.title"));
    }

    @Override
    public MenuKind presence() {
        return MenuKind.ADMIN;
    }

    @Override
    protected List<Component> tabs() {
        return List.of(Component.translatable("zones.shopadmin.tab.prices"),
                Component.translatable("zones.shopadmin.tab.stock"),
                Component.translatable("zones.shopadmin.tab.notes"),
                Component.translatable("zones.shopadmin.tab.attachments"),
                Component.translatable("zones.shopadmin.tab.section"),
                Component.translatable("zones.shopadmin.tab.layout"),
                Component.translatable("zones.shopadmin.tab.access"),
                Component.translatable("zones.shopadmin.tab.create"));
    }

    @Override
    protected int activeTab() {
        return tab;
    }

    @Override
    protected void pickTab(int index) {
        tab = index;
        rowScroll = 0;
    }

    @Override
    protected void buildBody() {
        List<ShopSection> sections = ClientShopData.sections();
        if (sectionId == null && !sections.isEmpty()) sectionId = sections.get(0).id();

        shownSections = sections.size();
        shownLayout = layoutSignature();
        noteField = null;
        idField = null;
        titleField = null;
        if (tab == 0) place(priceRows());
        if (tab == 1) place(stockRows());
        if (tab == 2) addNoteRows();
        if (tab == 3) place(attachRows());
        if (tab == 4) place(sectionRows());
        if (tab == 5) place(layoutRows());
        if (tab == 6) place(accessRows());
        if (tab == 7) addCreateRows();
        addSectionList(sections);
    }

    // WHY: перестановка, переименование и правка цены не меняют числа строк, поэтому обновление
    // WHY: экрана по размеру списка их пропускало: подпись ведёт и порядок, и сами значения
    private String layoutSignature() {
        StringBuilder signature = new StringBuilder();
        for (ShopSection section : ClientShopData.sections()) {
            signature.append(section.id()).append('=').append(section.title())
                    .append(section.access().list()).append('/');
        }

        ShopSection section = current();
        if (section == null) return signature.toString();

        for (String childId : section.childIds()) {
            ShopSection child = section.child(childId);
            signature.append(childId).append('=').append(child.title())
                    .append(child.access().list()).append(':');
        }
        for (ShopEntry entry : entries()) {
            appendEntry(signature, entry);
        }
        return signature.toString();
    }

    private static void appendEntry(StringBuilder signature, ShopEntry entry) {
        signature.append(entry.id()).append('|').append(entry.price()).append('|')
                .append(entry.stock()).append('|').append(entry.restockSeconds()).append('|')
                .append(entry.scope().id()).append('|').append(entry.description())
                .append('|').append(entry.access().list()).append(',');
    }

    // WHY: описание правится текстом, а не строкой-переключателем: поле ввода живёт до пересборки
    // WHY: экрана, поэтому набранное отправляется кнопкой, а не каждым нажатием клавиши
    private void addNoteRows() {
        List<ShopEntry> all = entries();
        if (all.isEmpty()) {
            place(List.of(emptyRow()));
            return;
        }

        pickedNote = Math.floorMod(pickedNote, all.size());
        ShopEntry entry = all.get(pickedNote);
        if (!entry.id().equals(noteEntryId)) {
            noteEntryId = entry.id();
            noteText = entry.description() == null ? "" : entry.description();
        }

        int x = rowsLeft();
        int y = contentTop() + PANEL_PAD;
        shownRows = NOTE_ROWS;

        PickRow picker = new PickRow(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.notes.item"), entryLabels(all),
                () -> pickedNote, this::selectNote);
        addRenderableWidget(picker.icon(entry::stack));
        y += ROW_HEIGHT + ROW_GAP;

        noteField = new MenuField(x, y, rowsWidth(), Component.translatable("zones.shopadmin.notes.text"),
                noteText, value -> noteText = value);
        addRenderableWidget(noteField.box());
        y += MenuField.BLOCK_HEIGHT + ROW_GAP;

        addNoteButtons(x, y, entry.id());
    }

    private void addNoteButtons(int x, int y, String entryId) {
        addRenderableWidget(new UiButton(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.notes.apply"), pressed -> applyNote(entryId)));
        y += ROW_HEIGHT + ROW_GAP;

        UiButton clear = new UiButton(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.notes.clear"), pressed -> clearNote(entryId));
        clear.active = !noteText.isBlank();
        addRenderableWidget(clear);
    }

    private void applyNote(String entryId) {
        String text = noteText.trim();
        if (text.isEmpty()) {
            clearNote(entryId);
            return;
        }
        send("item describe " + sectionId + " " + entryId + " " + text);
        MenuFeedback.show(Component.translatable("zones.shopadmin.notes.saved"), false);
    }

    private void clearNote(String entryId) {
        noteText = "";
        send("item describe " + sectionId + " " + entryId);
        rebuild();
    }

    private void selectNote(int picked) {
        pickedNote = picked;
        noteEntryId = null;
        rebuild();
    }

    private void addCreateRows() {
        int x = rowsLeft();
        int y = contentTop() + PANEL_PAD;

        shownRows = CREATE_ROWS;
        idField = new MenuField(x, y, rowsWidth(), Component.translatable("zones.shopadmin.new.id"),
                newSectionId, value -> newSectionId = value);
        addRenderableWidget(idField.box());
        y += MenuField.BLOCK_HEIGHT + ROW_GAP;

        titleField = new MenuField(x, y, rowsWidth(), Component.translatable("zones.shopadmin.new.title"),
                newSectionTitle, value -> newSectionTitle = value);
        addRenderableWidget(titleField.box());
        y += MenuField.BLOCK_HEIGHT + ROW_GAP;

        addCreateButtons(x, y);
    }

    private void addCreateButtons(int x, int y) {
        addRenderableWidget(new UiButton(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.new.section"), pressed -> createSection(false)));
        y += ROW_HEIGHT + ROW_GAP;

        UiButton child = new UiButton(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.new.subsection"), pressed -> createSection(true));
        child.active = current() != null;
        addRenderableWidget(child);
        y += ROW_HEIGHT + ROW_GAP;

        ShopSection section = current();
        ActionRow parent = new ActionRow(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.new.parent"),
                () -> section == null ? Component.translatable("zones.shopadmin.new.no_parent")
                        : Component.literal(section.title()), () -> {});
        parent.active = false;
        addRenderableWidget(parent);
    }

    private void createSection(boolean child) {
        String id = newSectionId.trim().toLowerCase(java.util.Locale.ROOT);
        if (!ID_PATTERN.matcher(id).matches()) {
            MenuFeedback.show(Component.translatable("zones.shopadmin.error.bad_id"), true);
            return;
        }
        if (child && current() == null) {
            MenuFeedback.show(Component.translatable("zones.shopadmin.error.no_parent"), true);
            return;
        }

        String title = newSectionTitle.trim().isEmpty() ? id : newSectionTitle.trim();
        send(child ? "subsection add " + sectionId + " " + id + " " + title : "section add " + id + " " + title);
        newSectionId = "";
        newSectionTitle = "";
        rebuild();
    }

    private void addSectionList(List<ShopSection> sections) {
        int capacity = rowCapacity(contentHeight() - PANEL_PAD * 2, sections.size());
        listScroll = clampList(listScroll, sections.size(), capacity);

        int x = listLeft();
        int y = listWindowTop();
        for (ShopSection section : listWindow(sections)) {
            String id = section.id();
            UiButton button = new UiButton(x, y, LIST_WIDTH, ROW_HEIGHT, Component.literal(section.title()),
                    pressed -> {
                        sectionId = id;
                        rowScroll = 0;
                        rebuild();
                    });
            button.active = !id.equals(sectionId);
            button.anchor(y, GlidingRow.Lane.LIST);
            addRenderableWidget(button);
            y += ROW_HEIGHT + ROW_GAP;
        }
    }

    @Override
    protected int rowsLeft() {
        return contentLeft() + PANEL_PAD + LIST_WIDTH + COLUMN_GAP;
    }

    @Override
    protected int rowsWidth() {
        return CONTENT_WIDTH - PANEL_PAD * 2 - LIST_WIDTH - COLUMN_GAP;
    }

    @Override
    protected int listWidth() {
        return LIST_WIDTH;
    }

    @Override
    protected int desiredRows() {
        return Math.max(shownSections, shownRows);
    }

    private ShopSection current() {
        return sectionId == null ? null : ClientShopData.section(sectionId);
    }

    private List<ShopEntry> entries() {
        ShopSection section = current();
        if (section == null) return List.of();

        List<ShopEntry> all = new ArrayList<>(section.entries().values());
        for (ShopSection child : section.children().values()) {
            all.addAll(child.entries().values());
        }
        return all;
    }

    private List<AbstractWidget> priceRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        for (ShopEntry entry : entries()) {
            rows.add(priceRow(entry));
        }
        if (rows.isEmpty()) rows.add(emptyRow());
        return rows;
    }

    private NumberRow priceRow(ShopEntry entry) {
        String id = entry.id();
        return new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, entry.stack().getHoverName(),
                () -> priceOf(id), value -> send("item price " + sectionId + " " + id + " " + value),
                0, MAX_PRICE, 5);
    }

    private int priceOf(String id) {
        for (ShopEntry entry : entries()) {
            if (entry.id().equals(id)) return entry.price();
        }
        return 0;
    }

    private List<AbstractWidget> stockRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        for (ShopEntry entry : entries()) {
            rows.add(stockRow(entry));
            rows.add(restockRow(entry));
            rows.add(scopeRow(entry));
        }
        if (rows.isEmpty()) rows.add(emptyRow());
        return rows;
    }

    // WHY: у общего склада один остаток на весь сервер, у командного свой у каждой команды,
    // WHY: у личного свой у каждого игрока: без этого лимит в одну штуку держит очередь из всей команды
    private PickRow scopeRow(ShopEntry entry) {
        String id = entry.id();
        PickRow row = new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.scope"), SCOPE_LABELS,
                () -> scopeOf(id).ordinal(),
                picked -> send("item scope " + sectionId + " " + id + " " + SCOPES[picked].id()));
        row.hint("zones.shopadmin.scope.hint");
        return row;
    }

    private StockScope scopeOf(String id) {
        for (ShopEntry entry : entries()) {
            if (entry.id().equals(id)) return entry.scope();
        }
        return StockScope.DEFAULT;
    }

    // WHY: склад хранится связками по размеру покупки, а игрок задаёт лимит штуками, поэтому строка
    // WHY: ведёт счёт в штуках, шаг равен связке, а пол лежит на -связке: оттуда шаг попадает ровно в ноль
    private NumberRow stockRow(ShopEntry entry) {
        String id = entry.id();
        int bundle = entry.bundle();

        NumberRow row = new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, entry.stack().getHoverName(),
                () -> stockItemsOf(id, bundle), value -> send(stockCommand(id, aligned(value, bundle))),
                -bundle, MAX_STOCK, bundle);
        return row.floorLabel(Component.translatable("zones.shopadmin.unlimited"));
    }

    private NumberRow restockRow(ShopEntry entry) {
        String id = entry.id();

        NumberRow row = new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.restock"),
                () -> restockOf(id), value -> send(restockCommand(id, value)),
                0, MAX_RESTOCK, RESTOCK_STEP);
        return row.floorLabel(Component.translatable("zones.shopadmin.no_restock"));
    }

    private String stockCommand(String id, int items) {
        return "item stock " + sectionId + " " + id + " " + items;
    }

    private String restockCommand(String id, int seconds) {
        return "item restock " + sectionId + " " + id + " " + seconds;
    }

    private int restockOf(String id) {
        for (ShopEntry entry : entries()) {
            if (entry.id().equals(id)) return Math.max(0, Math.min(MAX_RESTOCK, entry.restockSeconds()));
        }
        return 0;
    }

    private static int aligned(int value, int bundle) {
        if (value < 0) return ShopEntry.UNLIMITED;
        return Math.max(0, Math.round(value / (float) bundle) * bundle);
    }

    private int stockItemsOf(String id, int bundle) {
        for (ShopEntry entry : entries()) {
            if (!entry.id().equals(id)) continue;
            return entry.limited() ? Math.min(MAX_STOCK, entry.stock() * bundle) : -bundle;
        }
        return -bundle;
    }

    // WHY: обвесы правятся в мастерской, где видно оружие и сами обвесы, а не строками выбора:
    // WHY: здесь только вход в неё на нужном стволе
    private List<AbstractWidget> attachRows() {
        List<ShopEntry> guns = armedEntries();
        if (guns.isEmpty()) return List.of(noGunsRow());

        ShopGunBench bench = new ShopGunBench(sectionId);
        List<AbstractWidget> rows = new ArrayList<>();
        for (ShopEntry entry : guns) {
            String id = entry.id();
            ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, entry.stack().getHoverName(),
                    () -> Component.translatable("gunsmith.open"),
                    () -> com.persiki84.shared.client.menu.gunsmith.GunsmithScreen.open(bench, bench.positionOf(id), this));
            rows.add(row);
        }
        return rows;
    }

    private List<ShopEntry> armedEntries() {
        List<ShopEntry> guns = new ArrayList<>();
        for (ShopEntry entry : entries()) {
            if (GunSmith.isGun(entry.stack())) guns.add(entry);
        }
        return guns;
    }

    private AbstractWidget noGunsRow() {
        return frozenRow("zones.shopadmin.no_guns");
    }

    private AbstractWidget frozenRow(String label) {
        ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(label), Component::empty, () -> {});
        row.active = false;
        return row;
    }

    private static List<Component> entryLabels(List<ShopEntry> shown) {
        List<Component> labels = new ArrayList<>();
        for (ShopEntry entry : shown) {
            labels.add(entry.stack().getHoverName());
        }
        return labels;
    }

    private List<AbstractWidget> accessRows() {
        ShopSection section = current();
        if (section == null) return List.of(emptyRow());

        List<AccessTarget> targets = accessTargets(section);
        accessTarget = Math.floorMod(accessTarget, targets.size());
        AccessTarget target = targets.get(accessTarget);

        PickRow picker = new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.access.target"), targetLabels(targets),
                () -> accessTarget, this::selectTarget);
        picker.hint("zones.shopadmin.access.target" + HINT_SUFFIX);

        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(picker.icon(target::icon));
        addAccessRows(rows, target);
        return rows;
    }

    private void addAccessRows(List<AbstractWidget> rows, AccessTarget target) {
        ToggleRow everyone = new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.access.everyone"),
                () -> live(target).everyone(), value -> showEveryone(target, value));
        everyone.hint("zones.shopadmin.access.everyone" + HINT_SUFFIX);
        rows.add(everyone);

        List<String> teams = teamNames();
        if (teams.isEmpty()) {
            rows.add(reading("zones.shopadmin.access.no_teams", Component::empty));
            return;
        }
        for (String team : teams) {
            rows.add(new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.literal(team),
                    () -> live(target).allows(team),
                    value -> send(target.command() + (value ? " show " : " hide ") + team)));
        }
    }

    private void showEveryone(AccessTarget target, boolean everyone) {
        if (everyone) {
            send(target.command() + " everyone");
            return;
        }

        List<String> teams = teamNames();
        if (teams.isEmpty()) {
            MenuFeedback.show(Component.translatable("zones.shopadmin.access.no_teams"), true);
            return;
        }
        send(target.command() + " show " + teams.get(0));
    }

    private ShopAccess live(AccessTarget target) {
        ShopAccess access = target.access();
        return access == null ? EMPTY_ACCESS : access;
    }

    private void selectTarget(int picked) {
        accessTarget = picked;
        rebuild();
    }

    private List<AccessTarget> accessTargets(ShopSection section) {
        String id = section.id();
        List<AccessTarget> targets = new ArrayList<>();
        targets.add(new AccessTarget(Component.translatable("zones.shopadmin.access.section", section.title()),
                "access section " + id, () -> sectionAccess(id), () -> ItemStack.EMPTY));

        for (String childId : section.childIds()) {
            targets.add(new AccessTarget(Component.translatable("zones.shopadmin.access.subsection",
                    section.child(childId).title()), "access subsection " + id + " " + childId,
                    () -> childAccess(id, childId), () -> ItemStack.EMPTY));
        }
        for (ShopEntry entry : entries()) {
            String entryId = entry.id();
            targets.add(new AccessTarget(entry.stack().getHoverName(),
                    "access item " + id + " " + entryId, () -> entryAccess(entryId), entry::stack));
        }
        return targets;
    }

    private ShopAccess sectionAccess(String id) {
        ShopSection section = ClientShopData.section(id);
        return section == null ? null : section.access();
    }

    private ShopAccess childAccess(String id, String childId) {
        ShopSection section = ClientShopData.section(id);
        ShopSection child = section == null ? null : section.child(childId);
        return child == null ? null : child.access();
    }

    private ShopAccess entryAccess(String entryId) {
        for (ShopEntry entry : entries()) {
            if (entry.id().equals(entryId)) return entry.access();
        }
        return null;
    }

    private static List<Component> targetLabels(List<AccessTarget> targets) {
        List<Component> labels = new ArrayList<>();
        for (AccessTarget target : targets) {
            labels.add(target.label());
        }
        return labels;
    }

    private record AccessTarget(Component label, String command, Supplier<ShopAccess> lookup,
                                Supplier<ItemStack> iconSource) {
        private ShopAccess access() {
            return lookup.get();
        }

        private ItemStack icon() {
            return iconSource.get();
        }
    }

    // WHY: порядок и удаление удобнее править на самой витрине, где видно, как товар выглядит
    // WHY: игроку: список со стрелками остаётся для точных правок
    private AbstractWidget showcaseRow() {
        return new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.showcase"),
                () -> Component.translatable("zones.shopadmin.action.open"),
                ShopScreen::openEditor);
    }

    private List<AbstractWidget> layoutRows() {
        ShopSection section = current();
        if (section == null) return List.of(emptyRow());

        List<LayoutTarget> targets = layoutTargets(section);
        layoutTarget = Math.floorMod(layoutTarget, targets.size());
        LayoutTarget target = targets.get(layoutTarget);

        PickRow picker = new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.layout.target"), layoutLabels(targets),
                () -> layoutTarget, this::selectLayout);
        picker.hint("zones.shopadmin.layout.target" + HINT_SUFFIX);

        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(showcaseRow());
        rows.add(picker.icon(target::icon));
        rows.add(shiftRow("zones.shopadmin.layout.up", target, " up"));
        rows.add(shiftRow("zones.shopadmin.layout.down", target, " down"));
        if (target.moveCommand() != null) rows.add(hostRow(target));
        if (target.entryId() != null) rows.add(homeRow(section, target.entryId()));
        rows.add(dropRow(target));
        return rows;
    }

    // WHY: удаляют обычно подряд, поэтому выбор встаёт на соседа, а не в начало списка: иначе
    // WHY: после каждого удаления приходится листать к тому же месту заново
    private String neighbourSection() {
        List<ShopSection> sections = ClientShopData.sections();
        String previous = null;
        for (ShopSection section : sections) {
            if (section.id().equals(sectionId)) return previous;
            previous = section.id();
        }
        return null;
    }

    private AbstractWidget shiftRow(String label, LayoutTarget target, String direction) {
        return new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(label),
                () -> Component.translatable("zones.shopadmin.action.move"),
                () -> send(target.orderCommand() + direction));
    }

    private AbstractWidget dropRow(LayoutTarget target) {
        return new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(target.removeLabel()),
                () -> Component.translatable("zones.shopadmin.action.delete"), () -> {
            send(target.removeCommand());
            if (target.whole()) sectionId = neighbourSection();
            layoutTarget = Math.max(0, layoutTarget - 1);
            rebuild();
        }).alerting();
    }

    // WHY: раздел выбирается у товара и у отдела: меню подставляло целью переноса текущий раздел,
    // WHY: и увезти товар или отдел к соседям было нечем
    private AbstractWidget hostRow(LayoutTarget target) {
        List<ShopSection> sections = ClientShopData.sections();
        List<Component> labels = new ArrayList<>();
        for (ShopSection candidate : sections) {
            labels.add(Component.literal(candidate.title()));
        }

        PickRow row = new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.layout.host"), labels,
                () -> hostIndex(sections), picked -> relocate(target, sections.get(picked).id()));
        row.hint("zones.shopadmin.layout.host" + HINT_SUFFIX);
        row.active = sections.size() > 1;
        return row;
    }

    private int hostIndex(List<ShopSection> sections) {
        for (int index = 0; index < sections.size(); index++) {
            if (sections.get(index).id().equals(sectionId)) return index;
        }
        return 0;
    }

    private void relocate(LayoutTarget target, String targetId) {
        if (targetId.equals(sectionId)) return;

        send(target.moveCommand() + " " + targetId);
        layoutTarget = 0;
        rebuild();
    }

    // WHY: отдел выбирается у самого товара: положить предмет в отдел командой можно было только
    // WHY: при добавлении, а перенести уже лежащий товар в интерфейсе было нечем
    private AbstractWidget homeRow(ShopSection section, String entryId) {
        List<String> children = section.childIds();
        List<Component> labels = new ArrayList<>();
        labels.add(Component.translatable("zones.shopadmin.layout.section_itself"));
        for (String childId : children) {
            labels.add(Component.literal(section.child(childId).title()));
        }

        return new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.layout.home"), labels,
                () -> homeOf(section, entryId, children),
                picked -> moveEntry(entryId, picked == 0 ? "" : " " + children.get(picked - 1)));
    }

    private void moveEntry(String entryId, String childSuffix) {
        send("item move " + sectionId + " " + entryId + " " + sectionId + childSuffix);
    }

    private static int homeOf(ShopSection section, String entryId, List<String> children) {
        ShopSection owner = section.ownerOf(entryId);
        if (owner == null || owner == section) return 0;
        return Math.max(0, children.indexOf(owner.id()) + 1);
    }

    private void selectLayout(int picked) {
        layoutTarget = picked;
        rebuild();
    }

    private List<LayoutTarget> layoutTargets(ShopSection section) {
        String id = section.id();
        List<LayoutTarget> targets = new ArrayList<>();
        targets.add(new LayoutTarget(Component.translatable("zones.shopadmin.access.section", section.title()),
                "section order " + id, null, "section remove " + id, "zones.shopadmin.remove_section",
                true, null, () -> ItemStack.EMPTY));

        for (String childId : section.childIds()) {
            targets.add(new LayoutTarget(Component.translatable("zones.shopadmin.access.subsection",
                    section.child(childId).title()), "subsection order " + id + " " + childId,
                    "subsection move " + id + " " + childId,
                    "subsection remove " + id + " " + childId, "zones.shopadmin.remove_subsection",
                    false, null, () -> ItemStack.EMPTY));
        }
        for (ShopEntry entry : entries()) {
            String entryId = entry.id();
            targets.add(new LayoutTarget(entry.stack().getHoverName(), "item order " + id + " " + entryId,
                    "item move " + id + " " + entryId, "item remove " + id + " " + entryId,
                    "zones.shopadmin.remove_item", false, entryId, entry::stack));
        }
        return targets;
    }

    private static List<Component> layoutLabels(List<LayoutTarget> targets) {
        List<Component> labels = new ArrayList<>();
        for (LayoutTarget target : targets) {
            labels.add(target.label());
        }
        return labels;
    }

    private record LayoutTarget(Component label, String orderCommand, String moveCommand, String removeCommand,
                                String removeLabel, boolean whole, String entryId,
                                Supplier<ItemStack> iconSource) {
        private ItemStack icon() {
            return iconSource.get();
        }
    }

    private List<AbstractWidget> sectionRows() {
        ShopSection section = current();
        List<String> children = section == null ? List.of() : section.childIds();
        newParent = children.isEmpty() ? 0 : Math.floorMod(newParent, children.size() + 1);

        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.new_price"), () -> newPrice,
                value -> newPrice = value, 0, MAX_PRICE, 5));
        rows.add(parentRow(section, children));
        rows.add(new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.add_pick"),
                () -> Component.translatable("zones.shopadmin.action.add"),
                () -> pickEntry(childSuffix(children))));
        addByIdRows(rows, children);
        rows.add(new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.remove_section"),
                () -> Component.translatable("zones.shopadmin.action.delete"), () -> {
            send("section remove " + sectionId);
            sectionId = neighbourSection();
            rebuild();
        }).alerting());
        return rows;
    }

    // WHY: товар выбирается глазами: из инвентаря со всеми тегами (номером слота, стак берёт
    // WHY: сервер) или из реестра с поиском; цена задаётся тут же, числом
    private void pickEntry(String childSuffix) {
        String section = sectionId;
        ItemPickerScreen.open(Component.translatable("zones.shopadmin.pick.title"), this,
                ItemPickerScreen.Options.counted(Component.translatable("zones.shopadmin.new_price"), 0, MAX_PRICE, newPrice),
                choice -> {
                    newPrice = choice.amount();
                    if (choice.fromInventory()) {
                        send("item slot " + section + " " + choice.slot() + " " + choice.amount() + childSuffix);
                    } else {
                        send("item id " + section + " \"" + choice.itemId() + "\" " + newCount + " " + choice.amount() + childSuffix);
                    }
                });
    }

    private void addByIdRows(List<AbstractWidget> rows, List<String> children) {
        rows.add(itemIdRow());
        rows.add(new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.add_count"), () -> newCount,
                value -> newCount = value, 1, MAX_COUNT, 1));
        rows.add(new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.add_id"),
                () -> Component.translatable("zones.shopadmin.action.add"),
                () -> addById(children)));
    }

    // WHY: строка поля переживает пересборку: новая на каждый кадр стирала бы набранный
    // WHY: идентификатор и уводила бы курсор из поля на первой же смене состояния меню
    private FieldRow itemIdRow() {
        if (itemIdRow == null) {
            itemIdRow = new FieldRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                    Component.translatable("zones.shopadmin.item_id"),
                    Component.translatable("zones.shopadmin.item_id.example"),
                    "", MAX_ITEM_ID, value -> { });
            itemIdRow.hint("zones.shopadmin.item_id" + HINT_SUFFIX);
        }
        itemIdRow.setX(rowsLeft());
        itemIdRow.setWidth(rowsWidth());
        return itemIdRow;
    }

    // WHY: идентификатор уходит в кавычках: двоеточие в minecraft:stone Brigadier не читает
    // WHY: как часть слова и обрывает разбор команды на нём
    private void addById(List<String> children) {
        String item = itemIdRow().value().trim();
        if (item.isEmpty()) {
            MenuFeedback.show(Component.translatable("zones.shopadmin.error.no_item_id"), true);
            return;
        }
        send("item id " + sectionId + " \"" + item + "\" " + newCount + " " + newPrice
                + childSuffix(children));
    }

    private String childSuffix(List<String> children) {
        return newParent == 0 ? "" : " " + children.get(newParent - 1);
    }

    private AbstractWidget parentRow(ShopSection section, List<String> children) {
        List<Component> labels = new ArrayList<>();
        labels.add(Component.translatable("zones.shopadmin.layout.section_itself"));
        for (String childId : children) {
            labels.add(Component.literal(section.child(childId).title()));
        }

        PickRow row = new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.add_to"), labels,
                () -> newParent, picked -> newParent = picked);
        row.hint("zones.shopadmin.add_to" + HINT_SUFFIX);
        row.active = !children.isEmpty();
        return row;
    }

    private AbstractWidget emptyRow() {
        ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.empty"), Component::empty, () -> {});
        row.active = false;
        return row;
    }

    private void send(String tail) {
        MenuCommands.run(COMMAND + " " + tail, MENU_ID);
    }

    @Override
    protected void renderBody(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderListWell(graphics);

        if (tab == 2 || tab == 7) renderFields(graphics);
        Component hint = missing();
        if (hint != null) renderHint(graphics, hint, contentTop() + contentHeight() / 2.0f);
    }

    private Component missing() {
        if (tab == 7) return null;
        if (ClientShopData.sections().isEmpty()) return Component.translatable("zones.shopadmin.no_sections");
        return current() == null ? Component.translatable("zones.shopadmin.pick_section") : null;
    }

    private void renderFields(GuiGraphics graphics) {
        for (MenuField field : new MenuField[]{noteField, idField, titleField}) {
            if (field != null) field.render(graphics);
        }
    }

    @Override
    public void tick() {
        if (typingInRow()) return;

        String signature = layoutSignature();
        if (ClientShopData.sections().size() == shownSections && signature.equals(shownLayout)) return;
        rebuild();
    }

}
