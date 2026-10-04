package com.persiki84.capturepoints.client.menu;

import com.persiki84.capturepoints.menu.CapturePointMenuState;
import com.persiki84.minimap.client.MapCanvas;
import com.persiki84.minimap.client.MapRenderUtil;
import com.persiki84.minimap.client.MapStudioScreen;
import com.persiki84.minimap.client.MapThing;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.pick.ItemShelf;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.zone.ZoneShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// WHY: точки правятся там, где стоят: на карте их видно областью захвата, выбранная тащится мышью
// WHY: на новое место, справа сразу все её свойства, а награда и доход лежат самим предметом
public final class PointStudioScreen extends MapStudioScreen {
    static final String MENU_ID = CapturePointMenuState.MENU_ID;
    static final String COMMON = "common";
    static final String REWARD = "reward";
    static final String INCOME = "income";

    private static final int ART = 40;
    private static final int NEUTRAL = 0xFFC8CCD4;
    private static final float LINE_SCALE = 0.72f;

    private final PointInspector rows = new PointInspector(this);
    private final UiButton addButton;
    private final UiButton meButton;
    private final List<UiButton> header;
    private String chosenKey;
    private boolean creating;
    private int newX;
    private int newZ;
    private String shelfFor;
    private String pendingName;
    private CompoundTag builtFor;
    private List<PointEntry> entries = List.of();
    private List<StudioNav.Node> nodes = List.of();
    private List<MapThing> things = List.of();

    public PointStudioScreen() {
        this(null);
    }

    public PointStudioScreen(Screen parent) {
        super(Component.translatable("capturepoints.menu.title"), parent);
        addButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.empty(), pressed -> toggleCreate())
                .hint("studio.points.add.hint");
        meButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.translatable("studio.map.me"),
                pressed -> centerOnPlayer()).hint("studio.map.me.hint");
        header = List.of(addButton, meButton);
    }

    static CompoundTag state() {
        return MenuData.state(MENU_ID);
    }

    @Override
    protected String menuId() {
        return MENU_ID;
    }

    @Override
    protected Object stamp() {
        return state();
    }

    private void ensureBuilt() {
        CompoundTag state = state();
        if (state == builtFor) return;
        builtFor = state;
        ListTag stored = state.getList(CapturePointMenuState.POINTS, Tag.TAG_COMPOUND);
        List<PointEntry> built = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            built.add(PointEntry.of(stored.getCompound(index)));
        }
        entries = built;
        nodes = buildNodes(built);
        things = buildThings(built);
    }

    private static List<StudioNav.Node> buildNodes(List<PointEntry> built) {
        List<StudioNav.Node> list = new ArrayList<>();
        list.add(StudioNav.Node.heading("heading:points", Component.translatable("studio.points.node.points")));
        addPoints(list, built, false);
        list.add(StudioNav.Node.heading("heading:finals", Component.translatable("studio.points.node.finals")));
        addPoints(list, built, true);
        list.add(StudioNav.Node.heading("heading:rules", Component.translatable("studio.points.node.rules")));
        list.add(StudioNav.Node.item(COMMON, Component.translatable("studio.points.node.common"), Component.empty(),
                0, false));
        return list;
    }

    private static void addPoints(List<StudioNav.Node> list, List<PointEntry> built, boolean last) {
        for (PointEntry entry : built) {
            if (entry.last() != last) continue;
            Component owner = entry.owner().isEmpty() ? Component.empty() : Component.literal(entry.owner());
            list.add(StudioNav.Node.item(entry.key(), Component.literal(entry.name()), owner, 0, false));
        }
    }

    private static List<MapThing> buildThings(List<PointEntry> built) {
        List<MapThing> list = new ArrayList<>();
        for (PointEntry entry : built) {
            list.add(new MapThing(entry.key(), Component.literal(entry.name()), entry.number("x"), entry.number("y"),
                    entry.number("z"), entry.shape(), entry.number("size"), colorOf(entry), entry.here()));
        }
        return list;
    }

    private static int colorOf(PointEntry entry) {
        if (entry.last()) return UiAccent.color();
        return entry.owner().isEmpty() ? NEUTRAL : MapRenderUtil.getTeamColor(entry.owner());
    }

    @Override
    protected List<StudioNav.Node> nodes() {
        ensureBuilt();
        return nodes;
    }

    @Override
    protected List<MapThing> things() {
        ensureBuilt();
        return things;
    }

    @Override
    protected String selectedNode() {
        return chosenKey;
    }

    @Override
    protected void selectNode(StudioNav.Node node) {
        chosenKey = node.key();
        creating = false;
        shelfFor = null;
    }

    @Override
    protected String chosenThing() {
        return chosenKey != null && chosenKey.startsWith(PointEntry.PREFIX) ? chosenKey : null;
    }

    @Override
    protected void chooseThing(String key) {
        chosenKey = key;
        creating = false;
        shelfFor = null;
    }

    PointEntry entry() {
        ensureBuilt();
        if (creating || chosenKey == null) return null;
        for (PointEntry entry : entries) {
            if (entry.key().equals(chosenKey)) return entry;
        }
        return null;
    }

    PointEntry live(String name) {
        ensureBuilt();
        for (PointEntry entry : entries) {
            if (entry.name().equals(name)) return entry;
        }
        return null;
    }

    boolean commonChosen() {
        return !creating && COMMON.equals(chosenKey);
    }

    boolean creating() {
        return creating;
    }

    String shelfFor() {
        return shelfFor;
    }

    int newX() {
        return newX;
    }

    int newZ() {
        return newZ;
    }

    @Override
    protected Component stats() {
        if (state().isEmpty()) return Component.translatable("battlecraft.menu.waiting");
        ensureBuilt();
        int finals = 0;
        for (PointEntry entry : entries) {
            if (entry.last()) finals++;
        }
        return Component.translatable("studio.points.stats", entries.size() - finals, finals);
    }

    @Override
    protected List<UiButton> headerButtons() {
        addButton.setMessage(Component.translatable(creating ? "studio.points.cancel" : "studio.points.add"));
        return header;
    }

    private void toggleCreate() {
        if (creating) {
            creating = false;
            layout();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        startCreate(minecraft.player.getBlockX(), minecraft.player.getBlockZ());
    }

    void startCreate(int x, int z) {
        flushCommits();
        creating = true;
        shelfFor = null;
        newX = x;
        newZ = z;
        rows.beginCreate();
        layout();
    }

    @Override
    protected MapThing ghost() {
        if (!creating) return null;
        String typed = rows.typedName();
        Component name = typed.isBlank() ? Component.translatable("studio.points.new_name") : Component.literal(typed);
        return new MapThing("new", name, newX, MapCanvas.surfaceY(newX, newZ), newZ, rows.newShape(),
                rows.newRadius(), UiAccent.color(), true);
    }

    @Override
    protected void placeGhost(int x, int z) {
        newX = x;
        newZ = z;
    }

    void placeGhostAt(int x, int z) {
        placeGhost(x, z);
    }

    void create(String name, boolean last, int radius, int seconds, int cooldown, ZoneShape shape) {
        if (live(name) != null) {
            MenuFeedback.show(Component.translatable("capturepoints.menu.error.exists", name), true);
            return;
        }
        String root = last ? "finalpoint" : "capturepoint";
        now(root + " create \"" + name + "\" " + newX + " " + MapCanvas.surfaceY(newX, newZ) + " " + newZ + " "
                + radius + " " + seconds + " " + cooldown + " " + shape.id());
        pendingName = name;
        creating = false;
        layout();
    }

    @Override
    protected void moveThing(MapThing thing, int x, int z) {
        PointEntry moved = entryByKey(thing.key());
        if (moved == null) return;
        now(moved.root() + " setposition " + moved.quoted() + " " + x + " " + thing.y() + " " + z);
    }

    private PointEntry entryByKey(String key) {
        ensureBuilt();
        for (PointEntry entry : entries) {
            if (entry.key().equals(key)) return entry;
        }
        return null;
    }

    @Override
    protected Object inspectorSubject() {
        if (creating) return "new";
        if (shelfFor != null) return "shelf:" + shelfFor + ":" + chosenKey;
        return chosenKey;
    }

    @Override
    protected void buildInspector(StudioStack stack, int x, int width) {
        rows.build(stack, x, width);
    }

    @Override
    protected boolean shelfOpen() {
        return shelfFor != null && entry() != null;
    }

    void openShelf(String which) {
        flushCommits();
        shelfFor = which;
        layout();
    }

    void closeShelf() {
        shelfFor = null;
        layout();
    }

    @Override
    protected void addPick(ItemShelf.Pick pick, int slot) {
        PointEntry entry = entry();
        if (entry == null || shelfFor == null) return;
        boolean reward = REWARD.equals(shelfFor);
        int amount = Math.max(1, entry.number(reward ? "rewardAmount" : "incomeAmount"));
        now(entry.root() + (reward ? " setreward " : " setincome ") + entry.quoted() + " "
                + ItemShelf.spec(pick.stack()) + " " + amount);
        closeShelf();
    }

    @Override
    protected Component shelfHint() {
        return Component.translatable("studio.points.shelf.hint");
    }

    @Override
    protected int artHeight() {
        return shelfOpen() ? 0 : ART;
    }

    @Override
    protected void renderArt(GuiGraphics graphics, float left, float top, float width, float appear,
                             int mouseX, int mouseY) {
        Component title = artTitle();
        if (title == null) return;
        UiRender.textTrackedFit(graphics, this.font, title, left + width / 2.0f, top, 14.0f, width, 0.95f, 0.0f,
                UiTheme.alpha(UiAccent.text(), appear), false);
        UiRender.textTrackedFit(graphics, this.font, artDetail(), left + width / 2.0f, top + 16.0f, 10.0f, width,
                LINE_SCALE, 0.0f, UiTheme.alpha(UiAccent.textDim(), appear), false);
    }

    private Component artTitle() {
        if (creating) return Component.translatable("studio.points.group.new");
        if (commonChosen()) return Component.translatable("studio.points.node.common");
        PointEntry entry = entry();
        return entry == null ? null : Component.literal(entry.name());
    }

    private Component artDetail() {
        if (creating) return Component.translatable("studio.points.place", newX, newZ);
        if (commonChosen()) return Component.translatable("studio.points.common.detail");
        PointEntry entry = entry();
        if (entry == null) return Component.empty();
        Component kind = Component.translatable(entry.last() ? "capturepoints.menu.kind.final"
                : "capturepoints.menu.kind.point");
        Component where = entry.here() ? Component.translatable("studio.points.at", entry.number("x"),
                entry.number("y"), entry.number("z")) : Component.translatable("studio.map.elsewhere");
        return Component.translatable("studio.points.detail", kind, where);
    }

    @Override
    protected List<StudioMenu.Action> thingActions(MapThing thing) {
        return actionsFor(entryByKey(thing.key()));
    }

    @Override
    protected List<StudioMenu.Action> nodeActions(StudioNav.Node node) {
        if (COMMON.equals(node.key())) return List.of(StudioMenu.Action.of("studio.points.add", this::toggleCreate));
        return actionsFor(entryByKey(node.key()));
    }

    private List<StudioMenu.Action> actionsFor(PointEntry entry) {
        if (entry == null) return List.of();
        List<StudioMenu.Action> actions = new ArrayList<>();
        actions.add(StudioMenu.Action.of("studio.menu.tp", () -> teleport(entry)));
        actions.add(StudioMenu.Action.of("studio.menu.move_here", () -> moveHere(entry)));
        actions.add(StudioMenu.Action.of("studio.menu.reward", () -> openShelf(REWARD)));
        if (!entry.last()) actions.add(StudioMenu.Action.of("studio.menu.income", () -> openShelf(INCOME)));
        if (!entry.owner().isEmpty()) {
            actions.add(StudioMenu.Action.of("studio.menu.clear_owner",
                    () -> now(entry.root() + " clearowner " + entry.quoted())));
        }
        if (!entry.last()) {
            actions.add(StudioMenu.Action.of("capturepoints.menu.reset_cooldown",
                    () -> now("resetcapturecooldown " + entry.quoted())));
        }
        actions.add(StudioMenu.Action.careful("studio.menu.delete_point", () -> remove(entry)));
        return actions;
    }

    @Override
    protected List<StudioMenu.Action> groundActions(int x, int z) {
        return List.of(StudioMenu.Action.of("studio.menu.new_point_here", () -> startCreate(x, z)),
                StudioMenu.Action.of("studio.map.me", this::centerOnPlayer));
    }

    void teleport(PointEntry entry) {
        now("execute in " + entry.tag().getString("dimension") + " run tp @s " + entry.number("x") + " "
                + entry.number("y") + " " + entry.number("z"));
    }

    void moveHere(PointEntry entry) {
        now(entry.root() + " setposition " + entry.quoted() + " ~ ~ ~");
    }

    void remove(PointEntry entry) {
        now(entry.root() + " remove " + entry.quoted());
        chosenKey = neighbour(entry.key());
        layout();
    }

    // WHY: точки удаляют подряд, и прыжок к первой означал бы листать список заново
    private String neighbour(String removed) {
        String previous = null;
        for (PointEntry entry : entries) {
            if (entry.key().equals(removed)) return previous != null ? previous : nextAfter(removed);
            previous = entry.key();
        }
        return null;
    }

    private String nextAfter(String removed) {
        for (int index = 0; index < entries.size() - 1; index++) {
            if (entries.get(index).key().equals(removed)) return entries.get(index + 1).key();
        }
        return null;
    }

    @Override
    protected void deleteSelected() {
        PointEntry entry = entry();
        if (entry == null) return;
        if (confirm("delete:" + entry.key())) {
            remove(entry);
            return;
        }
        MenuFeedback.show(Component.translatable("studio.delete_again"), false);
    }

    @Override
    protected boolean submit() {
        return rows.submit() || super.submit();
    }

    @Override
    protected Component mapHint() {
        return Component.translatable(creating ? "studio.map.hint.place" : "studio.map.hint");
    }

    @Override
    protected void refreshed() {
        ensureBuilt();
        if (pendingName != null && live(pendingName) != null) {
            chosenKey = PointEntry.PREFIX + pendingName;
            pendingName = null;
        }
        if (chosenKey == null && !entries.isEmpty()) chosenKey = entries.get(0).key();
        rows.fill();
    }

    @Override
    public void tick() {
        if (ticks % 20 == 0) MenuData.request(MENU_ID);
        super.tick();
    }

    void now(String command) {
        send(command);
    }

    void later(String key, String command) {
        commitLater(key, command);
    }

    boolean pending(String key) {
        return committing(key);
    }

    boolean isArmed(String key) {
        return armed(key);
    }

    boolean confirmed(String key) {
        return confirm(key);
    }

    void relayout() {
        layout();
    }
}
