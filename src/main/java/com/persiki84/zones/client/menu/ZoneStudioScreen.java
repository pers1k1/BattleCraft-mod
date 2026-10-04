package com.persiki84.zones.client.menu;

import com.persiki84.minimap.client.MapCanvas;
import com.persiki84.minimap.client.MapStudioScreen;
import com.persiki84.minimap.client.MapThing;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.PaletteWindow;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.zone.ZoneShape;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneSource;
import com.persiki84.zones.ZoneType;
import com.persiki84.zones.ZonesMod;
import com.persiki84.zones.client.ClientZoneData;
import com.persiki84.zones.client.render.ZoneColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

// WHY: зоны правятся на карте: база и магазин видны своей областью и цветом, выбранная тащится на
// WHY: новое место, правила зоны лежат в той же колонке, что и её форма, а не на отдельной вкладке
public final class ZoneStudioScreen extends MapStudioScreen {
    static final String COMMAND = "battlecraft zone ";

    private static final int ART = 40;
    private static final float LINE_SCALE = 0.72f;

    private final ZoneInspector rows = new ZoneInspector(this);
    private final UiButton addButton;
    private final UiButton meButton;
    private final List<UiButton> header;
    private String chosenId;
    private boolean creating;
    private int newX;
    private int newZ;
    private String pendingId;
    private List<Zone> builtFor;
    private List<Zone> zones = List.of();
    private List<StudioNav.Node> nodes = List.of();
    private List<MapThing> things = List.of();

    public ZoneStudioScreen() {
        this(null);
    }

    public ZoneStudioScreen(Screen parent) {
        super(Component.translatable("zones.menu.title"), parent);
        addButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.empty(), pressed -> toggleCreate())
                .hint("studio.zones.add.hint");
        meButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.translatable("studio.map.me"),
                pressed -> centerOnPlayer()).hint("studio.map.me.hint");
        header = List.of(addButton, meButton);
    }

    @Override
    protected String menuId() {
        return ZonesMod.ZONES_MENU_ID;
    }

    // WHY: зоны приходят пакетами по одной, и каждая заменяет объект целиком: список ссылок и есть
    // WHY: дешёвый штамп, сравнение идёт по тождеству объектов
    @Override
    protected Object stamp() {
        return stored();
    }

    private static List<Zone> stored() {
        List<Zone> list = new ArrayList<>();
        for (Zone zone : ClientZoneData.all()) {
            if (zone.source() == ZoneSource.STORED) list.add(zone);
        }
        return list;
    }

    private void ensureBuilt() {
        List<Zone> current = stored();
        if (current.equals(builtFor)) return;
        builtFor = current;
        zones = current;
        nodes = buildNodes(current);
        things = buildThings(current);
    }

    private static List<StudioNav.Node> buildNodes(List<Zone> current) {
        List<StudioNav.Node> list = new ArrayList<>();
        for (ZoneType type : ZoneType.values()) {
            if (!type.isCreatableByCommand()) continue;
            list.add(StudioNav.Node.heading("heading:" + type.id(), Component.translatable("studio.zones.node." + type.id())));
            for (Zone zone : current) {
                if (zone.type() != type) continue;
                Component owner = zone.ownerTeam() == null ? Component.empty() : Component.literal(zone.ownerTeam());
                list.add(StudioNav.Node.item(zone.id(), Component.literal(zone.id()), owner, 0, false));
            }
        }
        return list;
    }

    private static List<MapThing> buildThings(List<Zone> current) {
        Minecraft minecraft = Minecraft.getInstance();
        List<MapThing> list = new ArrayList<>();
        for (Zone zone : current) {
            boolean here = minecraft.level != null && zone.inLevel(minecraft.level);
            list.add(new MapThing(zone.id(), Component.literal(zone.id()), zone.area().center().getX(),
                    zone.area().center().getY(), zone.area().center().getZ(), zone.area().shape(), zone.area().size(),
                    drawnColor(zone), here));
        }
        return list;
    }

    // WHY: зона без своего цвета рисуется цветом команды владельца, и образец показывает тот цвет,
    // WHY: которым она сейчас нарисована в мире
    static int drawnColor(Zone zone) {
        return 0xFF000000 | (zone.hasCustomColor() ? zone.color() : ZoneColors.packed(zone));
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

    Zone zone() {
        if (creating || chosenId == null) return null;
        Zone zone = ClientZoneData.byId(chosenId);
        return zone != null && zone.source() == ZoneSource.STORED ? zone : null;
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
        ensureBuilt();
        int bases = 0;
        for (Zone zone : zones) {
            if (zone.type() == ZoneType.BASE) bases++;
        }
        return Component.translatable("studio.zones.stats", bases, zones.size() - bases);
    }

    @Override
    protected List<UiButton> headerButtons() {
        addButton.setMessage(Component.translatable(creating ? "studio.points.cancel" : "studio.zones.add"));
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

    // WHY: новой зоне не нужно придумывать идентификатор: подставляется свободный, и его можно
    // WHY: тут же переписать в поле, которое уже в фокусе
    private String freeId() {
        for (int number = 1; ; number++) {
            String id = "zone_" + number;
            if (ClientZoneData.byId(id) == null) return id;
        }
    }

    @Override
    protected MapThing ghost() {
        if (!creating) return null;
        return new MapThing("new", Component.literal(rows.typedId()), newX, MapCanvas.surfaceY(newX, newZ), newZ,
                rows.newShape(), rows.newSize(), UiAccent.color(), true);
    }

    @Override
    protected void placeGhost(int x, int z) {
        newX = x;
        newZ = z;
    }

    void placeGhostAt(int x, int z) {
        placeGhost(x, z);
    }

    void create(String id, ZoneShape shape, int size, ZoneType type) {
        if (ClientZoneData.byId(id) != null) {
            MenuFeedback.show(Component.translatable("zones.error.zone_exists", id), true);
            return;
        }
        now(COMMAND + "create " + id + " " + shape.id() + " " + size + " " + type.id());
        now(COMMAND + "edit " + id + " at " + newX + " " + MapCanvas.surfaceY(newX, newZ) + " " + newZ);
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
        return creating || zone() != null ? ART : 0;
    }

    @Override
    protected void renderArt(GuiGraphics graphics, float left, float top, float width, float appear,
                             int mouseX, int mouseY) {
        Zone zone = zone();
        Component title = creating ? Component.translatable("studio.zones.group.new")
                : zone == null ? null : Component.literal(zone.id());
        if (title == null) return;
        UiRender.textTrackedFit(graphics, this.font, title, left + width / 2.0f, top, 14.0f, width, 0.95f, 0.0f,
                UiTheme.alpha(UiAccent.text(), appear), false);
        UiRender.textTrackedFit(graphics, this.font, artDetail(zone), left + width / 2.0f, top + 16.0f, 10.0f, width,
                LINE_SCALE, 0.0f, UiTheme.alpha(UiAccent.textDim(), appear), false);
    }

    private Component artDetail(Zone zone) {
        if (creating || zone == null) return Component.translatable("studio.points.place", newX, newZ);
        Minecraft minecraft = Minecraft.getInstance();
        Component where = minecraft.level != null && zone.inLevel(minecraft.level)
                ? Component.translatable("studio.points.at", zone.area().center().getX(),
                zone.area().center().getY(), zone.area().center().getZ())
                : Component.translatable("studio.map.elsewhere");
        return Component.translatable("studio.points.detail",
                Component.translatable("zones.type." + zone.type().id()), where);
    }

    @Override
    protected List<StudioMenu.Action> thingActions(MapThing thing) {
        return actionsFor(ClientZoneData.byId(thing.key()));
    }

    @Override
    protected List<StudioMenu.Action> nodeActions(StudioNav.Node node) {
        return actionsFor(ClientZoneData.byId(node.key()));
    }

    private List<StudioMenu.Action> actionsFor(Zone zone) {
        if (zone == null) return List.of(StudioMenu.Action.of("studio.zones.add", this::toggleCreate));
        String id = zone.id();
        return List.of(StudioMenu.Action.of("studio.menu.tp", () -> now(COMMAND + "tp " + id)),
                StudioMenu.Action.of("studio.menu.move_here", () -> now(COMMAND + "edit " + id + " here")),
                StudioMenu.Action.of("studio.menu.color", () -> rows.openColor(zone)),
                StudioMenu.Action.careful("studio.menu.delete_zone", () -> remove(id)));
    }

    @Override
    protected List<StudioMenu.Action> groundActions(int x, int z) {
        return List.of(StudioMenu.Action.of("studio.menu.new_zone_here", () -> startCreate(x, z)),
                StudioMenu.Action.of("studio.map.me", this::centerOnPlayer));
    }

    void remove(String id) {
        now(COMMAND + "delete " + id);
        chosenId = neighbour(id);
        layout();
    }

    private String neighbour(String removed) {
        ensureBuilt();
        for (int index = 0; index < zones.size(); index++) {
            if (!zones.get(index).id().equals(removed)) continue;
            if (index > 0) return zones.get(index - 1).id();
            return zones.size() > 1 ? zones.get(1).id() : null;
        }
        return null;
    }

    @Override
    protected void deleteSelected() {
        Zone zone = zone();
        if (zone == null) return;
        if (confirm("delete:" + zone.id())) {
            remove(zone.id());
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
        if (pendingId != null && ClientZoneData.byId(pendingId) != null) {
            chosenId = pendingId;
            pendingId = null;
        }
        if (chosenId == null && !zones.isEmpty()) chosenId = zones.get(0).id();
    }

    void now(String command) {
        send(command);
    }

    void later(String key, String command) {
        commitLater(key, command);
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
