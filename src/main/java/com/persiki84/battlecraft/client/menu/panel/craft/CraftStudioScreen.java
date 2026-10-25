package com.persiki84.battlecraft.client.menu.panel.craft;

import com.persiki84.battlecraft.craft.CraftBanIdentity;
import com.persiki84.battlecraft.craft.CraftMenuState;
import com.persiki84.battlecraft.craft.CraftPhase;
import com.persiki84.shared.client.menu.MenuCommands;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.pick.ItemShelf;
import com.persiki84.shared.client.menu.studio.StudioArtLines;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.client.menu.studio.StudioScreen;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.menu.studio.StudioTile;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.zones.client.menu.ItemTurntable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

// WHY: запреты крафта правятся как витрина: узел слева это фаза, в центре запрещённые предметы
// WHY: плитками, новые тащатся с полки на сетку или прямо на узел нужной фазы, а смена фазы это
// WHY: перенос плитки на другой узел. Команда в чате остаётся точным путём, студия зовёт её же
public final class CraftStudioScreen extends StudioScreen implements CraftRows.Host {
    private static final int ART = 112;
    private static final int REQUEST_TICKS = 20;
    private static final int DRAG_START = 4;
    private static final float TURNTABLE_HEIGHT = 70.0f;
    private static final float LINE_SCALE = 0.72f;
    private final StudioArtLines art = new StudioArtLines(1);
    private static final String HEADING = "phases";
    private static final String CLEAR = "clear-phase";
    private static final Component ADD = Component.translatable("studio.craft.add.button");
    private static final Component DONE = Component.translatable("studio.mode.done");
    private static final Component WAITING = Component.translatable("battlecraft.menu.waiting");
    private static final Component SHELF_HINT = Component.translatable("studio.craft.shelf.hint");
    private static final Component[] EMPTY_BY_PHASE = emptyTexts();
    private static final Predicate<StudioNav.Node> PHASE_NODE = node -> CraftPhase.byId(node.key()) != null;

    private final ItemTurntable turntable = new ItemTurntable();
    private final CraftRows rows = new CraftRows(this);
    private final UiButton modeButton;
    private final List<UiButton> header;
    private CraftView view = CraftView.EMPTY;
    private CompoundTag builtFor;
    private List<StudioNav.Node> nodes = List.of();
    private Component statsText = WAITING;
    private Component artId = Component.empty();
    private CraftPhase phase = CraftPhase.ALWAYS;
    private String selectedKey;
    private String pendingKey;
    private boolean adding;
    private boolean shelfOffered;
    private ItemShelf.Pick shelfPress;
    private double shelfPressX;
    private double shelfPressY;
    private boolean shelfCarried;

    public CraftStudioScreen() {
        this(null);
    }

    public CraftStudioScreen(Screen parent) {
        super(Component.translatable("studio.craft.title"), parent);
        modeButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, ADD, pressed -> toggleShelf())
                .hint("studio.craft.add.button.hint");
        header = List.of(modeButton);
    }

    private static Component[] emptyTexts() {
        CraftPhase[] phases = CraftPhase.values();
        Component[] texts = new Component[phases.length];
        for (CraftPhase each : phases) {
            texts[each.ordinal()] = Component.translatable("studio.craft.empty." + each.id());
        }
        return texts;
    }

    private static CompoundTag state() {
        return MenuData.state(CraftMenuState.MENU_ID);
    }

    @Override
    protected String menuId() {
        return CraftMenuState.MENU_ID;
    }

    @Override
    protected Object stamp() {
        return state();
    }

    private void ensureBuilt() {
        CompoundTag state = state();
        if (state == builtFor) return;
        builtFor = state;
        view = CraftView.of(state);
        nodes = buildNodes(view);
        statsText = view.arrived() ? Component.translatable("studio.craft.stats", view.total(), view.active(),
                Component.translatable(view.running() ? "studio.craft.stats.match" : "studio.craft.stats.lobby"))
                : WAITING;
    }

    private static List<StudioNav.Node> buildNodes(CraftView view) {
        List<StudioNav.Node> built = new ArrayList<>();
        built.add(StudioNav.Node.heading(HEADING, Component.translatable("studio.craft.group.phases")));
        for (CraftPhase each : CraftPhase.values()) {
            built.add(StudioNav.Node.item(each.id(), Component.translatable("studio.craft.node." + each.id()),
                    Component.literal(String.valueOf(view.tiles(each).size())), 0, false));
        }
        return List.copyOf(built);
    }

    @Override
    protected List<StudioNav.Node> nodes() {
        ensureBuilt();
        return nodes;
    }

    @Override
    protected String selectedNode() {
        return phase.id();
    }

    @Override
    protected void selectNode(StudioNav.Node node) {
        CraftPhase picked = CraftPhase.byId(node.key());
        if (picked == null) return;
        phase = picked;
        selectedKey = null;
        grid.reset();
        turntable.rest();
    }

    @Override
    public CraftPhase phase() {
        return phase;
    }

    @Override
    public CraftTile chosen() {
        ensureBuilt();
        return view.find(selectedKey);
    }

    @Override
    protected Component stats() {
        ensureBuilt();
        return statsText;
    }

    @Override
    protected List<UiButton> headerButtons() {
        modeButton.setMessage(adding ? DONE : ADD);
        return header;
    }

    private void toggleShelf() {
        flushCommits();
        adding = !adding;
        turntable.rest();
        layout();
    }

    @Override
    public void openShelf() {
        flushCommits();
        adding = true;
        turntable.rest();
        layout();
    }

    @Override
    protected boolean shelfOpen() {
        return adding;
    }

    @Override
    protected Object inspectorSubject() {
        if (adding) return "shelf:" + phase.id();
        CraftTile tile = chosen();
        return tile == null ? "node:" + phase.id() : "ban:" + tile.key();
    }

    @Override
    protected void buildInspector(StudioStack stack, int x, int width) {
        if (adding) {
            rows.buildShelf(stack, x, width);
            return;
        }
        CraftTile tile = chosen();
        if (tile != null) {
            artId = Component.literal(tile.ban().itemId());
            rows.buildBan(stack, x, width, tile);
            return;
        }
        rows.buildNode(stack, x, width, view.tiles(phase).size(), phase.activeWhen(view.running()));
    }

    @Override
    protected void buildBulk(StudioStack stack, int x, int width, List<Integer> indices) {
        rows.buildBulk(stack, x, width);
    }

    @Override
    protected int artHeight() {
        return adding || chosen() == null ? 0 : ART;
    }

    @Override
    protected void renderArt(GuiGraphics graphics, float left, float top, float width, float appear,
                             int mouseX, int mouseY) {
        CraftTile tile = adding ? null : chosen();
        if (tile == null) return;
        turntable.render(graphics, tile.icon(), left, top, width, TURNTABLE_HEIGHT);
        float y = top + TURNTABLE_HEIGHT + 4.0f;
        UiRender.textTrackedFit(graphics, this.font, tile.name(), left + width / 2.0f, y, 12.0f, width, 0.85f, 0.0f,
                UiTheme.alpha(UiAccent.text(), appear), false);
        int stateColor = tile.active() ? UiPalette.alert() : UiAccent.textDim();
        art.about(tile.key()).line(0, graphics, this.font, tile.plate(), left, y + 15.0f, 10.0f, width, LINE_SCALE,
                UiTheme.alpha(stateColor, appear));
        artLine(graphics, artId, left, width, y + 26.0f, UiTheme.alpha(UiAccent.textDim(), appear));
    }

    private void artLine(GuiGraphics graphics, Component text, float left, float width, float y, int color) {
        UiRender.textTrackedFit(graphics, this.font, text, left + width / 2.0f, y, 10.0f, width, LINE_SCALE, 0.0f,
                color, false);
    }

    @Override
    protected boolean pressArt(double mouseX, double mouseY) {
        if (artHeight() == 0 || !turntable.over(mouseX, mouseY)) return false;
        turntable.beginDrag();
        return true;
    }

    @Override
    protected boolean dragArt(double dragX, double dragY) {
        if (!turntable.dragging()) return false;
        turntable.drag(dragX, dragY);
        return true;
    }

    @Override
    protected void releaseArt() {
        turntable.endDrag();
    }

    @Override
    protected boolean scrollArt(double mouseX, double mouseY, double amount) {
        if (artHeight() == 0 || !turntable.over(mouseX, mouseY)) return false;
        turntable.magnify(amount);
        return true;
    }

    @Override
    protected List<? extends StudioTile> tiles() {
        ensureBuilt();
        return view.tiles(phase);
    }

    @Override
    protected int selectedTile() {
        ensureBuilt();
        return selectedKey == null ? -1 : view.indexOf(phase, selectedKey);
    }

    @Override
    protected void selectTile(int index) {
        adding = false;
        turntable.rest();
        List<CraftTile> shown = view.tiles(phase);
        selectedKey = index < 0 || index >= shown.size() ? null : shown.get(index).key();
    }

    @Override
    protected String tileKey(int index) {
        ensureBuilt();
        List<CraftTile> shown = view.tiles(phase);
        return index >= 0 && index < shown.size() ? shown.get(index).key() : null;
    }

    private List<CraftTile> tilesAt(List<Integer> indices) {
        List<CraftTile> shown = view.tiles(phase);
        List<CraftTile> picked = new ArrayList<>(indices.size());
        for (int index : indices) {
            if (index >= 0 && index < shown.size()) picked.add(shown.get(index));
        }
        return picked;
    }

    @Override
    protected void addPick(ItemShelf.Pick pick, int slot) {
        ban(pick, phase);
    }

    // WHY: из стака уходит только вид предмета (зелье, ствол TACZ): износ, имя и патроны в магазине
    // WHY: не совпали бы ни с одним результатом рецепта, и запрет молча ничего бы не закрывал
    private void ban(ItemShelf.Pick pick, CraftPhase target) {
        if (pick == null || pick.stack().isEmpty()) return;
        String spec = CraftBanIdentity.spec(pick.stack());
        CraftTile known = view.find(spec);
        if (known != null && known.phase() == target) {
            if (target == phase) flashKnown(spec);
            return;
        }
        if (!order(CraftOrders.ban(spec, target))) return;
        if (known != null) nav.nudge(known.phase().id(), -1);
        nav.nudge(target.id(), 1);
        if (target == phase) pendingKey = spec;
    }

    private void flashKnown(String key) {
        int index = view.indexOf(phase, key);
        if (index < 0) return;
        grid.reveal(index, view.tiles(phase).size());
        grid.flash(index);
    }

    @Override
    public void rephaseChosen(CraftPhase target) {
        CraftTile tile = chosen();
        if (tile == null || tile.phase() == target || !rephase(tile, target)) return;
        phase = target;
        pendingKey = tile.key();
        grid.reset();
        layout();
    }

    private boolean rephase(CraftTile tile, CraftPhase target) {
        if (!order(CraftOrders.phase(tile.key(), target))) return false;
        nav.nudge(tile.phase().id(), -1);
        nav.nudge(target.id(), 1);
        return true;
    }

    // WHY: ключ запрета несёт теги вида предмета целиком, и книга с пятью зачарованиями уже не влезает в
    // WHY: строку команды; счётчики узлов и ожидание вспышки двигаются только за ушедшей командой
    private boolean order(String command) {
        if (!MenuCommands.fits(command)) return false;
        send(command);
        return true;
    }

    @Override
    public void unbanChosen() {
        CraftTile tile = chosen();
        if (tile == null) return;
        selectedKey = neighbour(tile.key());
        unban(tile);
        layout();
    }

    private void unban(CraftTile tile) {
        if (order(CraftOrders.unban(tile.key()))) nav.nudge(tile.phase().id(), -1);
    }

    private String neighbour(String removed) {
        List<CraftTile> shown = view.tiles(phase);
        int index = view.indexOf(phase, removed);
        if (index < 0) return null;
        if (index > 0) return shown.get(index - 1).key();
        return shown.size() > 1 ? shown.get(1).key() : null;
    }

    @Override
    public void choosePhase(CraftPhase target) {
        if (target == phase) return;
        phase = target;
        selectedKey = null;
        grid.reset();
        layout();
    }

    @Override
    public void clearPhase() {
        if (!confirm(CLEAR + phase.id())) return;
        send(CraftOrders.clear(phase));
        nav.nudge(phase.id(), -view.tiles(phase).size());
        selectedKey = null;
        layout();
    }

    @Override
    public boolean clearArmed() {
        return armed(CLEAR + phase.id());
    }

    @Override
    protected void deleteSelected() {
        if (!adding) unbanChosen();
    }

    @Override
    protected void deleteMarked(List<Integer> indices) {
        for (CraftTile tile : tilesAt(indices)) {
            unban(tile);
        }
        selectedKey = null;
    }

    @Override
    protected boolean acceptsTile(StudioNav.Node node, int tile) {
        CraftPhase target = CraftPhase.byId(node.key());
        return target != null && target != phase;
    }

    @Override
    protected void dropTile(StudioNav.Node node, int tile) {
        CraftPhase target = node == null ? null : CraftPhase.byId(node.key());
        List<CraftTile> shown = view.tiles(phase);
        if (target == null || target == phase || tile < 0 || tile >= shown.size()) return;
        CraftTile moved = shown.get(tile);
        send(CraftOrders.phase(moved.key(), target));
        if (moved.key().equals(selectedKey)) selectedKey = null;
        layout();
    }

    @Override
    protected void dropTiles(StudioNav.Node node, List<Integer> indices) {
        CraftPhase target = node == null ? null : CraftPhase.byId(node.key());
        if (target == null || target == phase) return;
        List<String> commands = new ArrayList<>();
        for (CraftTile tile : tilesAt(indices)) {
            commands.add(CraftOrders.phase(tile.key(), target));
        }
        sendBatch(commands);
        selectedKey = null;
    }

    @Override
    protected List<StudioMenu.Action> tileActions(int index) {
        List<StudioMenu.Action> actions = new ArrayList<>(moveActions(List.of(index)));
        actions.add(StudioMenu.Action.danger("studio.craft.menu.unban", this::unbanChosen));
        return actions;
    }

    @Override
    protected List<StudioMenu.Action> bulkActions(List<Integer> indices) {
        List<StudioMenu.Action> actions = new ArrayList<>(moveActions(indices));
        actions.add(new StudioMenu.Action(Component.translatable("studio.craft.menu.unban_all", indices.size()),
                () -> unbanAll(indices), true, true, true));
        return actions;
    }

    private List<StudioMenu.Action> moveActions(List<Integer> indices) {
        List<StudioMenu.Action> actions = new ArrayList<>();
        for (CraftPhase target : CraftPhase.values()) {
            if (target == phase) continue;
            Component label = Component.translatable("studio.craft.menu.move",
                    Component.translatable("studio.craft.node." + target.id()));
            actions.add(new StudioMenu.Action(label, () -> moveAll(indices, target), false, false, true));
        }
        return actions;
    }

    private void moveAll(List<Integer> indices, CraftPhase target) {
        List<CraftTile> moved = tilesAt(indices);
        for (CraftTile tile : moved) {
            rephase(tile, target);
            if (tile.key().equals(selectedKey)) selectedKey = null;
        }
        marks.clear();
        layout();
    }

    private void unbanAll(List<Integer> indices) {
        deleteMarked(indices);
        marks.clear();
        layout();
    }

    @Override
    protected List<StudioMenu.Action> nodeActions(StudioNav.Node node) {
        CraftPhase target = CraftPhase.byId(node.key());
        if (target == null) return List.of();
        return List.of(StudioMenu.Action.of("studio.menu.add", this::openShelf),
                StudioMenu.Action.careful("studio.craft.menu.clear", this::clearNow)
                        .when(!view.tiles(target).isEmpty()));
    }

    private void clearNow() {
        send(CraftOrders.clear(phase));
        nav.nudge(phase.id(), -view.tiles(phase).size());
        selectedKey = null;
        layout();
    }

    @Override
    protected List<StudioMenu.Action> canvasActions() {
        return List.of(StudioMenu.Action.of("studio.menu.add", this::openShelf));
    }

    @Override
    protected Component emptyCanvas() {
        ensureBuilt();
        if (!view.arrived()) return WAITING;
        return EMPTY_BY_PHASE[phase.ordinal()];
    }

    @Override
    protected Component shelfHint() {
        return SHELF_HINT;
    }

    // WHY: только что запрещённая вещь находится в первом снимке, где она есть, и вспыхивает на своём
    // WHY: месте: сервер кладёт её в конец списка, а не туда, где её отпустили
    @Override
    protected void refreshed() {
        ensureBuilt();
        offerShelf();
        if (selectedKey != null && view.find(selectedKey) == null) selectedKey = null;
        if (pendingKey == null) return;
        int index = view.indexOf(phase, pendingKey);
        if (index < 0) return;
        grid.reveal(index, view.tiles(phase).size());
        grid.flash(index);
        pendingKey = null;
    }

    private void offerShelf() {
        if (shelfOffered || !view.arrived()) return;
        shelfOffered = true;
        if (view.total() == 0) adding = true;
    }

    @Override
    public void tick() {
        if (ticks % REQUEST_TICKS == 0) MenuData.request(CraftMenuState.MENU_ID);
        super.tick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        shelfPress = null;
        shelfCarried = false;
        double x = localX(mouseX);
        double y = localY(mouseY);
        boolean covered = contextMenu.showing() || modalOpen();
        if (button == 0 && adding && !leaving() && !covered && shelf.over(x, y)) {
            shelfPress = shelf.pick(x, y);
            shelfPressX = x;
            shelfPressY = y;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // WHY: предмет с полки можно бросить и прямо на узел фазы: каркас студии принимает полку только
    // WHY: сеткой, поэтому подсветку узла и бросок ведёт сам экран, не трогая общий перенос
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (shelfPress != null) {
            double x = localX(mouseX);
            double y = localY(mouseY);
            shelfCarried = shelfCarried || Math.abs(x - shelfPressX) > DRAG_START
                    || Math.abs(y - shelfPressY) > DRAG_START;
            if (shelfCarried) nav.hoverDrop(x, y, PHASE_NODE);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        String dropKey = shelfCarried ? nav.dropKey() : null;
        ItemShelf.Pick dropped = shelfPress;
        shelfPress = null;
        shelfCarried = false;
        if (dropKey != null) {
            nav.clearDrop();
            flushCommits();
            ban(dropped, CraftPhase.byId(dropKey));
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
