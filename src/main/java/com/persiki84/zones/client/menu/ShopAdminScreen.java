package com.persiki84.zones.client.menu;

import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.GlidingRow;
import com.persiki84.shared.client.menu.ManagerScreen;
import com.persiki84.shared.client.menu.MenuCommands;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.MenuField;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.gunsmith.GunSlot;
import com.persiki84.shared.gunsmith.GunSmith;
import com.persiki84.shared.gunsmith.GunStat;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.zones.client.ClientShopData;
import com.persiki84.zones.shop.ShopAccess;
import com.persiki84.zones.shop.ShopEntry;
import com.persiki84.zones.shop.ShopSection;
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
    private static final int CREATE_ROWS = 3 + MenuField.ROW_EQUIVALENT * 2;
    private static final int NOTE_ROWS = 3 + MenuField.ROW_EQUIVALENT;
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_.-]+");

    private static final ShopAccess EMPTY_ACCESS = new ShopAccess();

    private int tab;
    private int accessTarget;
    private String sectionId;
    private int newPrice = DEFAULT_PRICE;
    private int pickedGun;
    private int pickedNote;
    private String noteEntryId;
    private String noteText = "";
    private MenuField noteField;
    private MenuField idField;
    private MenuField titleField;
    private String newSectionId = "";
    private String newSectionTitle = "";
    private int shownSections = -1;
    private int shownEntries = -1;

    public ShopAdminScreen() {
        super(Component.translatable("zones.shopadmin.title"));
    }

    @Override
    protected List<Component> tabs() {
        return List.of(Component.translatable("zones.shopadmin.tab.prices"),
                Component.translatable("zones.shopadmin.tab.stock"),
                Component.translatable("zones.shopadmin.tab.notes"),
                Component.translatable("zones.shopadmin.tab.attachments"),
                Component.translatable("zones.shopadmin.tab.section"),
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
        shownEntries = entries().size();
        noteField = null;
        idField = null;
        titleField = null;
        if (tab == 0) place(priceRows());
        if (tab == 1) place(stockRows());
        if (tab == 2) addNoteRows();
        if (tab == 3) place(attachRows());
        if (tab == 4) place(sectionRows());
        if (tab == 5) place(accessRows());
        if (tab == 6) addCreateRows();
        addSectionList(sections);
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
        }
        if (rows.isEmpty()) rows.add(emptyRow());
        return rows;
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

    private List<AbstractWidget> attachRows() {
        List<ShopEntry> guns = armedEntries();
        if (guns.isEmpty()) return List.of(noGunsRow());

        pickedGun = Math.floorMod(pickedGun, guns.size());
        ShopEntry entry = guns.get(pickedGun);

        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.gun"), entryLabels(guns),
                () -> pickedGun, this::selectGun).icon(entry::stack));
        addSlotRows(rows, entry);
        addStatRows(rows, entry);
        return rows;
    }

    private void addStatRows(List<AbstractWidget> rows, ShopEntry entry) {
        for (GunStat stat : GunSmith.stats(entry.stack())) {
            ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                    Component.translatable(stat.label()), () -> Component.literal(stat.value()), () -> {});
            row.active = false;
            rows.add(row);
        }
    }

    private void selectGun(int picked) {
        pickedGun = picked;
        rebuild();
    }

    private void addSlotRows(List<AbstractWidget> rows, ShopEntry entry) {
        List<GunSlot> slots = GunSmith.slots(entry.stack());
        if (slots.isEmpty()) {
            rows.add(noAttachmentsRow());
            return;
        }
        for (GunSlot slot : slots) {
            rows.add(slotRow(entry, slot));
            if (!slot.installed().isEmpty()) rows.add(notesRow(entry, slot));
        }
    }

    private PickRow slotRow(ShopEntry entry, GunSlot slot) {
        List<String> values = new ArrayList<>();
        List<Component> labels = new ArrayList<>();
        values.add("");
        labels.add(Component.translatable("zones.shopadmin.slot_empty"));

        for (GunSlot.GunOption option : slot.options()) {
            values.add(option.value());
            labels.add(Component.literal(option.label()));
        }

        String id = entry.id();
        return new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(slot.label()),
                labels, () -> Math.max(0, values.indexOf(installedIn(id, slot.id()))),
                picked -> applySlot(entry, slot, values.get(picked)))
                .icon(() -> GunSmith.preview(installedIn(id, slot.id())));
    }

    private AbstractWidget notesRow(ShopEntry entry, GunSlot slot) {
        String id = entry.id();
        ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.effect"),
                () -> GunSmith.notesLabel(installedIn(id, slot.id())), () -> {});
        row.active = false;
        return row;
    }

    private String installedIn(String entryId, String slotId) {
        for (ShopEntry entry : entries()) {
            if (!entry.id().equals(entryId)) continue;

            for (GunSlot slot : GunSmith.slots(entry.stack())) {
                if (slot.id().equals(slotId)) return slot.installed();
            }
        }
        return "";
    }

    private void applySlot(ShopEntry entry, GunSlot slot, String value) {
        if (value.isEmpty()) {
            send("item detach " + sectionId + " " + entry.id() + " " + slot.id());
            return;
        }
        send("item attach " + sectionId + " " + entry.id() + " \"" + value + "\"");
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

    private AbstractWidget noAttachmentsRow() {
        return frozenRow("zones.shopadmin.no_attachments");
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

    private List<AbstractWidget> sectionRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.new_price"), () -> newPrice,
                value -> newPrice = value, 0, MAX_PRICE, 5));
        rows.add(new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.add_hand"),
                () -> Component.translatable("zones.shopadmin.action.add"),
                () -> send("item hand " + sectionId + " " + newPrice)));
        rows.add(new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.shopadmin.remove_section"),
                () -> Component.translatable("zones.shopadmin.action.delete"), () -> {
            send("section remove " + sectionId);
            sectionId = null;
            rebuild();
        }).alerting());
        return rows;
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

        if (tab == 2 || tab == 6) renderFields(graphics);
        Component hint = missing();
        if (hint != null) renderHint(graphics, hint, contentTop() + contentHeight() / 2.0f);
    }

    private Component missing() {
        if (tab == 6) return null;
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
        if (ClientShopData.sections().size() != shownSections || entries().size() != shownEntries) rebuild();
    }

}
