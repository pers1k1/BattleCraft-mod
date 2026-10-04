package com.persiki84.zones.client.menu;

import com.persiki84.minimap.client.MapCanvas;
import com.persiki84.minimap.client.MapStudioScreen;
import com.persiki84.minimap.client.MapThing;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.PaletteWindow;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.zones.mark.MarkHideZone;
import com.persiki84.zones.mark.MarkKind;
import com.persiki84.zones.mark.MarkMenuState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

// WHY: метка это место на карте, и правится она на карте: тащится мышью, зона скрытия видна
// WHY: кругом вокруг неё, а подпись, цвет и кто её видит лежат справа одной колонкой
public final class MarkStudioScreen extends MapStudioScreen {
    static final String COMMAND = "battlecraft mark ";

    private static final int ART = 40;
    private static final float LINE_SCALE = 0.72f;

    private final MarkInspector rows = new MarkInspector(this);
    private final UiButton addButton;
    private final UiButton meButton;
    private final List<UiButton> header;
    private String chosenId;
    private boolean creating;
    private int newX;
    private int newZ;
    private String pendingId;
    private CompoundTag builtFor;
    private List<MarkEntry> entries = List.of();
    private List<StudioNav.Node> nodes = List.of();
    private List<MapThing> things = List.of();

    public MarkStudioScreen() {
        this(null);
    }

    public MarkStudioScreen(Screen parent) {
        super(Component.translatable("zones.mark.menu.title"), parent);
        addButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.empty(), pressed -> toggleCreate())
                .hint("studio.marks.add.hint");
        meButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.translatable("studio.map.me"),
                pressed -> centerOnPlayer()).hint("studio.map.me.hint");
        header = List.of(addButton, meButton);
    }

    static CompoundTag state() {
        return MenuData.state(MarkMenuState.MENU_ID);
    }

    @Override
    protected String menuId() {
        return MarkMenuState.MENU_ID;
    }

    @Override
    protected Object stamp() {
        return state();
    }

    private void ensureBuilt() {
        CompoundTag state = state();
        if (state == builtFor) return;
        builtFor = state;
        ListTag stored = state.getList(MarkMenuState.MARKS, Tag.TAG_COMPOUND);
        List<MarkEntry> built = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            CompoundTag tag = stored.getCompound(index);
            built.add(new MarkEntry(tag.getString("id"), tag));
        }
        entries = built;
        nodes = buildNodes(built);
        things = buildThings(built);
    }

    private static List<StudioNav.Node> buildNodes(List<MarkEntry> built) {
        List<StudioNav.Node> list = new ArrayList<>();
        for (MarkKind kind : MarkKind.values()) {
            list.add(StudioNav.Node.heading("heading:" + kind.id(), Component.translatable("studio.marks.node." + kind.id())));
            for (MarkEntry entry : built) {
                if (entry.kind() != kind) continue;
                list.add(StudioNav.Node.item(entry.id(), Component.literal(entry.label()), Component.empty(), 0, false));
            }
        }
        return list;
    }

    // WHY: у метки с зоной скрытия на карте видна и сама зона: внутри неё метка гаснет в HUD, и
    // WHY: без круга это пришлось бы представлять по числу радиуса
    private static List<MapThing> buildThings(List<MarkEntry> built) {
        List<MapThing> list = new ArrayList<>();
        for (MarkEntry entry : built) {
            int radius = Math.max(MarkHideZone.OFF, entry.number("hideRadius"));
            list.add(new MapThing(entry.id(), Component.literal(entry.label()), entry.number("x"), entry.number("y"),
                    entry.number("z"), entry.hideShape(), radius > MarkHideZone.OFF ? radius : MapThing.PIN_ONLY,
                    entry.color(), entry.here()));
        }
        return list;
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
        return chosenId;
    }

    @Override
    protected void selectNode(StudioNav.Node node) {
        chosenId = node.key();
        creating = false;
    }

    @Override
    protected String chosenThing() {
        return creating ? null : chosenId;
    }

    @Override
    protected void chooseThing(String key) {
        chosenId = key;
        creating = false;
    }

    MarkEntry entry() {
        ensureBuilt();
        if (creating || chosenId == null) return null;
        for (MarkEntry entry : entries) {
            if (entry.id().equals(chosenId)) return entry;
        }
        return null;
    }

    boolean known(String id) {
        ensureBuilt();
        for (MarkEntry entry : entries) {
            if (entry.id().equals(id)) return true;
        }
        return false;
    }

    boolean creating() {
        return creating;
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
        return Component.translatable("studio.marks.stats", entries.size());
    }

    @Override
    protected List<UiButton> headerButtons() {
        addButton.setMessage(Component.translatable(creating ? "studio.points.cancel" : "studio.marks.add"));
        return header;
    }

    private void toggleCreate() {
        if (creating) {
            creating = false;
            layout();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) startCreate(minecraft.player.getBlockX(), minecraft.player.getBlockZ());
    }

    void startCreate(int x, int z) {
        flushCommits();
        creating = true;
        newX = x;
        newZ = z;
        rows.beginCreate(freeId());
        layout();
    }

    private String freeId() {
        for (int number = 1; ; number++) {
            String id = "mark_" + number;
            if (!known(id)) return id;
        }
    }

    @Override
    protected MapThing ghost() {
        if (!creating) return null;
        String label = rows.typedLabel();
        Component name = label.isBlank() ? Component.translatable("studio.marks.new_label") : Component.literal(label);
        return new MapThing("new", name, newX, MapCanvas.surfaceY(newX, newZ), newZ, null, MapThing.PIN_ONLY,
                UiAccent.color(), true);
    }

    @Override
    protected void placeGhost(int x, int z) {
        newX = x;
        newZ = z;
    }

    void placeGhostAt(int x, int z) {
        placeGhost(x, z);
    }

    void create(String id, String label, MarkKind kind) {
        if (known(id)) {
            MenuFeedback.show(Component.translatable("zones.mark.error.exists", id), true);
            return;
        }
        now(COMMAND + "create " + id + " at " + newX + " " + MapCanvas.surfaceY(newX, newZ) + " " + newZ
                + (label.isEmpty() ? "" : " " + label));
        if (kind != MarkKind.DEFAULT) now(COMMAND + "edit " + id + " kind " + kind.id());
        pendingId = id;
        chosenId = id;
        creating = false;
        layout();
    }

    @Override
    protected void moveThing(MapThing thing, int x, int z) {
        now(COMMAND + "edit " + thing.key() + " at " + x + " " + thing.y() + " " + z);
    }

    @Override
    protected Object inspectorSubject() {
        return creating ? "new" : chosenId;
    }

    @Override
    protected void buildInspector(StudioStack stack, int x, int width) {
        rows.build(stack, x, width);
    }

    @Override
    protected int artHeight() {
        return creating || entry() != null ? ART : 0;
    }

    @Override
    protected void renderArt(GuiGraphics graphics, float left, float top, float width, float appear,
                             int mouseX, int mouseY) {
        MarkEntry entry = entry();
        Component title = creating ? Component.translatable("studio.marks.group.new")
                : entry == null ? null : Component.literal(entry.label());
        if (title == null) return;
        int tone = creating || entry == null ? UiAccent.text() : 0xFF000000 | entry.color();
        UiRender.textTrackedFit(graphics, this.font, title, left + width / 2.0f, top, 14.0f, width, 0.95f, 0.0f,
                UiTheme.alpha(tone, appear), false);
        UiRender.textTrackedFit(graphics, this.font, artDetail(entry), left + width / 2.0f, top + 16.0f, 10.0f, width,
                LINE_SCALE, 0.0f, UiTheme.alpha(UiAccent.textDim(), appear), false);
    }

    private Component artDetail(MarkEntry entry) {
        if (creating || entry == null) return Component.translatable("studio.points.place", newX, newZ);
        Component where = entry.here() ? Component.translatable("studio.points.at", entry.number("x"),
                entry.number("y"), entry.number("z")) : Component.translatable("studio.map.elsewhere");
        return Component.translatable("studio.points.detail", Component.literal(entry.id()), where);
    }

    @Override
    protected List<StudioMenu.Action> thingActions(MapThing thing) {
        return actionsFor(thing.key());
    }

    @Override
    protected List<StudioMenu.Action> nodeActions(StudioNav.Node node) {
        return actionsFor(node.key());
    }

    private List<StudioMenu.Action> actionsFor(String id) {
        if (!known(id)) return List.of(StudioMenu.Action.of("studio.marks.add", this::toggleCreate));
        return List.of(StudioMenu.Action.of("studio.menu.tp", () -> now(COMMAND + "tp " + id)),
                StudioMenu.Action.of("studio.menu.move_here", () -> now(COMMAND + "edit " + id + " here")),
                StudioMenu.Action.of("studio.menu.color", () -> rows.openColor(entry())),
                StudioMenu.Action.careful("studio.menu.delete_mark", () -> remove(id)));
    }

    @Override
    protected List<StudioMenu.Action> groundActions(int x, int z) {
        return List.of(StudioMenu.Action.of("studio.menu.new_mark_here", () -> startCreate(x, z)),
                StudioMenu.Action.of("studio.map.me", this::centerOnPlayer));
    }

    void remove(String id) {
        now(COMMAND + "delete " + id);
        chosenId = neighbour(id);
        layout();
    }

    private String neighbour(String removed) {
        for (int index = 0; index < entries.size(); index++) {
            if (!entries.get(index).id().equals(removed)) continue;
            if (index > 0) return entries.get(index - 1).id();
            return entries.size() > 1 ? entries.get(1).id() : null;
        }
        return null;
    }

    @Override
    protected void deleteSelected() {
        MarkEntry entry = entry();
        if (entry == null) return;
        if (confirm("delete:" + entry.id())) {
            remove(entry.id());
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
        if (pendingId != null && known(pendingId)) {
            chosenId = pendingId;
            pendingId = null;
        }
        if (chosenId == null && !entries.isEmpty()) chosenId = entries.get(0).id();
        rows.fill();
    }

    @Override
    public void tick() {
        if (ticks % 20 == 0) MenuData.request(MarkMenuState.MENU_ID);
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

    void openPalette(String owner, Component title, int color, IntConsumer apply, Runnable clear) {
        palette(owner, PaletteWindow.Kind.SERVER, title, color, apply, clear);
    }
}
