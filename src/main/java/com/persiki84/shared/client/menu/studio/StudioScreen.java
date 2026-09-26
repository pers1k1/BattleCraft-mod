package com.persiki84.shared.client.menu.studio;

import com.persiki84.shared.client.menu.GlassScreen;
import com.persiki84.shared.client.menu.MenuCommands;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.pick.ItemShelf;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTitle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// WHY: одна раскладка на все редакторы разделов: слева что правим, в центре как это выглядит, справа
// WHY: все свойства выбранного разом. Виджеты создаются один раз и переставляются: состояние анимаций
// WHY: живёт в самом виджете, и пересоздание на каждый снимок обрывало бы любой переход
public abstract class StudioScreen extends GlassScreen {
    protected static final int NAV_WIDTH = 132;
    protected static final int CANVAS_WIDTH = 322;
    protected static final int INSPECTOR_WIDTH = 240;
    protected static final int GAP = 8;
    protected static final int ROW = 22;
    protected static final int ROW_GAP = 4;
    protected static final int CONTROL = 16;
    protected static final int HEADER_ROW = 20;
    protected static final int HEADER_BUTTON = 108;

    private static final int HEADER = 76;
    private static final int PANEL_LIMIT = 340;
    private static final int TITLE_TOP = 12;
    private static final int STATS_WIDTH = 236;
    private static final float RADIUS = 9.0f;
    private static final float FIT_MARGIN = 10.0f;
    private static final float LINE_SCALE = 0.72f;
    private static final int COMMIT_TICKS = 6;
    private static final int SETTLE_TICKS = 40;
    private static final long ARM_MILLIS = 3000L;
    private static final int DRAG_START = 4;
    private static final float ART_SPEED = 11.0f;
    private static final float ART_SLIDE = 12.0f;
    private static final float CARRIED_SCALE = 1.5f;
    private static final int SHELF_HINT = 14;

    protected final StudioNav nav = new StudioNav();
    protected final StudioGrid grid = new StudioGrid();
    protected final StudioStack inspector = new StudioStack();
    protected final StudioStack canvas = new StudioStack();
    protected final StudioShelf shelf = new StudioShelf();
    private final Map<String, String> due = new LinkedHashMap<>();
    private final Smooth artShown = new Smooth(0.0f, ART_SPEED);
    private Screen parent;
    protected int ticks;
    private int commitAt = -1;
    private int pendingSince = -1;
    private String armedKey;
    private long armedAt;
    private Object shownStamp;
    private Object artSubject;
    private StudioNav.Node pressNode;
    private int pressTile = -1;
    private ItemShelf.Pick pressPick;
    private boolean carryingNode;
    private boolean carryingTile;
    private boolean carryingPick;
    private double pressX;
    private double pressY;
    private double pointerX;
    private double pointerY;

    protected StudioScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    public void returnTo(Screen screen) {
        parent = screen;
    }

    protected abstract String menuId();

    protected abstract List<StudioNav.Node> nodes();

    protected abstract String selectedNode();

    protected abstract void selectNode(StudioNav.Node node);

    protected abstract Component stats();

    protected abstract List<UiButton> headerButtons();

    protected abstract Object inspectorSubject();

    protected abstract void buildInspector(StudioStack stack, int x, int width);

    protected abstract Object stamp();

    protected int artHeight() {
        return 0;
    }

    protected void renderArt(GuiGraphics graphics, float left, float top, float width, float appear,
                             int mouseX, int mouseY) {
    }

    protected boolean pressArt(double mouseX, double mouseY) {
        return false;
    }

    protected boolean dragArt(double dragX, double dragY) {
        return false;
    }

    protected void releaseArt() {
    }

    protected boolean scrollArt(double mouseX, double mouseY, double amount) {
        return false;
    }

    protected boolean gridCanvas() {
        return true;
    }

    protected List<? extends StudioTile> tiles() {
        return List.of();
    }

    protected int selectedTile() {
        return -1;
    }

    protected void selectTile(int index) {
    }

    protected boolean tilesMovable() {
        return false;
    }

    protected void moveTile(int from, int to) {
    }

    protected boolean acceptsTile(StudioNav.Node node, int tile) {
        return false;
    }

    protected void dropTile(StudioNav.Node node, int tile) {
    }

    protected StudioNav.Drop navRule(StudioNav.Node carried, StudioNav.Node over) {
        return StudioNav.Drop.NONE;
    }

    protected void navMoved(StudioNav.Node carried, StudioNav.Node target, StudioNav.Drop drop) {
    }

    protected void buildCanvas(StudioStack stack, int x, int width) {
    }

    protected boolean shelfOpen() {
        return false;
    }

    protected void addPick(ItemShelf.Pick pick, int slot) {
    }

    protected Component emptyCanvas() {
        return Component.translatable("studio.empty");
    }

    protected Component shelfHint() {
        return Component.translatable("studio.shelf.hint");
    }

    protected void deleteSelected() {
    }

    protected boolean submit() {
        return dropFocus();
    }

    protected boolean modalOpen() {
        return false;
    }

    protected boolean modalClick(double mouseX, double mouseY, int button) {
        return false;
    }

    protected void closeModal() {
    }

    protected void renderModal(GuiGraphics graphics, float centerX, float centerY, int mouseX, int mouseY) {
    }

    protected ItemStack modalHovered(double mouseX, double mouseY) {
        return ItemStack.EMPTY;
    }

    protected void renderCanvasOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    protected int contentWidth() {
        return NAV_WIDTH + CANVAS_WIDTH + INSPECTOR_WIDTH + GAP * 2;
    }

    protected int contentLeft() {
        return (this.width - contentWidth()) / 2;
    }

    protected int contentTop() {
        return Math.max(HEADER, (this.height - panelHeight()) / 2);
    }

    protected int panelHeight() {
        return Math.min(this.height - HEADER - GAP * 2, PANEL_LIMIT);
    }

    protected int canvasLeft() {
        return contentLeft() + NAV_WIDTH + GAP;
    }

    protected int inspectorLeft() {
        return canvasLeft() + CANVAS_WIDTH + GAP;
    }

    protected int inspectorRowsLeft() {
        return inspectorLeft() + (int) UiMetrics.PAD;
    }

    protected int inspectorRowsWidth() {
        return INSPECTOR_WIDTH - (int) UiMetrics.PAD * 2;
    }

    protected int canvasRowsLeft() {
        return canvasLeft() + (int) UiMetrics.PAD_WIDE;
    }

    protected int canvasRowsWidth() {
        return CANVAS_WIDTH - (int) UiMetrics.PAD_WIDE * 2;
    }

    protected int headerTop() {
        return contentTop() - HEADER_ROW - GAP;
    }

    private int headerLeft() {
        int buttons = headerButtons().size();
        return (this.width - (STATS_WIDTH + buttons * (HEADER_BUTTON + GAP))) / 2;
    }

    protected int panelBottom() {
        return contentTop() + panelHeight();
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
    protected float contentScale() {
        float needed = contentWidth() + FIT_MARGIN * 2.0f;
        return needed <= this.width ? 1.0f : this.width / needed;
    }

    @Override
    protected void init() {
        layout();
    }

    @Override
    protected void closing() {
        flushCommits();
        if (parent == null) {
            super.closing();
            return;
        }
        if (parent instanceof GlassScreen glass) glass.reenter();
        Minecraft.getInstance().setScreen(parent);
    }

    protected void send(String command) {
        MenuCommands.run(command, menuId());
        pendingSince = ticks;
    }

    // WHY: число уходит на сервер после паузы в наборе: удержание стрелки шагает десятки раз
    // WHY: в секунду, и каждая команда писала бы файл на диск и рассылала снимок всем
    protected void commitLater(String key, String command) {
        due.put(key, command);
        commitAt = ticks + COMMIT_TICKS;
    }

    protected boolean committing(String key) {
        return due.containsKey(key);
    }

    protected void flushCommits() {
        for (String command : due.values()) {
            send(command);
        }
        due.clear();
        commitAt = -1;
    }

    // WHY: необратимое удаление идёт вторым нажатием в течение трёх секунд, подпись кнопки
    // WHY: успевает смениться на «Точно?», и случайный щелчок ничего не теряет
    protected boolean confirm(String key) {
        long now = System.currentTimeMillis();
        if (key.equals(armedKey) && now - armedAt < ARM_MILLIS) {
            armedKey = null;
            return true;
        }
        armedKey = key;
        armedAt = now;
        return false;
    }

    protected boolean armed(String key) {
        return key.equals(armedKey) && System.currentTimeMillis() - armedAt < ARM_MILLIS;
    }

    protected final void layout() {
        GuiEventListener focus = getFocused();
        boolean typing = focus instanceof MenuRow row && row.capturing() || focus instanceof EditBox;
        clearWidgets();
        placeHeader();
        placeInspector();
        if (!gridCanvas()) placeCanvas();
        if (typing && children().contains(focus)) setFocused(focus);
    }

    private void placeHeader() {
        int x = headerLeft() + STATS_WIDTH + GAP;
        for (UiButton button : headerButtons()) {
            button.setWidth(HEADER_BUTTON);
            button.setPosition(x, headerTop());
            addRenderableWidget(button);
            x += HEADER_BUTTON + GAP;
        }
    }

    private void placeInspector() {
        int top = contentTop() + (int) UiMetrics.PAD + artHeight();
        int bottom = panelBottom() - (int) UiMetrics.PAD;
        if (shelfOpen()) {
            placeShelf(top);
            return;
        }
        inspector.begin(top, bottom, ROW_GAP, inspectorSubject());
        buildInspector(inspector, inspectorRowsLeft(), inspectorRowsWidth());
        inspector.end();
        for (AbstractWidget widget : inspector.placed()) {
            addRenderableWidget(widget);
        }
    }

    private void placeShelf(int top) {
        inspector.begin(top, top, ROW_GAP, shelf);
        buildInspector(inspector, inspectorRowsLeft(), inspectorRowsWidth());
        inspector.end();
        for (AbstractWidget widget : inspector.placed()) {
            addRenderableWidget(widget);
        }
        for (AbstractWidget widget : shelf.place(inspectorRowsLeft(), inspector.cursor(), inspectorRowsWidth())) {
            addRenderableWidget(widget);
        }
    }

    private void placeCanvas() {
        int top = contentTop() + (int) UiMetrics.PAD_WIDE;
        canvas.begin(top, panelBottom() - (int) UiMetrics.PAD_WIDE, ROW_GAP, selectedNode());
        buildCanvas(canvas, canvasRowsLeft(), canvasRowsWidth());
        canvas.end();
        for (AbstractWidget widget : canvas.placed()) {
            addRenderableWidget(widget);
        }
    }

    @Override
    public void tick() {
        ticks++;
        if (commitAt >= 0 && ticks >= commitAt && !typingRow()) flushCommits();
        if (pendingSince >= 0 && ticks - pendingSince > SETTLE_TICKS) {
            pendingSince = -1;
            grid.settle();
        }
        Object stamp = stamp();
        if (Objects.equals(stamp, shownStamp) || busy()) return;

        shownStamp = stamp;
        pendingSince = -1;
        grid.settle();
        refreshed();
        layout();
    }

    protected void refreshed() {
    }

    private boolean busy() {
        return isDragging() || carryingNode || carryingTile || carryingPick || typingRow() || shelf.typing();
    }

    protected boolean typingRow() {
        GuiEventListener focus = getFocused();
        return focus instanceof MenuRow row && row.capturing();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        pointerX = mouseX;
        pointerY = mouseY;
        UiTitle.render(graphics, this.font, getTitle(), this.width / 2.0f, TITLE_TOP, 1.0f, 0.0f, UiAccent.text());
        renderStats(graphics);
        renderWindows(graphics);
        nav.place(contentLeft(), contentTop(), NAV_WIDTH, panelHeight());
        nav.render(graphics, nodes(), selectedNode(), mouseX, mouseY);
        renderCanvasArea(graphics, mouseX, mouseY);
        renderInspectorArea(graphics, mouseX, mouseY);
        inspector.glide();
        canvas.glide();
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        inspector.renderHints(graphics, inspectorLeft() + INSPECTOR_WIDTH / 2.0f);
        renderCanvasOverlay(graphics, mouseX, mouseY);
        renderCarriedPick(graphics);
        if (modalOpen()) renderModal(graphics, this.width / 2.0f, contentTop() + panelHeight() / 2.0f, mouseX, mouseY);
        MenuFeedback.render(graphics, this.width / 2.0f, panelBottom() + GAP);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderStats(GuiGraphics graphics) {
        float left = headerLeft();
        UiGlass.panel(graphics, left, headerTop(), STATS_WIDTH, HEADER_ROW, UiMetrics.radius(HEADER_ROW), 1.0f);
        UiRender.textTrackedFit(graphics, this.font, stats(), left + STATS_WIDTH / 2.0f, headerTop(), HEADER_ROW,
                STATS_WIDTH - UiMetrics.PAD_WIDE * 2.0f, 0.8f, 0.0f, UiAccent.text(), false);
    }

    private void renderWindows(GuiGraphics graphics) {
        int top = contentTop();
        int height = panelHeight();
        UiGlass.window(graphics, contentLeft(), top, NAV_WIDTH, height, RADIUS, 1.0f);
        UiGlass.window(graphics, canvasLeft(), top, CANVAS_WIDTH, height, RADIUS, 1.0f);
        UiGlass.window(graphics, inspectorLeft(), top, INSPECTOR_WIDTH, height, RADIUS, 1.0f);
        UiGlass.layer(graphics);
    }

    private void renderCanvasArea(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!gridCanvas()) {
            canvas.renderHints(graphics, canvasLeft() + CANVAS_WIDTH / 2.0f);
            return;
        }
        grid.place(canvasLeft(), contentTop() + UiMetrics.PAD, CANVAS_WIDTH, panelHeight() - UiMetrics.PAD * 2.0f);
        List<? extends StudioTile> tiles = tiles();
        if (tiles.isEmpty() && !carryingPick) {
            renderWrapped(graphics, emptyCanvas(), canvasLeft() + CANVAS_WIDTH / 2.0f,
                    contentTop() + panelHeight() / 2.0f, CANVAS_WIDTH);
            return;
        }
        grid.render(graphics, tiles, selectedTile(), mouseX, mouseY);
    }

    private void renderInspectorArea(GuiGraphics graphics, int mouseX, int mouseY) {
        float left = inspectorLeft() + UiMetrics.PAD_WIDE;
        float width = INSPECTOR_WIDTH - UiMetrics.PAD_WIDE * 2.0f;
        if (shelfOpen()) {
            renderShelf(graphics, mouseX, mouseY);
            return;
        }
        Object subject = inspectorSubject();
        if (subject == null ? artSubject != null : !subject.equals(artSubject)) {
            artSubject = subject;
            artShown.snap(0.0f);
        }
        float appear = UiAnim.easeOut(artShown.to(1.0f, UiFrame.delta()));
        graphics.pose().pushPose();
        graphics.pose().translate((1.0f - appear) * ART_SLIDE, 0.0f, 0.0f);
        try {
            renderArt(graphics, left, contentTop() + UiMetrics.PAD_WIDE, width, appear, mouseX, mouseY);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderShelf(GuiGraphics graphics, int mouseX, int mouseY) {
        float left = inspectorLeft() + UiMetrics.PAD;
        float width = INSPECTOR_WIDTH - UiMetrics.PAD * 2.0f;
        float bottom = panelBottom() - UiMetrics.PAD - SHELF_HINT;
        shelf.render(graphics, left, width, bottom, mouseX, mouseY);
        line(graphics, shelfHint(), left, width, bottom + 2.0f);
    }

    protected void line(GuiGraphics graphics, Component text, float left, float width, float y) {
        UiRender.textTrackedFit(graphics, this.font, text, left + width / 2.0f, y, 10.0f, width, LINE_SCALE, 0.0f,
                UiAccent.textDim(), false);
    }

    protected void renderWrapped(GuiGraphics graphics, Component text, float centerX, float centerY, float width) {
        List<FormattedCharSequence> lines = UiRender.split(graphics, this.font, text, LINE_SCALE,
                (int) ((width - UiMetrics.PAD_WIDE * 2.0f) / LINE_SCALE));
        float step = this.font.lineHeight * LINE_SCALE + UiMetrics.GAP;
        float y = centerY - lines.size() * step / 2.0f;
        for (FormattedCharSequence sequence : lines) {
            UiRender.textCentered(graphics, this.font, Component.literal(UiRender.flatten(sequence)), centerX, y,
                    LINE_SCALE, UiAccent.textDim(), false);
            y += step;
        }
    }

    private void renderCarriedPick(GuiGraphics graphics) {
        if (!carryingPick || pressPick == null) return;

        graphics.pose().pushPose();
        graphics.pose().translate(pointerX - 12.0f, pointerY - 12.0f, 200.0f);
        graphics.pose().scale(CARRIED_SCALE, CARRIED_SCALE, 1.0f);
        try {
            graphics.renderItem(pressPick.stack(), 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (carryingTile || carryingPick || carryingNode) return;

        ItemStack stack = hoveredStack(mouseX, mouseY);
        if (stack.isEmpty()) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 400.0f);
        try {
            graphics.renderTooltip(this.font, stack, mouseX, mouseY);
        } finally {
            graphics.pose().popPose();
        }
    }

    private ItemStack hoveredStack(int mouseX, int mouseY) {
        if (modalOpen()) return modalHovered(mouseX, mouseY);
        if (shelfOpen() && shelf.over(mouseX, mouseY)) return shelf.hovered(mouseX, mouseY);
        if (!gridCanvas()) return ItemStack.EMPTY;

        List<? extends StudioTile> tiles = tiles();
        int index = grid.pick(mouseX, mouseY, tiles.size());
        return index < 0 ? ItemStack.EMPTY : tiles.get(index).icon();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (leaving()) return true;

        double x = localX(mouseX);
        double y = localY(mouseY);
        if (modalOpen()) {
            if (!modalClick(x, y, button)) closeModal();
            return true;
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (pressArt(x, y)) return dropFocus();
        if (nav.over(x, y)) return dropFocus() && pressNav(x, y);
        if (gridCanvas() && grid.over(x, y)) return dropFocus() && pressGrid(x, y);
        if (shelfOpen() && shelf.over(x, y)) return dropFocus() && pressShelf(x, y);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // WHY: сетка и список рисуются мимо виджетов экрана, и щелчок по ним не снимал фокус с поля:
    // WHY: набор висел открытым, правка не уходила, а экран не обновлялся, считая, что идёт ввод
    private boolean dropFocus() {
        if (getFocused() != null) setFocused(null);
        return true;
    }

    private boolean pressNav(double x, double y) {
        StudioNav.Node node = nav.at(x, y);
        if (node == null) return true;

        pressNode = node;
        pressX = x;
        pressY = y;
        if (!node.key().equals(selectedNode())) {
            flushCommits();
            selectNode(node);
            layout();
        }
        return true;
    }

    // WHY: щелчок по пустому месту сетки снимает выбор: так справа открываются настройки раздела
    private boolean pressGrid(double x, double y) {
        int index = grid.pick(x, y, tiles().size());
        if (index < 0) {
            if (selectedTile() >= 0) chooseTile(-1);
            return true;
        }
        pressTile = index;
        pressX = x;
        pressY = y;
        if (index != selectedTile()) chooseTile(index);
        return true;
    }

    protected void chooseTile(int index) {
        flushCommits();
        selectTile(index);
        layout();
    }

    private boolean pressShelf(double x, double y) {
        ItemShelf.Pick pick = shelf.pick(x, y);
        if (pick != null && shelf.doubled(pick)) {
            flushCommits();
            addPick(pick, -1);
            return true;
        }
        pressPick = pick;
        pressX = x;
        pressY = y;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (leaving()) return true;

        double x = localX(mouseX);
        double y = localY(mouseY);
        if (dragArt(dragX, dragY)) return true;
        if (pressNode != null) return dragNode(x, y);
        if (pressTile >= 0) return dragTile(x, y);
        if (pressPick != null) return dragPick(x, y);
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean dragNode(double x, double y) {
        if (!carryingNode && pressNode.movable() && moved(x, y)) {
            carryingNode = true;
            nav.carry(pressNode, y);
        }
        if (carryingNode) nav.carryTo(x, y, this::navRule);
        return true;
    }

    private boolean dragTile(double x, double y) {
        if (!carryingTile && moved(x, y)) {
            carryingTile = true;
            grid.carry(pressTile, x, y);
        }
        if (!carryingTile) return true;

        grid.carryTo(x, y, tiles().size(), tilesMovable());
        int carried = pressTile;
        nav.hoverDrop(x, y, node -> acceptsTile(node, carried));
        return true;
    }

    private boolean dragPick(double x, double y) {
        carryingPick = carryingPick || moved(x, y);
        if (carryingPick && gridCanvas()) grid.openGap(x, y, tiles().size());
        return true;
    }

    private boolean moved(double x, double y) {
        return Math.abs(x - pressX) > DRAG_START || Math.abs(y - pressY) > DRAG_START;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        releaseArt();
        double x = localX(mouseX);
        double y = localY(mouseY);
        if (carryingNode) releaseNode();
        if (carryingTile) releaseTile();
        if (pressPick != null) releasePick(x, y);
        pressNode = null;
        pressTile = -1;
        pressPick = null;
        carryingNode = false;
        carryingTile = false;
        carryingPick = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void releaseNode() {
        StudioNav.Node carried = nav.carried();
        StudioNav.Node target = nav.target();
        StudioNav.Drop drop = nav.drop();
        nav.release();
        if (target == null || drop == StudioNav.Drop.NONE) return;
        flushCommits();
        navMoved(carried, target, drop);
    }

    private void releaseTile() {
        String dropKey = nav.dropKey();
        nav.clearDrop();
        int[] move = grid.drop();
        if (dropKey != null) {
            grid.settle();
            flushCommits();
            dropTile(nodeByKey(dropKey), move[0]);
            return;
        }
        if (!tilesMovable() || move[0] < 0 || move[0] == move[1]) {
            grid.settle();
            return;
        }
        flushCommits();
        moveTile(move[0], move[1]);
    }

    private StudioNav.Node nodeByKey(String key) {
        for (StudioNav.Node node : nodes()) {
            if (node.key().equals(key)) return node;
        }
        return null;
    }

    private void releasePick(double x, double y) {
        grid.closeGap();
        if (!carryingPick) return;
        if (!gridCanvas() || !grid.over(x, y)) return;

        flushCommits();
        addPick(pressPick, grid.slotAt(x, y, tiles().size()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (leaving() || modalOpen()) return true;

        double x = localX(mouseX);
        double y = localY(mouseY);
        int step = amount > 0 ? -1 : 1;
        if (nav.over(x, y)) {
            nav.scrollBy(step);
        } else if (gridCanvas() && grid.over(x, y)) {
            grid.scrollBy(step, tiles().size());
        } else if (!gridCanvas() && canvas.covers(x, y, canvasLeft(), CANVAS_WIDTH)) {
            if (canvas.scrollBy(amount)) layout();
        } else if (shelfOpen() && shelf.over(x, y)) {
            shelf.scrollBy(step);
        } else if (!scrollArt(x, y, amount) && inspector.covers(x, y, inspectorLeft(), INSPECTOR_WIDTH)) {
            if (inspector.scrollBy(amount)) layout();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && modalOpen()) {
            closeModal();
            return true;
        }
        boolean typing = typingRow() || shelf.typing() || getFocused() instanceof EditBox;
        if (key == GLFW.GLFW_KEY_DELETE && !typing) {
            deleteSelected();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && typing && submit()) return true;
        return super.keyPressed(key, scan, modifiers);
    }
}
