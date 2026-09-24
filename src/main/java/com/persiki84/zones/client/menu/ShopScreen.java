package com.persiki84.zones.client.menu;

import com.persiki84.sellmod.client.ClientSellData;
import com.persiki84.shared.client.menu.GlassScreen;
import com.persiki84.shared.client.menu.MenuCommands;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.menu.GlidingRow;
import com.persiki84.shared.client.menu.ScrollHint;
import com.persiki84.shared.client.menu.ScrollLanes;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSlider;
import com.persiki84.shared.client.ui.UiTitle;
import com.persiki84.zones.client.ClientShopData;
import com.persiki84.zones.network.PacketHandler;
import com.persiki84.zones.network.ShopPurchasePacket;
import com.persiki84.zones.network.ShopRefreshPacket;
import com.persiki84.zones.network.ShopSellPacket;
import com.persiki84.zones.shop.ShopEntry;
import com.persiki84.zones.shop.ShopSection;
import com.persiki84.shared.menu.MenuKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ShopScreen extends GlassScreen {
    private static final float ENTER_SECONDS = 0.85f;
    private static final int SIDEBAR_WIDTH = 128;
    private static final int GRID_WIDTH = 322;
    private static final int PREVIEW_WIDTH = 214;
    private static final int PANEL_GAP = 8;
    private static final int HEADER_HEIGHT = 76;
    private static final int PANEL_HEIGHT_LIMIT = 320;
    private static final float PANEL_RADIUS = 9.0f;

    private static final int SEARCH_WIDTH = 152;
    private static final int BALANCE_WIDTH = 116;
    private static final int SELL_WIDTH = 146;
    private static final int HEADER_ROW_HEIGHT = 20;
    private static final int HEADER_ROW_GAP = 8;
    private static final int SEARCH_LIMIT = 32;

    private static final int ROW_HEIGHT = 20;
    private static final int TAB_HEIGHT = 17;
    private static final int TAB_WIDTH = 68;
    private static final int BUY_HEIGHT = 22;
    private static final int QUANTITY_HEIGHT = 16;
    private static final int MAX_ITEMS_PER_PURCHASE = 64;

    private static final int TITLE_TOP = 12;
    private static final float FRAME_MARGIN = 8.0f;
    private static final float TITLE_SCALE = 1.0f;
    private static final float LINE_SCALE = 0.75f;
    private static final float BALANCE_SCALE = 1.0f;
    private static final float FIT_MARGIN = 10.0f;
    private static final float GLIDE_SPEED = 19.0f;

    private final Smooth sectionGlide = new Smooth(0.0f, GLIDE_SPEED);
    private final ScrollLanes lanes = new ScrollLanes();
    private final ScrollHint sectionsAbove = new ScrollHint();
    private final ScrollHint sectionsBelow = new ScrollHint();
    private final ScrollHint factsAbove = new ScrollHint();
    private final ScrollHint factsBelow = new ScrollHint();
    private final List<UiButton> sectionButtons = new ArrayList<>();
    private final ShopView view = new ShopView();
    private final ShopGrid grid = new ShopGrid();
    private final ShopPreview preview = new ShopPreview();

    private EditBox search;
    private UiButton buyButton;
    private String sectionId;
    private String childId;
    private String entryId;
    private int sectionScroll;
    private int quantity = 1;
    private int shownPending = -1;
    private int shownBalance = -1;
    private int shownAvailable = Integer.MIN_VALUE;
    private boolean refreshed;
    private boolean editing;
    private boolean carrying;

    public ShopScreen() {
        super(Component.translatable("zones.shop.title"));
    }

    @Override
    public MenuKind presence() {
        return MenuKind.SHOP;
    }

    @Override
    protected void init() {
        if (!refreshed) {
            refreshed = true;
            PacketHandler.INSTANCE.sendToServer(new ShopRefreshPacket());
        }

        List<ShopSection> sections = ClientShopData.offered();
        if (sectionId == null && !sections.isEmpty()) sectionId = sections.get(0).id();

        if (search == null) search = createSearch();
        view.browse(sectionId, childId);
        rebuild();
        setInitialFocus(search);
    }

    private EditBox createSearch() {
        EditBox box = new EditBox(this.font, 0, 0, SEARCH_WIDTH, HEADER_ROW_HEIGHT,
                Component.translatable("zones.shop.search"));
        box.setBordered(false);
        box.setMaxLength(SEARCH_LIMIT);
        box.setHint(Component.translatable("zones.shop.search"));
        box.setTextColor(UiAccent.text());
        box.setResponder(this::applySearch);
        return box;
    }

    private void applySearch(String text) {
        view.search(text);
        grid.reset();
        if (view.find(entryId) == null) entryId = null;
        rebuild();
    }

    private int contentWidth() {
        return SIDEBAR_WIDTH + GRID_WIDTH + PREVIEW_WIDTH + PANEL_GAP * 2;
    }

    private int contentLeft() {
        return (this.width - contentWidth()) / 2;
    }

    @Override
    public boolean broadcast() {
        return !editing;
    }

    @Override
    protected Area frameArea() {
        float top = TITLE_TOP - FRAME_MARGIN;
        return new Area(contentLeft() - FRAME_MARGIN, top, contentWidth() + FRAME_MARGIN * 2.0f,
                contentTop() + panelHeight() + FRAME_MARGIN - top);
    }

    private int contentTop() {
        return Math.max(HEADER_HEIGHT, (this.height - panelHeight()) / 2);
    }

    private int panelHeight() {
        return Math.min(this.height - HEADER_HEIGHT - PANEL_GAP * 2, PANEL_HEIGHT_LIMIT);
    }

    private void rebuild() {
        boolean typing = search != null && search.isFocused();
        clearWidgets();

        int left = contentLeft();
        int top = contentTop();

        placeSearch();
        addSellButton();
        addSectionButtons(left, top);
        addTabButtons(left + SIDEBAR_WIDTH + PANEL_GAP, top);
        addBuyButton(left + SIDEBAR_WIDTH + GRID_WIDTH + PANEL_GAP * 2, top);

        if (typing) setFocused(search);
    }

    private void placeSearch() {
        search.setX(headerRowLeft() + (int) UiMetrics.PAD);
        search.setY(headerRowTop());
        search.setWidth(SEARCH_WIDTH - (int) UiMetrics.PAD * 2);
        addRenderableWidget(search);
    }

    // WHY: снимок значений записывается и в редакторе, хотя кнопки продажи там нет: тик сверяет
    // WHY: его с живыми числами, и без записи экран пересобирался каждый тик - кнопка моргала
    private void addSellButton() {
        shownPending = pendingValue();
        shownBalance = balanceValue();
        shownAvailable = availableValue();
        if (editing) {
            addViewerButton();
            return;
        }

        UiButton sell = new UiButton(sellLeft(), headerRowTop(), SELL_WIDTH, HEADER_ROW_HEIGHT,
                sellLabel(), pressed -> sellAll());
        sell.hint("zones.shop.sell.hint");
        sell.active = shownPending > 0;
        addRenderableWidget(sell);
    }

    // WHY: кнопка стоит на месте продажи: в редакторе продавать нечего, а смотреть витрину
    // WHY: глазами команды нужно там же, где она показана
    private void addViewerButton() {
        UiButton viewer = new UiButton(sellLeft(), headerRowTop(), SELL_WIDTH, HEADER_ROW_HEIGHT,
                viewerLabel(), pressed -> nextViewer());
        viewer.hint("zones.shop.edit.viewer.hint");
        addRenderableWidget(viewer);
    }

    private Component viewerLabel() {
        String team = view.viewedTeam();
        return team == null
                ? Component.translatable("zones.shop.edit.viewer.all")
                : Component.translatable("zones.shop.edit.viewer", team);
    }

    private void nextViewer() {
        List<String> teams = teamNames();
        String team = view.viewedTeam();
        int index = team == null ? 0 : teams.indexOf(team) + 1;

        view.asTeam(index >= teams.size() ? null : teams.get(index));
        sectionId = null;
        childId = null;
        entryId = null;
        grid.reset();
        rebuild();
    }

    private static List<String> teamNames() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return List.of();
        return new ArrayList<>(minecraft.level.getScoreboard().getTeamNames());
    }

    private int headerRowWidth() {
        return SEARCH_WIDTH + BALANCE_WIDTH + SELL_WIDTH + HEADER_ROW_GAP * 2;
    }

    private int headerRowLeft() {
        return (this.width - headerRowWidth()) / 2;
    }

    private int headerRowTop() {
        return contentTop() - HEADER_ROW_HEIGHT - PANEL_GAP;
    }

    private int balanceLeft() {
        return headerRowLeft() + SEARCH_WIDTH + HEADER_ROW_GAP;
    }

    private int sellLeft() {
        return balanceLeft() + BALANCE_WIDTH + HEADER_ROW_GAP;
    }

    @Override
    public void tick() {
        if (pendingValue() != shownPending || balanceValue() != shownBalance
                || availableValue() != shownAvailable) {
            rebuild();
        }
    }

    private int availableValue() {
        ShopEntry entry = currentEntry();
        return entry == null ? Integer.MIN_VALUE : entry.available();
    }

    private Component sellLabel() {
        int pending = pendingValue();
        if (pending <= 0) return Component.translatable("zones.shop.sell.empty");
        return Component.translatable("zones.shop.sell", pending);
    }

    private int pendingValue() {
        if (minecraft == null || minecraft.player == null) return 0;
        return ClientSellData.pending(minecraft.player);
    }

    private void sellAll() {
        PacketHandler.INSTANCE.sendToServer(new ShopSellPacket());
    }

    private int sectionCapacity() {
        int height = panelHeight() - (int) UiMetrics.PAD_WIDE * 2;
        int plain = Math.max(1, height / ROW_HEIGHT);
        if (ClientShopData.offered().size() <= plain) return plain;

        return Math.max(1, (height - ScrollHint.BAND_TOP - ScrollHint.BAND_BOTTOM) / ROW_HEIGHT);
    }

    private boolean sectionsScrollable() {
        return ClientShopData.offered().size() > sectionCapacity();
    }

    private void addSectionButtons(int left, int top) {
        List<ShopSection> sections = ClientShopData.offered();
        int capacity = sectionCapacity();
        sectionScroll = clamp(sectionScroll, sections.size(), capacity);
        sectionButtons.clear();

        int y = sectionsTop(top) - lead(sectionScroll) * ROW_HEIGHT;
        for (ShopSection section : around(sections, sectionScroll, capacity)) {
            addSectionButton(section, left, y);
            y += ROW_HEIGHT;
        }
    }

    private void addSectionButton(ShopSection section, int left, int y) {
        String id = section.id();
        UiButton button = new UiButton(left + (int) UiMetrics.GAP, y,
                SIDEBAR_WIDTH - (int) UiMetrics.GAP * 2, ROW_HEIGHT - 2,
                sectionLabel(section), pressed -> openSection(id)).lit();
        button.active = view.searching() || !id.equals(sectionId);
        button.anchor(y, GlidingRow.Lane.LIST);
        sectionButtons.add(button);
        addRenderableWidget(button);
    }

    private static Component sectionLabel(ShopSection section) {
        return Component.literal(section.title() + "  " + offeredCount(section));
    }

    private static boolean hasOfferedChildren(ShopSection section) {
        return !section.visibleChildren(ClientShopData.team(), false).isEmpty();
    }

    private static int offeredCount(ShopSection section) {
        String team = ClientShopData.team();
        int total = section.visibleEntries(team, false).size();
        for (ShopSection child : section.visibleChildren(team, false)) {
            total += child.visibleEntries(team, false).size();
        }
        return total;
    }

    private void openSection(String id) {
        int direction = stepTo(ClientShopData.offered().stream().map(ShopSection::id).toList(), sectionId, id);
        sectionId = id;
        childId = null;
        entryId = null;
        clearSearch();
        grid.reset(direction);
        view.browse(sectionId, null);
        preview.rest();
        rebuild();
    }

    private static int stepTo(List<String> order, String from, String to) {
        int was = order.indexOf(from);
        int now = order.indexOf(to);
        if (was < 0 || now < 0 || was == now) return 1;
        return now > was ? 1 : -1;
    }

    private List<String> childOrder(ShopSection section) {
        List<String> order = new ArrayList<>();
        order.add(null);
        for (ShopSection child : section.visibleChildren(ClientShopData.team(), false)) {
            order.add(child.id());
        }
        return order;
    }

    private void clearSearch() {
        if (search.getValue().isEmpty()) return;
        search.setValue("");
    }

    private void addTabButtons(int gridLeft, int top) {
        ShopSection section = currentSection();
        if (view.searching() || section == null || !hasOfferedChildren(section)) return;

        int x = gridLeft + (int) UiMetrics.GAP;
        addTab(x, top, Component.translatable("zones.shop.tab.all"), null);
        x += TAB_WIDTH + (int) UiMetrics.GAP_TIGHT;

        for (ShopSection child : section.visibleChildren(ClientShopData.team(), false)) {
            addTab(x, top, Component.literal(child.title()), child.id());
            x += TAB_WIDTH + (int) UiMetrics.GAP_TIGHT;
        }
    }

    private void addTab(int x, int top, Component label, String id) {
        UiButton tab = new UiButton(x, top + (int) UiMetrics.GAP, TAB_WIDTH, TAB_HEIGHT, label, pressed -> {
            ShopSection section = currentSection();
            int direction = section == null ? 1 : stepTo(childOrder(section), childId, id);
            childId = id;
            entryId = null;
            grid.reset(direction);
            view.browse(sectionId, id);
            preview.rest();
            rebuild();
        }).lit();
        tab.active = !Objects.equals(id, childId);
        addRenderableWidget(tab);
    }

    private void addBuyButton(int previewLeft, int top) {
        ShopEntry entry = currentEntry();
        if (entry == null) return;
        if (editing) {
            addEditButton(entry, previewLeft, top);
            return;
        }

        int affordable = maxAffordable(entry);
        quantity = Math.max(1, Math.min(quantity, Math.max(1, affordable)));

        int width = PREVIEW_WIDTH - (int) UiMetrics.PAD_WIDE * 2;
        int x = previewLeft + (int) UiMetrics.PAD_WIDE;
        int buttonY = top + panelHeight() - BUY_HEIGHT - (int) UiMetrics.PAD_WIDE;

        if (sliderShown(entry)) {
            addRenderableWidget(quantitySlider(x, buttonY - QUANTITY_HEIGHT - (int) UiMetrics.GAP,
                    width, affordable));
        }

        buyButton = new UiButton(x, buttonY, width, BUY_HEIGHT, buyLabel(entry), button -> purchase(entry));
        buyButton.active = !entry.soldOut() && affordable > 0;
        addRenderableWidget(buyButton);
    }

    // WHY: в редакторе на месте покупки стоит удаление: витрина показывает товар так, как его
    // WHY: увидит игрок, и правится там же, где смотрится, а не в отдельной таблице
    private void addEditButton(ShopEntry entry, int previewLeft, int top) {
        int width = PREVIEW_WIDTH - (int) UiMetrics.PAD_WIDE * 2;
        int x = previewLeft + (int) UiMetrics.PAD_WIDE;
        int buttonY = top + panelHeight() - BUY_HEIGHT - (int) UiMetrics.PAD_WIDE;

        ShopView.Found found = view.find(entry.id());
        if (found == null) return;

        addRenderableWidget(new UiButton(x, buttonY, width, BUY_HEIGHT,
                Component.translatable("zones.shop.edit.drop"), button -> dropEntry(found)));
    }

    private void dropEntry(ShopView.Found found) {
        MenuCommands.run("battlecraft shop item remove " + found.sectionId() + " " + found.entry().id(),
                ShopAdminScreen.MENU_ID);
        entryId = neighbourEntry(found.entry().id());
        rebuild();
    }

    // WHY: удаляют подряд, поэтому выбор встаёт на соседа: возврат к первому товару заставляет
    // WHY: каждый раз искать место заново
    private String neighbourEntry(String removed) {
        String previous = null;
        for (ShopView.Found found : view.shown()) {
            if (found.entry().id().equals(removed)) return previous;
            previous = found.entry().id();
        }
        return null;
    }

    private boolean sliderShown(ShopEntry entry) {
        return !entry.soldOut() && maxAffordable(entry) > 1;
    }

    private UiSlider quantitySlider(int x, int y, int width, int affordable) {
        double start = (quantity - 1) / (double) (affordable - 1);
        return new UiSlider(x, y, width, QUANTITY_HEIGHT, quantityLabel(), start) {
            @Override
            protected void updateMessage() {
                setMessage(quantityLabel());
            }

            @Override
            protected void applyValue() {
                quantity = 1 + (int) Math.round(this.value * (affordable - 1));
                refreshBuyLabel();
            }
        };
    }

    private Component quantityLabel() {
        int bundle = bundleSize();
        if (bundle <= 1) return Component.translatable("zones.shop.quantity", quantity);
        return Component.translatable("zones.shop.quantity.bundled", quantity, quantity * bundle);
    }

    private int bundleSize() {
        ShopEntry entry = currentEntry();
        return entry == null ? 1 : entry.bundle();
    }

    private Component buyLabel(ShopEntry entry) {
        if (entry.soldOut()) return Component.translatable("zones.shop.sold_out");
        if (maxAffordable(entry) <= 0) {
            return Component.translatable("zones.shop.too_expensive", entry.price() - balanceValue());
        }

        int items = quantity * entry.bundle();
        if (items <= 1) return Component.translatable("zones.shop.buy", entry.price());
        return Component.translatable("zones.shop.buy.many", items, entry.price() * quantity);
    }

    private void refreshBuyLabel() {
        ShopEntry entry = currentEntry();
        if (buyButton != null && entry != null) {
            buyButton.setMessage(buyLabel(entry));
        }
    }

    private int maxAffordable(ShopEntry entry) {
        int ceiling = Math.max(1, MAX_ITEMS_PER_PURCHASE / entry.bundle());
        int limit = entry.limited() ? Math.min(ceiling, Math.max(0, entry.available())) : ceiling;
        if (entry.price() <= 0) return limit;
        return Math.min(limit, balanceValue() / entry.price());
    }

    private int balanceValue() {
        if (minecraft == null || minecraft.player == null) return 0;
        return ClientSellData.balance(minecraft.player);
    }

    private void purchase(ShopEntry entry) {
        ShopView.Found found = view.find(entry.id());
        if (found == null) return;

        PacketHandler.INSTANCE.sendToServer(
                new ShopPurchasePacket(found.sectionId(), found.childId(), entry.id(), quantity));
    }

    @Override
    protected float revealTop() {
        return contentTop();
    }

    @Override
    protected float revealSpan() {
        return panelHeight();
    }

    @Override
    protected float enterSeconds() {
        return ENTER_SECONDS;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = contentLeft();
        int top = contentTop();
        int gridLeft = left + SIDEBAR_WIDTH + PANEL_GAP;
        int previewLeft = gridLeft + GRID_WIDTH + PANEL_GAP;
        int height = panelHeight();

        UiTitle.render(graphics, this.font, editing
                        ? Component.translatable("zones.shop.edit.title")
                        : getTitle(),
                this.width / 2.0f, TITLE_TOP, TITLE_SCALE, 0.0f, UiAccent.text());
        renderHeaderPlates(graphics);

        UiGlass.window(graphics, left, top, SIDEBAR_WIDTH, height, PANEL_RADIUS, 1.0f);
        UiGlass.window(graphics, gridLeft, top, GRID_WIDTH, height, PANEL_RADIUS, 1.0f);
        UiGlass.window(graphics, previewLeft, top, PREVIEW_WIDTH, height, PANEL_RADIUS, 1.0f);
        UiGlass.layer(graphics);

        glideSections();
        renderGrid(graphics, gridLeft, top, height, mouseX, mouseY);
        renderPreview(graphics, previewLeft, top, height);

        renderWidgets(graphics, mouseX, mouseY, partialTick);
        renderSectionHints(graphics, left, top, height);
        renderFactHints(graphics, previewLeft);
        MenuFeedback.render(graphics, this.width / 2.0f, top + height + PANEL_GAP);
    }

    private void renderFactHints(GuiGraphics graphics, int previewLeft) {
        float centerX = previewLeft + PREVIEW_WIDTH / 2.0f;
        factsAbove.render(graphics, centerX, preview.textTop() - ScrollHint.BAND_TOP / 2.0f,
                false, preview.hiddenAbove());
        factsBelow.render(graphics, centerX, preview.textBottom() + ScrollHint.BAND_BOTTOM / 2.0f,
                true, preview.hiddenBelow());
    }

    private void renderSectionHints(GuiGraphics graphics, int left, int top, int height) {
        boolean scrollable = sectionsScrollable();
        int last = ClientShopData.offered().size() - sectionCapacity();
        float centerX = left + SIDEBAR_WIDTH / 2.0f;

        sectionsAbove.render(graphics, centerX, top + (UiMetrics.PAD_WIDE + ScrollHint.BAND_TOP) / 2.0f,
                false, scrollable && sectionScroll > 0);
        sectionsBelow.render(graphics, centerX,
                top + height - (UiMetrics.PAD_WIDE + ScrollHint.BAND_BOTTOM) / 2.0f,
                true, scrollable && sectionScroll < last);
    }

    // WHY: полоса отсечения шла от кромки панели, а ряды начинались ниже на высоту подсказки
    // WHY: прокрутки: запасной ряд над списком попадал в полосу и висел срезанной плашкой
    private void glideSections() {
        int top = contentTop();
        lanes.list(sectionGlide.to(0.0f, UiFrame.delta()), sectionsTop(top),
                sectionsTop(top) + sectionCapacity() * ROW_HEIGHT);
        for (UiButton button : sectionButtons) {
            button.glide(lanes);
        }
    }

    private int sectionsTop(int panelTop) {
        return panelTop + (int) UiMetrics.PAD_WIDE + (sectionsScrollable() ? ScrollHint.BAND_TOP : 0);
    }

    @Override
    protected float contentScale() {
        float needed = contentWidth() + FIT_MARGIN * 2.0f;
        return needed <= this.width ? 1.0f : this.width / needed;
    }

    private void renderHeaderPlates(GuiGraphics graphics) {
        float rowY = headerRowTop();
        UiGlass.sunken(graphics, headerRowLeft(), rowY, SEARCH_WIDTH, HEADER_ROW_HEIGHT,
                UiMetrics.radius(HEADER_ROW_HEIGHT), 1.0f);

        UiGlass.panel(graphics, balanceLeft(), rowY, BALANCE_WIDTH, HEADER_ROW_HEIGHT,
                UiMetrics.radius(HEADER_ROW_HEIGHT), 1.0f);
        UiRender.textTrackedFit(graphics, this.font, balanceLabel(),
                balanceLeft() + BALANCE_WIDTH / 2.0f, rowY, HEADER_ROW_HEIGHT,
                BALANCE_WIDTH - UiMetrics.PAD_WIDE * 2.0f, BALANCE_SCALE, 0.0f, UiAccent.text(), false);
    }

    private Component balanceLabel() {
        if (minecraft == null || minecraft.player == null) return Component.empty();
        return Component.translatable("zones.shop.balance", ClientSellData.balance(minecraft.player));
    }

    private void renderGrid(GuiGraphics graphics, int gridLeft, int top, int height, int mouseX, int mouseY) {
        int gridTop = top + tabsOffset();
        grid.place(gridLeft, gridTop, GRID_WIDTH, height - tabsOffset() - (int) UiMetrics.PAD);

        List<ShopView.Found> entries = view.shown();
        if (entries.isEmpty()) {
            UiRender.textCentered(graphics, this.font, emptyLabel(), gridLeft + GRID_WIDTH / 2.0f,
                    top + height / 2.0f, LINE_SCALE, UiAccent.text(), false);
            return;
        }
        grid.render(graphics, entries, entryId, view.searching(), mouseX, mouseY);
    }

    private Component emptyLabel() {
        return view.searching()
                ? Component.translatable("zones.shop.info.nothing_found")
                : Component.translatable("zones.shop.info.empty");
    }

    private int tabsOffset() {
        ShopSection section = currentSection();
        boolean tabs = !view.searching() && section != null && hasOfferedChildren(section);
        return (int) UiMetrics.PAD_WIDE + (tabs ? TAB_HEIGHT + (int) UiMetrics.GAP_WIDE : 0);
    }

    private void renderPreview(GuiGraphics graphics, int previewLeft, int top, int height) {
        ShopEntry entry = currentEntry();
        if (entry == null) {
            UiRender.textCentered(graphics, this.font, Component.translatable("zones.shop.pick_item"),
                    previewLeft + PREVIEW_WIDTH / 2.0f, top + height / 2.0f, LINE_SCALE,
                    UiAccent.text(), false);
            return;
        }
        preview.render(graphics, entry, previewLeft, top, PREVIEW_WIDTH, controlsTop(entry, top, height));
    }

    private float controlsTop(ShopEntry entry, int top, int height) {
        float reserved = BUY_HEIGHT + UiMetrics.PAD_WIDE * 2.0f;
        if (sliderShown(entry)) reserved += QUANTITY_HEIGHT + UiMetrics.GAP;
        return top + height - reserved;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (leaving()) return true;

        double localX = localX(mouseX);
        double localY = localY(mouseY);
        if (button == 0 && currentEntry() != null && preview.overModel(localX, localY)) {
            preview.beginDrag();
            return true;
        }
        if (button == 0 && grid.grabScrollbar(localX, localY, view.shown().size())) return true;
        if (pickTile(localX, localY)) {
            if (editing && button == 0) beginCarry(localX, localY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (leaving()) return true;
        if (grid.dragging()) {
            grid.dragScrollbar(localY(mouseY), view.shown().size());
            return true;
        }
        if (carrying) {
            grid.carryTo(localX(mouseX), localY(mouseY), view.shown().size());
            return true;
        }
        if (!preview.dragging()) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);

        preview.drag(dragX, dragY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        grid.endDrag();
        preview.endDrag();
        if (carrying) {
            dropCarried();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void beginCarry(double mouseX, double mouseY) {
        ShopView.Found found = view.find(entryId);
        if (found == null || view.searching()) return;

        int index = view.shown().indexOf(found);
        if (index < 0) return;

        carrying = true;
        grid.carry(found, index, mouseX, mouseY);
    }

    // WHY: место товара считается по его номеру в показанном списке: витрина без поиска
    // WHY: показывает ровно один раздел или отдел, и номер плитки равен месту в каталоге
    private void dropCarried() {
        carrying = false;
        ShopView.Found found = view.find(entryId);
        int delta = grid.dropDelta();
        if (found == null || delta == 0) return;

        int index = view.shown().indexOf(found);
        MenuCommands.run("battlecraft shop item order " + found.sectionId() + " "
                + found.entry().id() + " to " + (index + delta + 1), ShopAdminScreen.MENU_ID);
    }

    private boolean pickTile(double mouseX, double mouseY) {
        ShopView.Found found = grid.pick(mouseX, mouseY, view.shown());
        if (found == null) return false;

        entryId = found.entry().id();
        preview.rest();
        rebuild();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (leaving()) return true;

        double localX = localX(mouseX);
        double localY = localY(mouseY);
        int left = contentLeft();
        int step = amount > 0 ? -1 : 1;

        if (localX < left + SIDEBAR_WIDTH) {
            scrollSections(step);
            return true;
        }
        if (localX < left + SIDEBAR_WIDTH + PANEL_GAP + GRID_WIDTH) {
            grid.scrollBy(step, view.shown().size());
            return true;
        }
        if (preview.overModel(localX, localY)) {
            preview.magnify(amount);
            return true;
        }
        if (preview.overText(localX, localY) && preview.scrollText(amount)) return true;
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private void scrollSections(int step) {
        int next = clamp(sectionScroll + step, ClientShopData.offered().size(), sectionCapacity());
        if (next == sectionScroll) return;

        sectionGlide.snap(sectionGlide.get() + (next - sectionScroll) * ROW_HEIGHT);
        sectionScroll = next;
        rebuild();
    }

    private ShopSection currentSection() {
        return sectionId == null ? null : ClientShopData.offeredSection(sectionId);
    }

    private ShopEntry currentEntry() {
        if (entryId == null) return null;
        ShopView.Found found = view.find(entryId);
        return found == null ? null : found.entry();
    }

    private static int clamp(int scroll, int total, int capacity) {
        return Math.max(0, Math.min(scroll, Math.max(0, total - capacity)));
    }

    public boolean editing() {
        return editing;
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new ShopScreen());
    }

    public static void openEditor() {
        ShopScreen screen = new ShopScreen();
        screen.editing = true;
        screen.view.edit(true);
        Minecraft.getInstance().setScreen(screen);
    }
}
