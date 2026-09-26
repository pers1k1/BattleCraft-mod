package com.persiki84.zones.client.menu.studio;

import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.gunsmith.GunSmith;
import com.persiki84.zones.client.menu.ItemTurntable;
import com.persiki84.zones.shop.ShopAccess;
import com.persiki84.zones.shop.ShopEntry;
import com.persiki84.zones.shop.ShopSection;
import com.persiki84.zones.shop.StockScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ShopInspector {
    private static final int ROW = 22;
    private static final int CONTROL = 16;
    private static final int GAP = 4;
    private static final int MAX_PRICE = 1000000;
    private static final int MAX_STOCK = 9999;
    private static final int MAX_RESTOCK = 604800;
    private static final int RESTOCK_STEP = 30;
    private static final int TITLE_LIMIT = 48;
    private static final int ENTRY_ART = 110;
    private static final int NODE_ART = 46;
    private static final float TURNTABLE_HEIGHT = 70.0f;
    private static final float LINE_SCALE = 0.72f;
    private static final String NOTE = "note";
    private static final String TITLE = "title";
    private static final String DROP_ENTRY = "drop-entry";
    private static final String DROP_NODE = "drop-node";
    private static final StockScope[] SCOPES = StockScope.values();
    private static final Component SURE = Component.translatable("studio.sure");
    private static final Component DELETE = Component.translatable("studio.delete");
    private static final Component CREATE = Component.translatable("studio.create");

    private final ShopStudioScreen screen;
    private final ItemTurntable turntable = new ItemTurntable();
    private final ShopAccessRows access;
    private final Map<Integer, NumberRow> stockRows = new HashMap<>();
    private HeadingRow tradeHeading;
    private NumberRow priceRow;
    private NumberRow bundleRow;
    private NumberRow restockRow;
    private PickRow scopeRow;
    private HeadingRow noteHeading;
    private FieldRow noteRow;
    private UiButton gunsButton;
    private UiButton copyButton;
    private UiButton removeButton;
    private HeadingRow sectionHeading;
    private HeadingRow childHeading;
    private FieldRow titleRow;
    private ActionRow childRow;
    private ActionRow dropSectionRow;
    private ActionRow dropChildRow;
    private HeadingRow shelfHeading;
    private NumberRow newPriceRow;
    private NumberRow newCountRow;
    private HeadingRow bulkHeading;
    private NumberRow bulkPriceRow;
    private List<ShopEntry> bulkEntries = List.of();
    private int newPrice = 100;
    private int newCount = 1;
    private boolean filling;
    private String filledFor;
    private boolean focusTitle;
    private boolean shownArmed;

    ShopInspector(ShopStudioScreen screen) {
        this.screen = screen;
        this.access = new ShopAccessRows(10, ROW, screen::now);
        createTradeRows();
        createNoteRows();
        createNodeRows();
        createShelfRows();
        createBulkRows();
    }

    private void createTradeRows() {
        tradeHeading = heading("studio.shop.group.trade");
        priceRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("studio.shop.price_row"),
                () -> entryValue(ShopEntry::price), value -> screen.later("price", entryCommand("price", value)),
                0, MAX_PRICE, 5), "studio.shop.price_row");
        bundleRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("studio.shop.bundle"),
                () -> entryValue(ShopEntry::bundle), value -> screen.later("bundle", entryCommand("count", value)),
                1, ShopEntry.BUNDLE_LIMIT, 1), "studio.shop.bundle");
        restockRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("zones.shopadmin.restock"),
                () -> entryValue(entry -> Math.min(MAX_RESTOCK, entry.restockSeconds())),
                value -> screen.later("restock", entryCommand("restock", value)), 0, MAX_RESTOCK, RESTOCK_STEP)
                .floorLabel(Component.translatable("zones.shopadmin.no_restock")), "studio.shop.restock");
        scopeRow = new PickRow(0, 0, 10, ROW, Component.translatable("zones.shopadmin.scope"), scopeLabels(),
                () -> entryValue(entry -> entry.scope().ordinal()),
                picked -> screen.now(scopeCommand(SCOPES[picked])));
        scopeRow.hint("zones.shopadmin.scope.hint");
    }

    private void createNoteRows() {
        noteHeading = heading("studio.shop.group.note");
        noteRow = new FieldRow(0, 0, 10, ROW, Component.translatable("studio.shop.note"),
                Component.translatable("studio.shop.note.placeholder"), "", ShopEntry.DESCRIPTION_LIMIT, this::noteTyped);
        gunsButton = new UiButton(0, 0, 10, CONTROL, Component.translatable("studio.guns"), pressed -> screen.openGuns())
                .hint("studio.guns.hint");
        copyButton = new UiButton(0, 0, 10, CONTROL, Component.translatable("studio.copy"), pressed -> screen.copyEntry())
                .hint("studio.copy.hint");
        removeButton = new UiButton(0, 0, 10, CONTROL, DELETE, pressed -> pressRemove());
    }

    private void createNodeRows() {
        sectionHeading = heading("studio.shop.group.section");
        childHeading = heading("studio.shop.group.child");
        titleRow = new FieldRow(0, 0, 10, ROW, Component.translatable("studio.shop.title_row"),
                Component.translatable("studio.shop.title_row.placeholder"), "", TITLE_LIMIT, this::titleTyped);
        childRow = hinted(new ActionRow(0, 0, 10, ROW, Component.translatable("studio.shop.new_child"),
                () -> CREATE, screen::createChild), "studio.shop.new_child");
        dropSectionRow = new ActionRow(0, 0, 10, ROW, Component.translatable("zones.shopadmin.remove_section"),
                () -> screen.isArmed(DROP_NODE) ? SURE : DELETE, this::pressDropNode).alerting();
        dropChildRow = new ActionRow(0, 0, 10, ROW, Component.translatable("zones.shopadmin.remove_subsection"),
                () -> screen.isArmed(DROP_NODE) ? SURE : DELETE, this::pressDropNode).alerting();
    }

    private void createBulkRows() {
        bulkHeading = heading("studio.bulk.group");
        bulkPriceRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("studio.bulk.price"),
                () -> bulkEntries.isEmpty() ? 0 : bulkEntries.get(0).price(),
                value -> screen.bulkPrice(bulkEntries, value), 0, MAX_PRICE, 5), "studio.bulk.price");
    }

    private void createShelfRows() {
        shelfHeading = heading("studio.shop.group.new");
        newPriceRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("zones.shopadmin.new_price"),
                () -> newPrice, value -> newPrice = value, 0, MAX_PRICE, 5), "studio.shop.new_price");
        newCountRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("studio.shop.new_count"),
                () -> newCount, value -> newCount = value, 1, ShopEntry.BUNDLE_LIMIT, 1), "studio.shop.new_count");
    }

    private static HeadingRow heading(String key) {
        return new HeadingRow(0, 0, 10, ROW, Component.translatable(key));
    }

    private static <T extends com.persiki84.shared.client.menu.MenuRow> T hinted(T row, String key) {
        row.hint(key + ".hint");
        return row;
    }

    private static List<Component> scopeLabels() {
        List<Component> labels = new ArrayList<>();
        for (StockScope scope : SCOPES) {
            labels.add(Component.translatable(scope.label()));
        }
        return labels;
    }

    int newPrice() {
        return newPrice;
    }

    int newCount() {
        return newCount;
    }

    void rest() {
        turntable.rest();
    }

    void focusTitle() {
        focusTitle = true;
    }

    private int entryValue(java.util.function.ToIntFunction<ShopEntry> read) {
        ShopEntry entry = screen.entry();
        return entry == null ? 0 : read.applyAsInt(entry);
    }

    private String entryCommand(String verb, int value) {
        ShopNode node = screen.node();
        ShopEntry entry = screen.entry();
        if (node == null || entry == null) return "";
        return ShopStudioScreen.COMMAND + "item " + verb + " " + node.section() + " " + entry.id() + " " + value;
    }

    private String scopeCommand(StockScope scope) {
        ShopNode node = screen.node();
        ShopEntry entry = screen.entry();
        if (node == null || entry == null) return "";
        return ShopStudioScreen.COMMAND + "item scope " + node.section() + " " + entry.id() + " " + scope.id();
    }

    private NumberRow stockRow(int bundle) {
        return stockRows.computeIfAbsent(bundle, size -> hinted(new NumberRow(0, 0, 10, ROW,
                Component.translatable("studio.shop.stock"), () -> stockItems(size),
                value -> screen.later("stock", entryCommand("stock", aligned(value, size))), -size, MAX_STOCK, size)
                .floorLabel(Component.translatable("zones.shopadmin.unlimited")), "studio.shop.stock"));
    }

    // WHY: склад хранится связками по размеру покупки, а админ задаёт лимит штуками, поэтому шаг
    // WHY: равен связке, а пол лежит на -связке: оттуда шаг попадает ровно в ноль
    private int stockItems(int bundle) {
        ShopEntry entry = screen.entry();
        if (entry == null || !entry.limited()) return -bundle;
        return Math.min(MAX_STOCK, entry.stock() * bundle);
    }

    private static int aligned(int value, int bundle) {
        if (value < 0) return ShopEntry.UNLIMITED;
        return Math.max(0, Math.round(value / (float) bundle) * bundle);
    }

    private void noteTyped(String text) {
        if (filling) return;
        ShopNode node = screen.node();
        ShopEntry entry = screen.entry();
        if (node == null || entry == null) return;
        String base = ShopStudioScreen.COMMAND + "item describe " + node.section() + " " + entry.id();
        screen.later(NOTE, text.isBlank() ? base : base + " " + text.trim());
    }

    private void titleTyped(String text) {
        ShopNode node = screen.node();
        if (filling || node == null || text.isBlank()) return;
        String command = node.top() ? "section rename " + node.section()
                : "subsection rename " + node.section() + " " + node.child();
        screen.later(TITLE, ShopStudioScreen.COMMAND + command + " " + text.trim());
    }

    private void pressRemove() {
        if (screen.confirmed(DROP_ENTRY)) {
            screen.removeEntry();
            return;
        }
        tick();
    }

    private void pressDropNode() {
        if (screen.confirmed(DROP_NODE)) screen.removeNode();
    }

    void tick() {
        boolean armed = screen.isArmed(DROP_ENTRY);
        if (armed == shownArmed) return;
        shownArmed = armed;
        removeButton.setMessage(armed ? SURE : DELETE);
    }

    void entry(StudioStack stack, int x, int width) {
        ShopEntry entry = screen.entry();
        fill(false);
        stack.add(sized(tradeHeading, width), x);
        stack.add(sized(priceRow, width), x);
        stack.add(sized(bundleRow, width), x);
        stack.add(sized(stockRow(entry.bundle()), width), x);
        stack.add(sized(restockRow, width), x);
        stack.add(sized(scopeRow, width), x);
        stack.add(sized(noteHeading, width), x, GAP);
        stack.add(sized(noteRow, width), x);
        access.target(accessCommand(), this::entryAccess);
        access.place(stack, x, width);
        placeButtons(stack, x, width, GunSmith.isGun(entry.stack()));
    }

    private void placeButtons(StudioStack stack, int x, int width, boolean gun) {
        int third = (width - GAP * 2) / 3;
        gunsButton.active = gun;
        stack.add(sized(gunsButton, third), x, GAP * 2);
        stack.beside(sized(copyButton, third), x + third + GAP);
        stack.beside(sized(removeButton, width - third * 2 - GAP * 2), x + (third + GAP) * 2);
    }

    private String accessCommand() {
        ShopNode node = screen.node();
        ShopEntry entry = screen.entry();
        if (node == null) return "";
        if (entry != null) return ShopStudioScreen.COMMAND + "access item " + node.section() + " " + entry.id();
        if (node.top()) return ShopStudioScreen.COMMAND + "access section " + node.section();
        return ShopStudioScreen.COMMAND + "access subsection " + node.section() + " " + node.child();
    }

    private ShopAccess entryAccess() {
        ShopEntry entry = screen.entry();
        return entry == null ? null : entry.access();
    }

    private ShopAccess nodeAccess() {
        ShopNode node = screen.node();
        ShopSection owner = node == null ? null : node.owner();
        return owner == null ? null : owner.access();
    }

    void node(StudioStack stack, int x, int width) {
        ShopNode node = screen.node();
        fill(false);
        stack.add(sized(node.top() ? sectionHeading : childHeading, width), x);
        stack.add(sized(titleRow, width), x);
        access.target(accessCommand(), this::nodeAccess);
        access.place(stack, x, width);
        if (node.top()) stack.add(sized(childRow, width), x, GAP * 2);
        stack.add(sized(node.top() ? dropSectionRow : dropChildRow, width), x, node.top() ? 0 : GAP * 2);
        if (focusTitle && node.owner() != null) takeTitleFocus();
    }

    private void takeTitleFocus() {
        focusTitle = false;
        screen.setFocused(titleRow);
        titleRow.box().moveCursorToEnd();
        titleRow.box().setHighlightPos(0);
    }

    // WHY: общая цена показывает цену первой из выбранных и ставит её всем: так видно, от чего
    // WHY: отталкиваются стрелки, а разные цены группы выравниваются одним числом
    void bulk(StudioStack stack, int x, int width, List<ShopEntry> entries) {
        bulkEntries = entries;
        stack.add(sized(bulkHeading, width), x);
        stack.add(sized(bulkPriceRow, width), x);
    }

    void shelf(StudioStack stack, int x, int width) {
        stack.add(sized(shelfHeading, width), x);
        stack.add(sized(newPriceRow, width), x);
        stack.add(sized(newCountRow, width), x);
        stack.skip(GAP);
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    void fill(boolean force) {
        String subject = String.valueOf(screen.subject());
        boolean fresh = force || !subject.equals(filledFor);
        filledFor = subject;
        filling = true;
        try {
            ShopEntry entry = screen.entry();
            if (entry != null && !noteRow.capturing() && (fresh || !screen.pending(NOTE))) {
                set(noteRow, entry.description() == null ? "" : entry.description());
            }
            ShopNode node = screen.node();
            ShopSection owner = node == null ? null : node.owner();
            if (owner != null && !titleRow.capturing() && (fresh || !screen.pending(TITLE))) set(titleRow, owner.title());
        } finally {
            filling = false;
        }
    }

    private static void set(FieldRow row, String value) {
        if (!row.value().equals(value)) row.box().setValue(value);
    }

    int artHeight() {
        if (screen.entry() != null) return ENTRY_ART;
        return screen.node() != null ? NODE_ART : 0;
    }

    void renderArt(GuiGraphics graphics, float left, float top, float width, float appear) {
        ShopEntry entry = screen.entry();
        if (entry != null) {
            renderEntryArt(graphics, entry, left, top, width, appear);
            return;
        }
        ShopNode node = screen.node();
        ShopSection owner = node == null ? null : node.owner();
        if (owner != null) renderNodeArt(graphics, node, owner, left, top, width, appear);
    }

    private void renderEntryArt(GuiGraphics graphics, ShopEntry entry, float left, float top, float width,
                                float appear) {
        ItemStack stack = entry.stack();
        turntable.render(graphics, stack, left, top, width, TURNTABLE_HEIGHT);
        float y = top + TURNTABLE_HEIGHT + 4.0f;
        UiRender.textTrackedFit(graphics, font(), stack.getHoverName(), left + width / 2.0f, y, 12.0f, width,
                0.85f, 0.0f, UiTheme.alpha(UiAccent.text(), appear), false);
        line(graphics, Component.literal(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()),
                left, width, y + 15.0f, appear);
        line(graphics, stockLine(entry), left, width, y + 26.0f, appear);
    }

    private static Component stockLine(ShopEntry entry) {
        if (!entry.limited()) return Component.translatable("studio.shop.art.unlimited");
        if (entry.soldOut()) return Component.translatable("studio.shop.art.sold_out");
        return Component.translatable("studio.shop.art.stock", Math.max(0, entry.available()) * entry.bundle(),
                entry.stock() * entry.bundle());
    }

    private void renderNodeArt(GuiGraphics graphics, ShopNode node, ShopSection owner, float left, float top,
                               float width, float appear) {
        UiRender.textTrackedFit(graphics, font(), Component.literal(owner.title()), left + width / 2.0f, top, 14.0f,
                width, 0.95f, 0.0f, UiTheme.alpha(UiAccent.text(), appear), false);
        Component counts = node.top()
                ? Component.translatable("studio.shop.art.section", owner.deepEntryIds().size(), owner.children().size())
                : Component.translatable("studio.shop.art.child", owner.entries().size());
        line(graphics, counts, left, width, top + 16.0f, appear);
        line(graphics, accessLine(owner.access()), left, width, top + 27.0f, appear);
    }

    private static Component accessLine(ShopAccess access) {
        if (access.everyone()) return Component.translatable("studio.shop.art.everyone");
        return Component.translatable("studio.shop.art.teams", String.join(", ", access.list()));
    }

    private void line(GuiGraphics graphics, Component text, float left, float width, float y, float appear) {
        UiRender.textTrackedFit(graphics, font(), text, left + width / 2.0f, y, 10.0f, width, LINE_SCALE, 0.0f,
                UiTheme.alpha(UiAccent.textDim(), appear), false);
    }

    boolean pressArt(double mouseX, double mouseY) {
        if (screen.entry() == null || !turntable.over(mouseX, mouseY)) return false;
        turntable.beginDrag();
        return true;
    }

    boolean dragArt(double dragX, double dragY) {
        if (!turntable.dragging()) return false;
        turntable.drag(dragX, dragY);
        return true;
    }

    void releaseArt() {
        turntable.endDrag();
    }

    boolean scrollArt(double mouseX, double mouseY, double amount) {
        if (screen.entry() == null || !turntable.over(mouseX, mouseY)) return false;
        turntable.magnify(amount);
        return true;
    }

    private static net.minecraft.client.gui.Font font() {
        return Minecraft.getInstance().font;
    }
}
