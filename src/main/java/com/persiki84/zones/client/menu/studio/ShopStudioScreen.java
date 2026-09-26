package com.persiki84.zones.client.menu.studio;

import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.gunsmith.GunsmithScreen;
import com.persiki84.shared.client.menu.pick.ItemShelf;
import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.client.menu.studio.StudioScreen;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.menu.studio.StudioTile;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.zones.client.ClientShopData;
import com.persiki84.zones.client.menu.ShopGunBench;
import com.persiki84.zones.client.menu.ShopScreen;
import com.persiki84.zones.network.PacketHandler;
import com.persiki84.zones.network.ShopRefreshPacket;
import com.persiki84.zones.shop.ShopEntry;
import com.persiki84.zones.shop.ShopSection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class ShopStudioScreen extends StudioScreen {
    public static final String MENU_ID = "shop";

    static final String COMMAND = "battlecraft shop ";
    private static final int WANTED_TICKS = 60;
    private static final int PENDING_TICKS = 80;

    private final ShopInspector inspectorRows = new ShopInspector(this);
    private final List<UiButton> header;
    private final UiButton modeButton;
    private final UiButton viewerButton;
    private final List<PendingAdd> pendingAdds = new ArrayList<>();
    private String nodeKey;
    private String parsedKey;
    private ShopNode parsedNode;
    private String entryId;
    private String wantedNode;
    private int wantedSince;
    private boolean adding;
    private String viewedTeam;
    private int cachedVersion = -1;
    private String cachedNode;
    private String cachedTeam;
    private List<StudioNav.Node> nodes = List.of();
    private List<ShopTile> tiles = List.of();

    private record PendingAdd(String nodeKey, Set<String> known, int slot, int since) {}

    public ShopStudioScreen() {
        this(null);
    }

    public ShopStudioScreen(Screen parent) {
        super(Component.translatable("studio.shop.title"), parent);
        modeButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.empty(), pressed -> switchMode())
                .hint("studio.shop.mode.hint");
        viewerButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.empty(), pressed -> nextViewer())
                .hint("studio.shop.viewer.hint");
        UiButton sectionButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW,
                Component.translatable("studio.shop.new_section"), pressed -> createSection())
                .hint("studio.shop.new_section.hint");
        UiButton previewButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW,
                Component.translatable("studio.shop.preview"), pressed -> openPreview())
                .hint("studio.shop.preview.hint");
        header = List.of(modeButton, viewerButton, previewButton, sectionButton);
        PacketHandler.INSTANCE.sendToServer(new ShopRefreshPacket());
    }

    @Override
    protected String menuId() {
        return MENU_ID;
    }

    private void ensureCache() {
        String node = selectedNode();
        int version = ClientShopData.version();
        if (version == cachedVersion && java.util.Objects.equals(node, cachedNode)
                && java.util.Objects.equals(viewedTeam, cachedTeam)) return;

        cachedVersion = version;
        cachedNode = node;
        cachedTeam = viewedTeam;
        nodes = ShopNode.tree();
        tiles = buildTiles();
    }

    private List<ShopTile> buildTiles() {
        ShopSection owner = ownerOf(node());
        if (owner == null) return List.of();
        List<ShopTile> built = new ArrayList<>();
        for (ShopEntry entry : owner.entries().values()) {
            built.add(ShopTile.of(entry, viewedTeam));
        }
        return built;
    }

    private static ShopSection ownerOf(ShopNode node) {
        return node == null ? null : node.owner();
    }

    @Override
    protected List<StudioNav.Node> nodes() {
        ensureCache();
        return nodes;
    }

    @Override
    protected String selectedNode() {
        ShopNode node = nodeKey != null && nodeKey.equals(parsedKey) ? parsedNode : ShopNode.parse(nodeKey);
        if (node != null && node.owner() != null) return nodeKey;
        if (nodeKey != null && nodeKey.equals(wantedNode) && ticks - wantedSince < WANTED_TICKS) return nodeKey;

        List<ShopSection> sections = ClientShopData.sections();
        nodeKey = sections.isEmpty() ? null : ShopNode.keyOf(sections.get(0).id(), null);
        entryId = null;
        return nodeKey;
    }

    ShopNode node() {
        String key = selectedNode();
        if (key == null) return null;
        if (!key.equals(parsedKey)) {
            parsedKey = key;
            parsedNode = ShopNode.parse(key);
        }
        return parsedNode;
    }

    ShopEntry entry() {
        ShopSection owner = ownerOf(node());
        return owner == null || entryId == null ? null : owner.entry(entryId);
    }

    @Override
    protected void selectNode(StudioNav.Node picked) {
        nodeKey = picked.key();
        entryId = null;
        grid.reset();
        inspectorRows.rest();
    }

    void want(String key) {
        nodeKey = key;
        entryId = null;
        wantedNode = key;
        wantedSince = ticks;
        grid.reset();
        nav.reveal(key);
    }

    @Override
    protected Component stats() {
        ShopSection owner = ownerOf(node());
        if (owner == null) return Component.translatable(ClientShopData.sections().isEmpty()
                ? "studio.shop.stats.empty" : "battlecraft.menu.waiting");
        ensureCache();
        int hidden = 0;
        for (ShopTile tile : tiles) {
            if (tile.faded()) hidden++;
        }
        if (viewedTeam == null) return Component.translatable("studio.shop.stats", owner.title(), tiles.size());
        return Component.translatable("studio.shop.stats.team", owner.title(), tiles.size() - hidden, viewedTeam);
    }

    @Override
    protected List<UiButton> headerButtons() {
        modeButton.setMessage(Component.translatable(adding ? "studio.mode.done" : "studio.shop.mode.add"));
        modeButton.active = node() != null;
        viewerButton.setMessage(viewedTeam == null ? Component.translatable("studio.shop.viewer.all")
                : Component.translatable("studio.shop.viewer.team", viewedTeam));
        return header;
    }

    private void switchMode() {
        flushCommits();
        adding = !adding;
        inspectorRows.rest();
        layout();
    }

    private void nextViewer() {
        List<String> teams = ShopAccessRows.teams();
        int index = viewedTeam == null ? 0 : teams.indexOf(viewedTeam) + 1;
        viewedTeam = index >= teams.size() ? null : teams.get(index);
        if (teams.isEmpty()) MenuFeedback.show(Component.translatable("zones.shopadmin.access.no_teams"), true);
        layout();
    }

    private void openPreview() {
        flushCommits();
        ShopScreen.openPreview(this, viewedTeam);
    }

    @Override
    protected Object inspectorSubject() {
        if (adding) return "shelf";
        if (entry() != null) return "entry:" + nodeKey + "/" + entryId;
        return "node:" + nodeKey;
    }

    @Override
    protected void buildInspector(StudioStack stack, int x, int width) {
        if (adding) {
            inspectorRows.shelf(stack, x, width);
        } else if (entry() != null) {
            inspectorRows.entry(stack, x, width);
        } else if (node() != null) {
            inspectorRows.node(stack, x, width);
        }
    }

    @Override
    protected int artHeight() {
        return adding ? 0 : inspectorRows.artHeight();
    }

    @Override
    protected void renderArt(GuiGraphics graphics, float left, float top, float width, float appear,
                             int mouseX, int mouseY) {
        inspectorRows.renderArt(graphics, left, top, width, appear);
    }

    @Override
    protected boolean pressArt(double mouseX, double mouseY) {
        return !adding && inspectorRows.pressArt(mouseX, mouseY);
    }

    @Override
    protected boolean dragArt(double dragX, double dragY) {
        return inspectorRows.dragArt(dragX, dragY);
    }

    @Override
    protected void releaseArt() {
        inspectorRows.releaseArt();
    }

    @Override
    protected boolean scrollArt(double mouseX, double mouseY, double amount) {
        return !adding && inspectorRows.scrollArt(mouseX, mouseY, amount);
    }

    @Override
    protected Object stamp() {
        return ClientShopData.version();
    }

    @Override
    public void tick() {
        super.tick();
        inspectorRows.tick();
    }

    @Override
    protected void refreshed() {
        settlePendingAdds();
        inspectorRows.fill(false);
    }

    @Override
    protected List<? extends StudioTile> tiles() {
        ensureCache();
        return tiles;
    }

    @Override
    protected int selectedTile() {
        if (entryId == null) return -1;
        List<? extends StudioTile> shown = tiles();
        for (int index = 0; index < shown.size(); index++) {
            if (((ShopTile) shown.get(index)).entry().id().equals(entryId)) return index;
        }
        return -1;
    }

    @Override
    protected void selectTile(int index) {
        entryId = index < 0 ? null : tiles.get(index).entry().id();
        adding = false;
        inspectorRows.rest();
    }

    @Override
    protected boolean tilesMovable() {
        return true;
    }

    // WHY: витрина показывает ровно один раздел или отдел, поэтому номер плитки равен месту товара
    // WHY: в каталоге, и перенос уходит одной командой на место, а не десятком шагов
    @Override
    protected void moveTile(int from, int to) {
        ShopNode node = node();
        if (node == null) return;
        send(COMMAND + "item order " + node.section() + " " + tiles.get(from).entry().id() + " to " + (to + 1));
    }

    @Override
    protected boolean acceptsTile(StudioNav.Node target, int tile) {
        return !target.key().equals(nodeKey) && ShopNode.parse(target.key()) != null;
    }

    @Override
    protected void dropTile(StudioNav.Node target, int tile) {
        ShopNode node = node();
        ShopNode to = target == null ? null : ShopNode.parse(target.key());
        if (node == null || to == null || tile < 0 || tile >= tiles.size()) return;

        send(COMMAND + "item move " + node.section() + " " + tiles.get(tile).entry().id() + " " + to.section()
                + to.childSuffix());
        if (tiles.get(tile).entry().id().equals(entryId)) entryId = null;
        layout();
    }

    @Override
    protected StudioNav.Drop navRule(StudioNav.Node carried, StudioNav.Node over) {
        ShopNode from = ShopNode.parse(carried.key());
        ShopNode to = ShopNode.parse(over.key());
        if (from == null || to == null) return StudioNav.Drop.NONE;
        if (from.top() && to.top()) return StudioNav.Drop.BEFORE;
        if (!from.top() && !to.top() && from.section().equals(to.section())) return StudioNav.Drop.BEFORE;
        if (!from.top() && to.top() && !from.section().equals(to.section())) return StudioNav.Drop.INTO;
        return StudioNav.Drop.NONE;
    }

    @Override
    protected void navMoved(StudioNav.Node carried, StudioNav.Node target, StudioNav.Drop drop) {
        ShopNode from = ShopNode.parse(carried.key());
        ShopNode to = ShopNode.parse(target.key());
        if (from == null || to == null) return;
        if (drop == StudioNav.Drop.INTO) {
            send(COMMAND + "subsection move " + from.section() + " " + from.child() + " " + to.section());
            want(ShopNode.keyOf(to.section(), from.child()));
            return;
        }
        boolean after = drop == StudioNav.Drop.AFTER;
        if (from.top()) {
            send(COMMAND + "section order " + from.section() + " to " + place(sectionIds(), from.section(), to.section(), after));
        } else {
            ShopSection top = from.topSection();
            if (top == null) return;
            send(COMMAND + "subsection order " + from.section() + " " + from.child() + " to "
                    + place(top.childIds(), from.child(), to.child(), after));
        }
    }

    private static List<String> sectionIds() {
        List<String> ids = new ArrayList<>();
        for (ShopSection section : ClientShopData.sections()) {
            ids.add(section.id());
        }
        return ids;
    }

    private static int place(List<String> order, String moved, String anchor, boolean after) {
        List<String> rest = new ArrayList<>(order);
        rest.remove(moved);
        return rest.indexOf(anchor) + (after ? 1 : 0) + 1;
    }

    @Override
    protected boolean shelfOpen() {
        return adding;
    }

    // WHY: номер нового товара клиент не знает, его выбирает сервер; поэтому запоминается состав
    // WHY: раздела до добавления, и новый товар узнаётся в первом снимке, где его не было
    @Override
    protected void addPick(ItemShelf.Pick pick, int slot) {
        ShopNode node = node();
        ShopSection top = node == null ? null : node.topSection();
        if (top == null) {
            MenuFeedback.show(Component.translatable("zones.shopadmin.pick_section"), true);
            return;
        }
        Set<String> known = new HashSet<>(top.deepEntryIds());
        send(addCommand(node, pick));
        pendingAdds.add(new PendingAdd(node.key(), known, slot, ticks));
    }

    private String addCommand(ShopNode node, ItemShelf.Pick pick) {
        String tail = node.childSuffix();
        if (pick.fromInventory()) {
            return COMMAND + "item slot " + node.section() + " " + pick.slot() + " " + inspectorRows.newPrice() + tail;
        }
        String spec = ItemShelf.spec(pick.stack()).replace("\\", "\\\\").replace("\"", "\\\"");
        return COMMAND + "item id " + node.section() + " \"" + spec + "\" " + inspectorRows.newCount() + " "
                + inspectorRows.newPrice() + tail;
    }

    private void settlePendingAdds() {
        Set<String> claimed = new HashSet<>();
        Iterator<PendingAdd> cursor = pendingAdds.iterator();
        while (cursor.hasNext()) {
            PendingAdd pending = cursor.next();
            String arrived = arrivedEntry(pending, claimed);
            if (arrived != null) {
                claimed.add(arrived);
                place(pending, arrived);
                cursor.remove();
            } else if (ticks - pending.since() > PENDING_TICKS) {
                cursor.remove();
            }
        }
    }

    private static String arrivedEntry(PendingAdd pending, Set<String> claimed) {
        ShopSection owner = ownerOf(ShopNode.parse(pending.nodeKey()));
        if (owner == null) return null;
        for (String id : owner.entryIds()) {
            if (!pending.known().contains(id) && !claimed.contains(id)) return id;
        }
        return null;
    }

    private void place(PendingAdd pending, String arrived) {
        ShopNode node = ShopNode.parse(pending.nodeKey());
        ShopSection owner = ownerOf(node);
        int index = owner.entryIds().indexOf(arrived);
        int slot = pending.slot() >= 0 && pending.slot() < index ? pending.slot() : index;
        if (slot != index) send(COMMAND + "item order " + node.section() + " " + arrived + " to " + (slot + 1));
        if (!pending.nodeKey().equals(nodeKey)) return;
        entryId = arrived;
        cachedVersion = -1;
        grid.reveal(slot, owner.entries().size());
        grid.flash(slot);
    }

    @Override
    protected Component emptyCanvas() {
        if (ClientShopData.sections().isEmpty()) return Component.translatable("studio.shop.empty_catalog");
        return Component.translatable(adding ? "studio.shop.empty_adding" : "studio.shop.empty");
    }

    @Override
    protected Component shelfHint() {
        return Component.translatable("studio.shop.shelf.hint");
    }

    @Override
    protected void deleteSelected() {
        if (entry() != null) removeEntry();
    }

    void removeEntry() {
        ShopNode node = node();
        ShopEntry entry = entry();
        if (node == null || entry == null) return;

        flushCommits();
        int index = selectedTile();
        send(COMMAND + "item remove " + node.section() + " " + entry.id());
        entryId = neighbour(index);
        layout();
    }

    // WHY: удаляют подряд, поэтому выбор встаёт на соседа, а не пропадает: иначе каждый следующий
    // WHY: товар приходится искать заново
    private String neighbour(int index) {
        if (tiles.size() <= 1) return null;
        int next = index > 0 ? index - 1 : 1;
        return tiles.get(Math.min(next, tiles.size() - 1)).entry().id();
    }

    void copyEntry() {
        ShopNode node = node();
        ShopEntry entry = entry();
        if (node == null || entry == null) return;

        flushCommits();
        ShopSection top = node.topSection();
        Set<String> known = top == null ? Set.of() : new HashSet<>(top.deepEntryIds());
        send(COMMAND + "item copy " + node.section() + " " + entry.id());
        pendingAdds.add(new PendingAdd(node.key(), known, selectedTile() + 1, ticks));
    }

    void openGuns() {
        ShopNode node = node();
        ShopEntry entry = entry();
        if (node == null || entry == null) return;
        ShopGunBench bench = new ShopGunBench(node.section());
        GunsmithScreen.open(bench, bench.positionOf(entry.id()), this);
    }

    void createSection() {
        flushCommits();
        String id = freeId("section_", sectionIds());
        String title = Component.translatable("studio.shop.new_section.title").getString();
        send(COMMAND + "section add " + id + " " + title);
        adding = false;
        want(ShopNode.keyOf(id, null));
        inspectorRows.focusTitle();
        layout();
    }

    void createChild() {
        ShopNode node = node();
        ShopSection top = node == null ? null : node.topSection();
        if (top == null) return;

        flushCommits();
        String id = freeId("part_", top.childIds());
        String title = Component.translatable("studio.shop.new_child.title").getString();
        send(COMMAND + "subsection add " + top.id() + " " + id + " " + title);
        want(ShopNode.keyOf(top.id(), id));
        inspectorRows.focusTitle();
        layout();
    }

    private static String freeId(String prefix, List<String> taken) {
        int number = 1;
        while (taken.contains(prefix + number)) {
            number++;
        }
        return prefix + number;
    }

    void removeNode() {
        ShopNode node = node();
        if (node == null) return;

        flushCommits();
        String next = neighbourNode(node.key());
        send(node.top() ? COMMAND + "section remove " + node.section()
                : COMMAND + "subsection remove " + node.section() + " " + node.child());
        nodeKey = next;
        entryId = null;
        grid.reset();
        layout();
    }

    private String neighbourNode(String removed) {
        List<StudioNav.Node> shown = nodes();
        for (int index = 0; index < shown.size(); index++) {
            if (!shown.get(index).key().equals(removed)) continue;
            if (index > 0) return shown.get(index - 1).key();
            return shown.size() > 1 ? shown.get(1).key() : null;
        }
        return null;
    }

    boolean confirmed(String key) {
        return confirm(key);
    }

    boolean isArmed(String key) {
        return armed(key);
    }

    void later(String key, String command) {
        if (!command.isEmpty()) commitLater(key, command);
    }

    Object subject() {
        return inspectorSubject();
    }

    boolean pending(String key) {
        return committing(key);
    }

    void now(String command) {
        if (!command.isEmpty()) send(command);
    }
}
