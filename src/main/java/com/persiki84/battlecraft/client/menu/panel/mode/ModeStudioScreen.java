package com.persiki84.battlecraft.client.menu.panel.mode;

import com.persiki84.battlecraft.mode.ModeId;
import com.persiki84.battlecraft.mode.ModeLimits;
import com.persiki84.battlecraft.mode.ModeMenuState;
import com.persiki84.shared.client.menu.MenuCommands;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.pick.ItemShelf;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.client.menu.studio.StudioScreen;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.menu.studio.StudioTile;
import com.persiki84.shared.client.ui.UiButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// WHY: режим правится как витрина: узел слева это пресет, в центре его стартовый набор плитками
// WHY: (перенос меняет порядок выдачи), справа цифры матча. Команда в чате остаётся точным путём,
// WHY: студия зовёт её же
public final class ModeStudioScreen extends StudioScreen implements ModeRows.Host {
    private static final int REQUEST_TICKS = 20;
    private static final String HEADING = "modes";
    private static final String CLEAR = "clear-kit:";
    private static final String RESET = "reset-mode:";
    private static final Component ADD = Component.translatable("studio.modes.add.button");
    private static final Component DONE = Component.translatable("studio.mode.done");
    private static final Component WAITING = Component.translatable("battlecraft.menu.waiting");
    private static final Component EMPTY_KIT = Component.translatable("studio.modes.kit.empty");
    private static final Component SHELF_HINT = Component.translatable("studio.modes.shelf.hint");

    private final ModeRows rows = new ModeRows(this);
    private final UiButton addButton;
    private final List<UiButton> header;
    private ModeView view = ModeView.EMPTY;
    private CompoundTag builtFor;
    private List<StudioNav.Node> nodes = List.of();
    private Component statsText = WAITING;
    private ModeId mode = ModeId.MAIN;
    private int selected = -1;
    private boolean adding;

    public ModeStudioScreen() {
        this(null);
    }

    public ModeStudioScreen(Screen parent) {
        super(Component.translatable("studio.modes.title"), parent);
        addButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, ADD, pressed -> toggleShelf())
                .hint("studio.modes.add.button.hint");
        header = List.of(addButton);
    }

    private static CompoundTag state() {
        return MenuData.state(ModeMenuState.MENU_ID);
    }

    @Override
    protected String menuId() {
        return ModeMenuState.MENU_ID;
    }

    @Override
    protected Object stamp() {
        return state();
    }

    private void ensureBuilt() {
        CompoundTag state = state();
        if (state == builtFor) return;
        builtFor = state;
        view = ModeView.of(state);
        nodes = buildNodes(view);
        statsText = view.arrived() ? Component.translatable("studio.modes.stats",
                Component.translatable(view.active().translationKey()),
                Component.translatable(view.locked() ? "studio.modes.stats.locked" : "studio.modes.stats.vote"))
                : WAITING;
    }

    private static List<StudioNav.Node> buildNodes(ModeView view) {
        List<StudioNav.Node> built = new ArrayList<>();
        built.add(StudioNav.Node.heading(HEADING, Component.translatable("studio.modes.group.modes")));
        for (ModeId id : ModeId.values()) {
            built.add(StudioNav.Node.item(id.id(), Component.translatable(id.translationKey()),
                    Component.literal(String.valueOf(view.settings(id).kit().size())), 0, false));
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
        return mode.id();
    }

    @Override
    protected void selectNode(StudioNav.Node node) {
        ModeId picked = ModeId.byId(node.key());
        if (picked == null || picked == mode) return;
        flushCommits();
        mode = picked;
        selected = -1;
        grid.reset();
    }

    @Override
    protected Component stats() {
        ensureBuilt();
        return statsText;
    }

    @Override
    protected List<UiButton> headerButtons() {
        addButton.setMessage(adding ? DONE : ADD);
        return header;
    }

    private void toggleShelf() {
        flushCommits();
        adding = !adding;
        layout();
    }

    @Override
    public void openShelf() {
        flushCommits();
        adding = true;
        layout();
    }

    @Override
    protected boolean shelfOpen() {
        return adding;
    }

    @Override
    protected Object inspectorSubject() {
        if (adding) return "shelf:" + mode.id();
        KitTile tile = chosen();
        return tile == null ? "mode:" + mode.id() : "kit:" + mode.id() + ":" + tile.key();
    }

    @Override
    protected void buildInspector(StudioStack stack, int x, int width) {
        ensureBuilt();
        if (adding) {
            rows.buildShelf(stack, x, width);
            return;
        }
        KitTile tile = chosen();
        if (tile != null) {
            rows.buildKit(stack, x, width, tile);
            return;
        }
        rows.buildMode(stack, x, width);
    }

    private List<KitTile> kit() {
        return view.settings(mode).kit();
    }

    @Override
    protected List<? extends StudioTile> tiles() {
        ensureBuilt();
        return kit();
    }

    @Override
    protected int selectedTile() {
        ensureBuilt();
        return selected >= 0 && selected < kit().size() ? selected : -1;
    }

    @Override
    protected void selectTile(int index) {
        flushCommits();
        adding = false;
        selected = index;
    }

    @Override
    protected String tileKey(int index) {
        List<KitTile> shown = kit();
        return index >= 0 && index < shown.size() ? shown.get(index).key() : null;
    }

    @Override
    protected boolean tilesMovable() {
        return true;
    }

    // WHY: отложенная правка количества ключится номером позиции: после переноса она ушла бы в чужую
    @Override
    protected void moveTile(int from, int to) {
        if (from == to) return;
        flushCommits();
        send(ModeOrders.kitMove(mode, from, to));
        selected = to;
    }

    // WHY: из инвентаря уходит только номер слота, стак сервер берёт сам: ствол TACZ с тегом не влезает в
    // WHY: строку команды; из каталога уходит вид предмета по одному, количество правится в инспекторе.
    // WHY: Позиция встаёт в конец и сразу переносится к месту броска: команды доходят по порядку
    @Override
    protected void addPick(ItemShelf.Pick pick, int slot) {
        if (pick == null || pick.stack().isEmpty()) return;
        int total = kit().size();
        if (total >= ModeLimits.KIT_ENTRIES_MAX) {
            MenuFeedback.show(Component.translatable("battlecraft.mode.kit.full", ModeLimits.KIT_ENTRIES_MAX), true);
            return;
        }
        String command = pick.fromInventory() ? ModeOrders.kitSlot(mode, pick.slot())
                : ModeOrders.kitItem(mode, ItemShelf.spec(pick.stack()), 1);
        if (!MenuCommands.fits(command)) return;
        send(command);
        if (slot >= 0 && slot < total) {
            send(ModeOrders.kitMove(mode, total, slot));
            selected = slot;
        }
        nav.nudge(mode.id(), 1);
    }

    @Override
    protected void deleteSelected() {
        if (!adding) removeChosen();
    }

    @Override
    protected List<StudioMenu.Action> tileActions(int index) {
        return List.of(StudioMenu.Action.danger("studio.modes.menu.remove", this::removeChosen));
    }

    @Override
    protected List<StudioMenu.Action> bulkActions(List<Integer> indices) {
        return List.of(new StudioMenu.Action(Component.translatable("studio.menu.delete_many", indices.size()),
                () -> removeMarked(indices), true, true, true));
    }

    @Override
    protected void deleteMarked(List<Integer> indices) {
        removeMarked(indices);
    }

    // WHY: позиции снимаются с конца: сервер сдвигает хвост набора после каждого удаления
    private void removeMarked(List<Integer> indices) {
        flushCommits();
        List<Integer> order = new ArrayList<>(indices);
        order.sort(Comparator.reverseOrder());
        for (int index : order) {
            send(ModeOrders.kitRemove(mode, index));
        }
        nav.nudge(mode.id(), -order.size());
        selected = -1;
        marks.clear();
        layout();
    }

    @Override
    protected List<StudioMenu.Action> canvasActions() {
        return List.of(StudioMenu.Action.of("studio.menu.add", this::openShelf));
    }

    @Override
    protected Component emptyCanvas() {
        ensureBuilt();
        return view.arrived() ? EMPTY_KIT : WAITING;
    }

    @Override
    protected Component shelfHint() {
        return SHELF_HINT;
    }

    @Override
    protected void refreshed() {
        ensureBuilt();
        if (selected >= kit().size()) selected = -1;
    }

    @Override
    public void tick() {
        if (ticks % REQUEST_TICKS == 0) MenuData.request(ModeMenuState.MENU_ID);
        super.tick();
    }

    @Override
    public ModeId mode() {
        return mode;
    }

    @Override
    public ModeView view() {
        ensureBuilt();
        return view;
    }

    @Override
    public KitTile chosen() {
        int index = selectedTile();
        return index < 0 ? null : kit().get(index);
    }

    @Override
    public void later(String key, String command) {
        commitLater(key, command);
    }

    @Override
    public void now(String command) {
        send(command);
    }

    @Override
    public void clearKit() {
        if (!confirm(CLEAR + mode.id())) return;
        send(ModeOrders.kitClear(mode));
        nav.nudge(mode.id(), -kit().size());
        selected = -1;
        layout();
    }

    @Override
    public boolean clearArmed() {
        return armed(CLEAR + mode.id());
    }

    @Override
    public void resetMode() {
        if (!confirm(RESET + mode.id())) return;
        send(ModeOrders.reset(mode));
        layout();
    }

    @Override
    public boolean resetArmed() {
        return armed(RESET + mode.id());
    }

    // WHY: удаление ставит выбор на соседа, чтобы клавиша Delete шла по набору подряд. Набранное, но не
    // WHY: подтверждённое количество уходит раньше удаления, пока выбор ещё на этой позиции
    @Override
    public void removeChosen() {
        setFocused(null);
        flushCommits();
        KitTile tile = chosen();
        if (tile == null) return;
        send(ModeOrders.kitRemove(mode, tile.index()));
        nav.nudge(mode.id(), -1);
        selected = kit().size() > 1 ? Math.max(0, tile.index() - 1) : -1;
        layout();
    }
}
