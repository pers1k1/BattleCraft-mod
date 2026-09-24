package com.persiki84.shared.client.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTitle;
import com.persiki84.shared.client.ui.UiAccent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public abstract class ManagerScreen extends GlassScreen {
    protected static final float ROW_APPEAR_LIFT = 5.0f;
    private static final float GLIDE_SPEED = 11.0f;
    private static final float GLIDE_MAX_ROWS = 3.0f;

    protected int rowScroll;
    protected int listScroll;
    protected int shownRows = -1;
    protected int sourceRows = -1;
    private String shownSignature = "";
    private final Smooth glide = new Smooth(0.0f, GLIDE_SPEED);
    private final Smooth listGlide = new Smooth(0.0f, GLIDE_SPEED);
    private final ScrollLanes lanes = new ScrollLanes();
    private int rowsTotal;
    private int rowsCapacity;
    private int listTotal;
    private int listCapacity;
    private int scrollStep;

    protected static final int CONTENT_WIDTH = 430;
    protected static final int CONTENT_HEIGHT = 296;
    protected static final int CONTENT_HEIGHT_MIN = 150;
    protected static final int CONTENT_HEIGHT_MAX = 430;
    protected static final int HEADER_HEIGHT = 62;
    protected static final int TITLE_TOP = 14;
    protected static final int TAB_HEIGHT = 18;
    protected static final int TAB_GAP = 5;
    protected static final int ROW_HEIGHT = 22;
    protected static final int ROW_GAP = 4;
    protected static final float PANEL_RADIUS = 9.0f;
    protected static final float PANEL_ALPHA = 1.0f;
    protected static final float TITLE_SCALE = 1.0f;
    protected static final float HINT_SCALE = 0.75f;
    protected static final int PANEL_PAD = 10;
    protected static final String HINT_SUFFIX = MenuHint.SUFFIX;

    private static final int LAYOUT_PASSES = 3;
    private static final float FIT_MARGIN = 12.0f;
    private static final float PAGE_SPEED = 21.0f;
    private static final float PAGE_SLIDE = 14.0f;
    private static final float BOX_SPEED = 13.0f;
    private static final int BACK_WIDTH = 62;
    private static final int BACK_HEIGHT = 18;
    private static final int SEARCH_WIDTH = 156;

    private final Smooth page = new Smooth(1.0f, PAGE_SPEED);
    private final Smooth box = new Smooth(CONTENT_HEIGHT, BOX_SPEED);
    private final ScrollHint rowsAbove = new ScrollHint();
    private final ScrollHint rowsBelow = new ScrollHint();
    private final ScrollHint listAbove = new ScrollHint();
    private final ScrollHint listBelow = new ScrollHint();
    private final PaletteStack palettes = new PaletteStack();
    private final List<UiButton> tabButtons = new ArrayList<>();
    private final List<Component> tabTitles = new ArrayList<>();
    private UiButton backButton;
    private SearchField search;
    private boolean placed;
    private Screen returnTo;
    private boolean returning;
    private int slide;

    protected ManagerScreen(Component title) {
        super(title);
    }

    protected abstract List<Component> tabs();

    protected abstract int activeTab();

    protected abstract void pickTab(int index);

    protected abstract void buildBody();

    protected abstract void renderBody(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    public void returnTo(Screen parent) {
        this.returnTo = parent;
    }

    @Override
    protected void init() {
        rebuild();
    }

    // WHY: набранное в поиске переживает пересборку: отклик поля зовёт rebuild прямо из ввода,
    // WHY: и без возврата фокуса каждая буква уводила бы курсор из поля
    protected void rebuild() {
        boolean typing = search != null && search.typing();
        layout();
        if (typing) setFocused(search.box());
    }

    private void layout() {
        for (int pass = 0; pass < LAYOUT_PASSES; pass++) {
            int height = contentHeight();
            placed = false;
            clearWidgets();
            buildBody();
            addBackButton();
            addTabs();
            addSearch();
            if (contentHeight() == height) return;
        }
    }

    @Override
    protected int carryScope() {
        return activeTab();
    }

    @Override
    protected void carried(GuiEventListener widget, GuiEventListener older) {
        if (scrollStep != 0 || !steady()) return;
        if (widget instanceof MenuRow row && older instanceof MenuRow previous) row.drift(previous);
    }

    protected boolean searchable() {
        return true;
    }

    protected List<AbstractWidget> rowsOf(int tab) {
        return null;
    }

    protected boolean searching() {
        return search != null && !MenuSearch.needle(search.query()).isEmpty();
    }

    // WHY: поле показывается только там, где строки раскладывает place(): вкладки с ручной
    // WHY: раскладкой поиск не слушают, и висящее над ними поле обещало бы несуществующее
    private void addSearch() {
        if (!searchable()) {
            search = null;
            return;
        }
        if (search == null) {
            search = new SearchField(Component.translatable("battlecraft.menu.search"), this::onSearch);
        }
        if (!placed) return;

        search.place(searchLeft(), searchTop(), SEARCH_WIDTH);
        addWidget(search.box());
    }

    private boolean searchShown() {
        return search != null && placed;
    }

    private void onSearch(String text) {
        rowScroll = 0;
        rebuild();
    }

    // WHY: поле встаёт по левой кромке панели, а кнопка возврата уходит к правой: с полем
    // WHY: справа шапка не совпадала ни с одной кромкой и читалась как съехавшая
    private int searchLeft() {
        return contentLeft();
    }

    private int searchTop() {
        return backTop() + (BACK_HEIGHT - SearchField.HEIGHT) / 2;
    }

    private void addBackButton() {
        if (returnTo == null) {
            backButton = null;
            return;
        }
        if (backButton == null) {
            backButton = new UiButton(backLeft(), backTop(), BACK_WIDTH, BACK_HEIGHT,
                    Component.translatable("battlecraft.menu.back"), pressed -> goBack());
        } else {
            backButton.setPosition(backLeft(), backTop());
        }
        addWidget(backButton);
    }

    private int backTop() {
        return tabsTop() - BACK_HEIGHT - TAB_GAP;
    }

    private int backLeft() {
        return searchable() ? contentLeft() + CONTENT_WIDTH - BACK_WIDTH : contentLeft();
    }

    private boolean headerRow() {
        return returnTo != null || searchable();
    }

    protected float panelCenterX() {
        return contentLeft() + CONTENT_WIDTH / 2.0f;
    }

    private int tabsLeft() {
        int count = tabs().size();
        if (count == 0) return contentLeft();

        int width = tabWidth(count);
        return Math.round(panelCenterX() - (width * count + TAB_GAP * (count - 1)) / 2.0f);
    }

    private int tabsTop() {
        return contentTop() - TAB_HEIGHT - PANEL_PAD;
    }

    // WHY: возврат идёт тем же путём, что и закрытие: экран догорает своим набором движения,
    // WHY: а родитель проявляется заново, вместо мгновенной подмены одной картинки другой
    private void goBack() {
        if (returnTo == null || leaving()) return;

        returning = true;
        dismiss();
    }

    @Override
    protected void closing() {
        Screen parent = returnTo;
        returnTo = null;
        if (!returning || parent == null) {
            super.closing();
            return;
        }
        returning = false;
        if (parent instanceof GlassScreen glass) glass.reenter();
        Minecraft.getInstance().setScreen(parent);
    }

    private void addTabs() {
        List<Component> titles = tabs();
        if (titles.isEmpty()) {
            tabButtons.clear();
            tabTitles.clear();
            return;
        }

        if (!titles.equals(tabTitles)) recreateTabs(titles);

        int width = tabWidth(titles.size());
        int x = tabsLeft();
        for (int index = 0; index < tabButtons.size(); index++) {
            UiButton tab = tabButtons.get(index);
            tab.setPosition(x, tabsTop());
            tab.setWidth(width);
            tab.active = index != activeTab();
            addWidget(tab);
            x += width + TAB_GAP;
        }
    }

    private void recreateTabs(List<Component> titles) {
        tabButtons.clear();
        tabTitles.clear();
        tabTitles.addAll(titles);

        for (int index = 0; index < titles.size(); index++) {
            int slot = index;
            tabButtons.add(new UiButton(0, 0, tabWidth(titles.size()), TAB_HEIGHT,
                    titles.get(index), pressed -> jumpTo(slot)));
        }
    }

    private int tabWidth(int count) {
        int available = CONTENT_WIDTH - TAB_GAP * (count - 1);
        return Math.max(48, Math.min(96, available / count));
    }

    protected int contentLeft() {
        return (this.width - CONTENT_WIDTH) / 2;
    }

    protected int contentTop() {
        return Math.max(headerHeight(), (this.height - contentHeight()) / 2);
    }

    protected int contentHeight() {
        int available = Math.max(CONTENT_HEIGHT_MIN, this.height - headerHeight() - PANEL_PAD * 2);
        return Math.min(Math.min(available, CONTENT_HEIGHT_MAX), Math.max(CONTENT_HEIGHT_MIN, wantedHeight()));
    }

    private int headerHeight() {
        return headerRow() ? HEADER_HEIGHT + BACK_HEIGHT + TAB_GAP : HEADER_HEIGHT;
    }

    private int wantedHeight() {
        int rows = desiredRows();
        if (rows <= 0) return CONTENT_HEIGHT;
        return PANEL_PAD * 2 + rows * ROW_HEIGHT + (rows - 1) * ROW_GAP;
    }

    protected int desiredRows() {
        return 0;
    }

    protected int rowCapacity(int listHeight) {
        return Math.max(1, (listHeight + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
    }

    protected int rowCapacity(int listHeight, int total) {
        int plain = rowCapacity(listHeight);
        if (total <= plain) return plain;

        return Math.max(1, rowCapacity(listHeight - ScrollHint.BAND_TOP - ScrollHint.BAND_BOTTOM));
    }

    protected int rowsTop() {
        return contentTop() + PANEL_PAD + (rowsScrollable() ? ScrollHint.BAND_TOP : 0);
    }

    protected int listTop() {
        return contentTop() + PANEL_PAD + (listScrollable() ? ScrollHint.BAND_TOP : 0);
    }

    protected int rowsLeft() {
        return contentLeft() + PANEL_PAD;
    }

    protected int rowsWidth() {
        return CONTENT_WIDTH - PANEL_PAD * 2;
    }

    protected int listLeft() {
        return contentLeft() + PANEL_PAD;
    }

    protected int listWidth() {
        return 0;
    }

    private boolean rowsScrollable() {
        return rowsTotal > rowsCapacity;
    }

    private boolean listScrollable() {
        return listTotal > listCapacity;
    }

    @Override
    protected float revealTop() {
        return panelTop();
    }

    @Override
    protected float revealSpan() {
        return panelHeight();
    }

    protected float panelHeight() {
        return box.to(contentHeight(), UiFrame.delta());
    }

    protected float panelTop() {
        return Math.max(headerHeight(), (this.height - panelHeight()) / 2.0f);
    }

    @Override
    protected float contentScale() {
        float needed = CONTENT_WIDTH + FIT_MARGIN * 2.0f;
        return needed <= this.width ? 1.0f : this.width / needed;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiTitle.render(graphics, this.font, getTitle(), panelCenterX(), TITLE_TOP,
                TITLE_SCALE, 0.0f, UiAccent.text());

        UiGlass.window(graphics, contentLeft(), panelTop(), CONTENT_WIDTH, panelHeight(),
                PANEL_RADIUS, PANEL_ALPHA);
        UiGlass.layer(graphics);

        if (backButton != null) {
            backButton.render(graphics, mouseX, mouseY, partialTick);
        }
        if (searchShown()) {
            search.render(graphics);
            search.box().render(graphics, mouseX, mouseY, partialTick);
        }
        for (UiButton tab : tabButtons) {
            tab.render(graphics, mouseX, mouseY, partialTick);
        }
        renderPage(graphics, mouseX, mouseY, partialTick);
        MenuFeedback.render(graphics, panelCenterX(), panelTop() + panelHeight() + ROW_GAP);
    }

    private void renderPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float turned = UiAnim.easeOut(page.to(1.0f, UiFrame.delta()));

        graphics.pose().pushPose();
        graphics.pose().translate(slide * PAGE_SLIDE * (1.0f - turned), 0.0f, 0.0f);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, turned * shown());
        try {
            glideRows();
            renderBody(graphics, mouseX, mouseY, partialTick);
            clipToPanel(graphics);
            try {
                renderWidgets(graphics, mouseX, mouseY, partialTick);
                graphics.flush();
                renderScrollHints(graphics);
            } finally {
                graphics.disableScissor();
            }
        } finally {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, shown());
            graphics.pose().popPose();
        }
    }

    protected void renderHint(GuiGraphics graphics, Component hint, float y) {
        float centerX = rowsLeft() + rowsWidth() / 2.0f;
        int wrap = Math.max(1, (int) ((rowsWidth() - PANEL_PAD * 2) / HINT_SCALE));
        List<FormattedCharSequence> lines = UiRender.split(graphics, this.font, hint, HINT_SCALE, wrap);
        float step = this.font.lineHeight * HINT_SCALE + ROW_GAP;
        float top = y - (lines.size() - 1) * step / 2.0f;

        for (int index = 0; index < lines.size(); index++) {
            UiRender.textCentered(graphics, this.font, Component.literal(UiRender.flatten(lines.get(index))),
                    centerX, top + index * step, HINT_SCALE, UiAccent.textFaint(), false);
        }
    }

    protected int clampScroll(int scroll, int total, int capacity) {
        rowsTotal = total;
        rowsCapacity = capacity;
        return limit(scroll, total, capacity);
    }

    protected int clampList(int scroll, int total, int capacity) {
        listTotal = total;
        listCapacity = capacity;
        return limit(scroll, total, capacity);
    }

    protected <T> List<T> listWindow(List<T> source) {
        return around(source, listScroll, listCapacity);
    }

    protected int listWindowTop() {
        return listTop() - lead(listScroll) * (ROW_HEIGHT + ROW_GAP);
    }

    protected boolean scrollList(int step) {
        int next = limit(listScroll + step, listTotal, listCapacity);
        if (next == listScroll) return false;

        noteListScroll(next - listScroll, ROW_HEIGHT + ROW_GAP);
        listScroll = next;
        rebuild();
        return true;
    }

    protected boolean scrollRows(int step) {
        int next = limit(rowScroll + step, rowsTotal, rowsCapacity);
        if (next == rowScroll) return false;

        int travelled = (next - rowScroll) * (ROW_HEIGHT + ROW_GAP);
        rowScroll = next;
        scrollStep = step;
        glide.snap(capped(glide.get() + travelled));
        rebuild();
        scrollStep = 0;
        return true;
    }

    protected float rowSlide() {
        return scrollStep == 0 ? ROW_APPEAR_LIFT : 0.0f;
    }

    private void renderScrollHints(GuiGraphics graphics) {
        float top = panelTop();
        float bottom = top + panelHeight();

        hintPair(graphics, rowsAbove, rowsBelow, rowsLeft() + rowsWidth() / 2.0f, top, bottom,
                rowsScrollable() && rowScroll > 0, rowsScrollable() && rowScroll < rowsTotal - rowsCapacity);

        if (listWidth() <= 0) return;

        hintPair(graphics, listAbove, listBelow, listLeft() + listWidth() / 2.0f, top, bottom,
                listScrollable() && listScroll > 0, listScrollable() && listScroll < listTotal - listCapacity);
    }

    private static void hintPair(GuiGraphics graphics, ScrollHint above, ScrollHint below, float centerX,
                                 float top, float bottom, boolean hiddenAbove, boolean hiddenBelow) {
        above.render(graphics, centerX, top + (PANEL_PAD + ScrollHint.BAND_TOP) / 2.0f, false, hiddenAbove);
        below.render(graphics, centerX, bottom - (PANEL_PAD + ScrollHint.BAND_BOTTOM) / 2.0f, true, hiddenBelow);
    }

    private void clipToPanel(GuiGraphics graphics) {
        UiRender.clip(graphics, contentLeft(), panelTop(), CONTENT_WIDTH, panelHeight());
    }

    private void glideRows() {
        float delta = UiFrame.delta();
        float top = panelTop() + PANEL_PAD;
        float bottom = panelTop() + panelHeight() - PANEL_PAD;
        lanes.rows(glide.to(0.0f, delta), top, bottom);
        lanes.list(listGlide.to(0.0f, delta), top, bottom);

        for (Renderable renderable : this.renderables) {
            if (renderable instanceof GlidingRow row) row.glide(lanes);
        }
    }

    protected void noteListScroll(int step, int rowHeight) {
        listGlide.snap(capped(listGlide.get() + step * rowHeight));
    }

    private static float capped(float offset) {
        float limit = (ROW_HEIGHT + ROW_GAP) * GLIDE_MAX_ROWS;
        return Math.max(-limit, Math.min(limit, offset));
    }

    private static int limit(int scroll, int total, int capacity) {
        return Math.max(0, Math.min(scroll, Math.max(0, total - capacity)));
    }

    protected void renderListWell(GuiGraphics graphics) {
        UiGlass.sunken(graphics, contentLeft() + PANEL_PAD - 4, panelTop() + PANEL_PAD - 4,
                listWidth() + 8, panelHeight() - PANEL_PAD * 2 + 8, UiMetrics.radius(ROW_HEIGHT), 0.65f);
    }

    protected void place(List<AbstractWidget> source) {
        placed = true;
        sourceRows = source.size();
        List<AbstractWidget> rows = searching() ? found(source) : source;
        shownRows = rows.size();
        int capacity = rowCapacity(contentHeight() - PANEL_PAD * 2, rows.size());
        rowScroll = clampScroll(rowScroll, rows.size(), capacity);

        int lead = lead(rowScroll);
        int y = rowsTop() - lead * (ROW_HEIGHT + ROW_GAP);
        int index = -lead;
        for (AbstractWidget row : around(rows, rowScroll, capacity)) {
            if (row instanceof GlidingRow gliding) {
                gliding.anchor(y, GlidingRow.Lane.ROWS);
            } else {
                row.setY(y);
            }
            if (row instanceof MenuRow menuRow && rowSlide() > 0.0f) {
                menuRow.stagger(Math.max(0, index), rowSlide());
            }
            addRenderableWidget(row);
            y += ROW_HEIGHT + ROW_GAP;
            index++;
        }
    }

    // WHY: окно палитры одно на все экраны: выбор цвета стрелками по списку названий не показывал
    // WHY: сам цвет, а своего цвета вне списка не давал выбрать вовсе
    protected void palette(String owner, PaletteWindow.Kind kind, Component title, int color,
                           IntConsumer apply, Runnable clear) {
        palettes.toggle(this.width, this.height, owner, kind, title, color, apply, clear);
    }

    protected boolean paletteCovering(double mouseX, double mouseY) {
        return palettes.covering(mouseX, mouseY);
    }

    @Override
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        palettes.render(graphics, this.width, this.height, mouseX, mouseY);
    }

    @Override
    protected boolean inputCaptured() {
        return palettes.covering(cursorX(), cursorY());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!leaving() && palettes.mouseClicked(mouseX, mouseY)) return true;

        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (palettes.any()) setFocused(null);
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (palettes.mouseDragged(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (palettes.mouseReleased()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char symbol, int modifiers) {
        if (palettes.charTyped(symbol)) return true;
        return super.charTyped(symbol, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (leaving() || inputCaptured()) return true;

        int step = amount > 0 ? -1 : 1;
        if (localX(mouseX) >= contentLeft() + PANEL_PAD + listWidth()) {
            return scrollRows(step) || true;
        }
        return scrollList(step) || true;
    }

    protected static List<String> teamNames() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return List.of();
        return new ArrayList<>(minecraft.level.getScoreboard().getTeamNames());
    }

    // WHY: всеобщий поиск: найденное на чужих вкладках дописывается под своей вкладкой строкой
    // WHY: перехода, потому что чужая строка не знает ни своего состояния, ни своих команд здесь
    private List<AbstractWidget> found(List<AbstractWidget> source) {
        String needle = MenuSearch.needle(search.query());
        List<AbstractWidget> kept = new ArrayList<>(MenuSearch.filter(source, needle));
        addElsewhere(kept, needle);
        if (kept.isEmpty()) kept.add(reading("battlecraft.menu.search.empty", Component::empty));
        return kept;
    }

    private void addElsewhere(List<AbstractWidget> kept, String needle) {
        List<Component> titles = tabs();
        for (int index = 0; index < titles.size(); index++) {
            if (index == activeTab()) continue;

            List<AbstractWidget> other = rowsOf(index);
            if (other == null) continue;
            addHits(kept, MenuSearch.filter(other, needle), titles.get(index), index);
        }
    }

    private void addHits(List<AbstractWidget> kept, List<AbstractWidget> hits, Component title, int tab) {
        boolean headed = false;
        for (AbstractWidget hit : hits) {
            if (hit instanceof HeadingRow) continue;

            if (!headed) {
                kept.add(heading(title));
                headed = true;
            }
            kept.add(new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, hit.getMessage(),
                    () -> Component.translatable("battlecraft.menu.search.go"), () -> jumpTo(tab)));
        }
    }

    protected HeadingRow heading(Component label) {
        return new HeadingRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, label);
    }

    private void jumpTo(int index) {
        if (index == activeTab()) return;

        slide = Integer.compare(index, activeTab());
        palettes.closeAll();
        pickTab(index);
        page.snap(0.0f);
        rebuild();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (palettes.keyPressed(key)) return true;
        // WHY: строка ловит Escape сама, когда в ней печатают число или клавишу, поэтому поиск
        // WHY: перехватывает его только вне строк - иначе ввод в строке нечем отменить
        if (key == GLFW.GLFW_KEY_ESCAPE && !inputCaptured() && searching()
                && !(getFocused() instanceof MenuRow)) {
            search.clear();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    protected ActionRow action(String label, String value, Runnable run) {
        ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(label), () -> Component.translatable(value), run);
        row.hint(label + HINT_SUFFIX);
        return row;
    }

    // WHY: перестановка и переименование не меняют числа строк, поэтому экран, обновляющийся по их
    // WHY: количеству, их не замечает: список сверяется подписью, как состав магазина
    protected boolean stale(String signature) {
        if (signature.equals(shownSignature)) return false;
        if (typingInRow()) return false;

        shownSignature = signature;
        return true;
    }

    // WHY: пересборка заменяет виджеты, а набранное живёт в самой строке: обновление снимка
    // WHY: посреди набора числа или клавиши стирало бы ввод у того, кто как раз печатает
    protected boolean typingInRow() {
        return getFocused() instanceof MenuRow row && row.capturing();
    }

    protected static String heldItem() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return "";

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return "";

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    protected ActionRow reading(String label, Supplier<Component> value) {
        ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(label), value, () -> {});
        row.hint(label + HINT_SUFFIX);
        row.active = false;
        return row;
    }
}
