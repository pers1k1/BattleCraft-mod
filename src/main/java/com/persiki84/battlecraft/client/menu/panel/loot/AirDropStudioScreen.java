package com.persiki84.battlecraft.client.menu.panel.loot;

import com.persiki84.airdrop.loot.LootEntry;
import com.persiki84.airdrop.loot.LootTable;
import com.persiki84.airdrop.loot.LootTables;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.pick.ItemShelf;
import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.client.menu.studio.StudioScreen;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.menu.studio.StudioTile;
import com.persiki84.shared.client.ui.UiButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

// WHY: аирдроп, тайники и таблицы лута это один раздел: тайник смотрит в таблицу, аирдроп смотрит
// WHY: в таблицу, и править их по отдельным экранам значило держать связи между ними в голове
public final class AirDropStudioScreen extends StudioScreen {
    static final String DROP = "drop";
    static final String CACHES = "caches";
    private static final String TABLES_HEADING = "tables";
    private static final String TABLE_PREFIX = "t:";
    private static final int SNAPSHOT_TICKS = 20;
    private static final int WANTED_TICKS = 60;

    private final AirDropSettings settings = new AirDropSettings(this);
    private final LootInspector lootRows = new LootInspector(this);
    private final CacheInspector cacheRows = new CacheInspector(this);
    private final LootTrial trial = new LootTrial();
    private final List<UiButton> tableHeader;
    private final List<UiButton> cacheHeader;
    private final List<UiButton> dropHeader;
    private final UiButton modeButton;
    private final UiButton rollButton;
    private String nodeKey;
    private int selectedEntry = -1;
    private int selectedCache = -1;
    private boolean adding;
    private String wantedTable;
    private int wantedSince;
    private int flashAt = -1;
    private Stamp builtFor;
    private List<StudioNav.Node> nodes = List.of();
    private List<? extends StudioTile> tiles = List.of();

    private record Stamp(int airdrop, List<LootSnapshot.Table> loot, String node) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Stamp stamp && stamp.airdrop == airdrop && stamp.loot == loot
                    && java.util.Objects.equals(stamp.node, node);
        }

        @Override
        public int hashCode() {
            return airdrop;
        }
    }

    public AirDropStudioScreen(String table, Screen parent) {
        super(Component.translatable("studio.airdrop.title"), parent);
        nodeKey = table == null ? DROP : TABLE_PREFIX + table;
        rollButton = button("studio.loot.roll", this::roll);
        modeButton = button("studio.loot.add", this::switchMode);
        tableHeader = List.of(rollButton, modeButton, button("studio.loot.new_table", this::newTable));
        cacheHeader = List.of(button("studio.cache.aim", this::addLookedAt),
                button("studio.cache.fill_all", () -> now("airdrop cache fill_all")));
        dropHeader = List.of(button("studio.drop.now", () -> now("airdrop now")),
                button("studio.drop.here", () -> now("airdrop spawn")),
                button("studio.drop.reload", () -> now("airdrop reload")));
        MenuData.invalidate(ModuleMenuStates.LOOT);
        MenuData.request(ModuleMenuStates.LOOT);
        MenuData.request(ModuleMenuStates.AIRDROP);
    }

    private static UiButton button(String key, Runnable action) {
        return new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.translatable(key), pressed -> action.run())
                .hint(key + ".hint");
    }

    @Override
    protected String menuId() {
        return tableName() != null ? ModuleMenuStates.LOOT : ModuleMenuStates.AIRDROP;
    }

    static CompoundTag state() {
        return MenuData.state(ModuleMenuStates.AIRDROP);
    }

    @Override
    protected Object stamp() {
        return new Stamp(state().hashCode(), LootSnapshot.all(), selectedNode());
    }

    private void ensureBuilt() {
        Stamp stamp = (Stamp) stamp();
        if (stamp.equals(builtFor)) return;
        builtFor = stamp;
        nodes = buildNodes();
        tiles = buildTiles();
    }

    private List<StudioNav.Node> buildNodes() {
        List<StudioNav.Node> built = new ArrayList<>();
        built.add(StudioNav.Node.item(DROP, Component.translatable("studio.airdrop.node.drop"), Component.empty(), 0, false));
        built.add(StudioNav.Node.item(CACHES, Component.translatable("studio.airdrop.node.caches"),
                Component.literal(String.valueOf(CacheTile.of(state()).size())), 0, false));
        built.add(StudioNav.Node.heading(TABLES_HEADING, Component.translatable("studio.airdrop.node.tables")));
        for (LootSnapshot.Table table : LootSnapshot.all()) {
            String name = table.table().name();
            built.add(StudioNav.Node.item(TABLE_PREFIX + name, Component.literal(name),
                    Component.literal(String.valueOf(table.table().entries().size())), 0, false));
        }
        return built;
    }

    private List<? extends StudioTile> buildTiles() {
        if (CACHES.equals(selectedNode())) return CacheTile.of(state());
        LootSnapshot.Table table = currentTable();
        return table == null ? List.of() : LootTile.of(table.table());
    }

    @Override
    protected List<StudioNav.Node> nodes() {
        ensureBuilt();
        return nodes;
    }

    @Override
    protected String selectedNode() {
        if (DROP.equals(nodeKey) || CACHES.equals(nodeKey)) return nodeKey;
        String name = nodeKey == null ? null : nodeKey.substring(TABLE_PREFIX.length());
        if (name != null && LootSnapshot.find(name) != null) return nodeKey;
        if (name != null && name.equals(wantedTable) && ticks - wantedSince < WANTED_TICKS) return nodeKey;
        if (!LootSnapshot.arrived() && name != null) return nodeKey;
        nodeKey = DROP;
        return nodeKey;
    }

    String tableName() {
        String node = selectedNode();
        return node.startsWith(TABLE_PREFIX) ? node.substring(TABLE_PREFIX.length()) : null;
    }

    LootSnapshot.Table currentTable() {
        String name = tableName();
        return name == null ? null : LootSnapshot.find(name);
    }

    LootTable table() {
        LootSnapshot.Table current = currentTable();
        String name = tableName();
        return current == null ? LootTable.empty(name == null ? "" : name) : current.table();
    }

    LootEntry entry() {
        LootTable table = table();
        return tableName() != null && table.contains(selectedEntry) ? table.entries().get(selectedEntry) : null;
    }

    int entryIndex() {
        return selectedEntry;
    }

    CacheTile cache() {
        if (!CACHES.equals(selectedNode()) || selectedCache < 0) return null;
        for (StudioTile tile : tiles()) {
            if (tile instanceof CacheTile cache && cache.id() == selectedCache) return cache;
        }
        return null;
    }

    boolean adding() {
        return adding;
    }

    @Override
    protected void selectNode(StudioNav.Node picked) {
        nodeKey = picked.key();
        selectedEntry = -1;
        selectedCache = -1;
        adding = false;
        trial.close();
        grid.reset();
        lootRows.rest();
    }

    void openTable(String name) {
        flushCommits();
        nodeKey = TABLE_PREFIX + name;
        selectedEntry = -1;
        selectedCache = -1;
        adding = false;
        grid.reset();
        nav.reveal(nodeKey);
        layout();
    }

    void wantTable(String name) {
        wantedTable = name;
        wantedSince = ticks;
        openTable(name);
    }

    @Override
    protected Component stats() {
        String node = selectedNode();
        if (DROP.equals(node)) return settings.stats();
        if (CACHES.equals(node)) return cacheRows.stats();
        LootTable table = table();
        if (!LootSnapshot.arrived() || currentTable() == null) return Component.translatable("battlecraft.menu.waiting");
        return Component.translatable("airdrop.editor.stats", table.name(), table.entries().size(),
                LootChance.shown(LootOdds.positions(table)), LootChance.shown(LootOdds.items(table)));
    }

    @Override
    protected List<UiButton> headerButtons() {
        String node = selectedNode();
        if (DROP.equals(node)) return dropHeader;
        if (CACHES.equals(node)) return cacheHeader;
        rollButton.active = !table().entries().isEmpty();
        modeButton.setMessage(Component.translatable(adding ? "studio.mode.done" : "studio.loot.add"));
        return tableHeader;
    }

    private void newTable() {
        flushCommits();
        selectedEntry = -1;
        adding = false;
        lootRows.focusName();
    }

    List<CacheTile> cacheTiles() {
        if (!CACHES.equals(selectedNode())) return CacheTile.of(state());
        List<CacheTile> caches = new ArrayList<>();
        for (StudioTile tile : tiles()) {
            if (tile instanceof CacheTile cache) caches.add(cache);
        }
        return caches;
    }

    private void roll() {
        if (!table().entries().isEmpty()) trial.roll(table());
    }

    private void switchMode() {
        flushCommits();
        adding = !adding;
        lootRows.rest();
        layout();
    }

    // WHY: контейнер выбирается прицелом, а не координатами: сервер проверяет место заново,
    // WHY: клиент только называет блок, на который смотрит игрок
    private void addLookedAt() {
        HitResult hit = Minecraft.getInstance().hitResult;
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            MenuFeedback.show(Component.translatable("airdrop.menu.cache_aim"), true);
            return;
        }
        BlockPos pos = block.getBlockPos();
        cacheRows.expectNew(CacheTile.of(state()).size());
        now("airdrop cache add " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    @Override
    protected Object inspectorSubject() {
        String node = selectedNode();
        if (adding) return "shelf";
        if (entry() != null) return "entry:" + node + "/" + selectedEntry;
        if (cache() != null) return "cache:" + selectedCache;
        return "node:" + node;
    }

    @Override
    protected void buildInspector(StudioStack stack, int x, int width) {
        String node = selectedNode();
        if (DROP.equals(node)) {
            settings.inspector(stack, x, width);
        } else if (CACHES.equals(node)) {
            cacheRows.build(stack, x, width);
        } else {
            lootRows.build(stack, x, width);
        }
    }

    @Override
    protected boolean gridCanvas() {
        return !DROP.equals(selectedNode());
    }

    @Override
    protected void buildCanvas(StudioStack stack, int x, int width) {
        settings.place(stack, x, width);
    }

    @Override
    protected int artHeight() {
        String node = selectedNode();
        if (adding) return 0;
        if (DROP.equals(node)) return settings.artHeight();
        return CACHES.equals(node) ? cacheRows.artHeight() : lootRows.artHeight();
    }

    @Override
    protected void renderArt(GuiGraphics graphics, float left, float top, float width, float appear,
                             int mouseX, int mouseY) {
        String node = selectedNode();
        if (DROP.equals(node)) {
            settings.renderArt(graphics, left, top, width, appear);
        } else if (CACHES.equals(node)) {
            cacheRows.renderArt(graphics, left, top, width, appear);
        } else {
            lootRows.renderArt(graphics, left, top, width, appear);
        }
    }

    @Override
    protected boolean pressArt(double mouseX, double mouseY) {
        return !adding && (lootRows.pressArt(mouseX, mouseY) || cacheRows.pressArt(mouseX, mouseY));
    }

    @Override
    protected boolean dragArt(double dragX, double dragY) {
        return lootRows.dragArt(dragX, dragY) || cacheRows.dragArt(dragX, dragY);
    }

    @Override
    protected void releaseArt() {
        lootRows.releaseArt();
        cacheRows.releaseArt();
    }

    @Override
    protected boolean scrollArt(double mouseX, double mouseY, double amount) {
        return !adding && (lootRows.scrollArt(mouseX, mouseY, amount) || cacheRows.scrollArt(mouseX, mouseY, amount));
    }

    @Override
    public void tick() {
        if (ticks % SNAPSHOT_TICKS == 0) {
            MenuData.request(ModuleMenuStates.LOOT);
            MenuData.request(ModuleMenuStates.AIRDROP);
        }
        super.tick();
        lootRows.tick();
        cacheRows.tick();
    }

    @Override
    protected void refreshed() {
        LootTable table = table();
        if (selectedEntry >= table.entries().size()) selectedEntry = table.entries().size() - 1;
        if (flashAt >= 0 && flashAt < table.entries().size()) {
            grid.reveal(flashAt, table.entries().size());
            grid.flash(flashAt);
            flashAt = -1;
        }
        int arrived = cacheRows.arrived(CacheTile.of(state()));
        if (arrived >= 0) selectedCache = arrived;
    }

    @Override
    protected List<? extends StudioTile> tiles() {
        ensureBuilt();
        return tiles;
    }

    @Override
    protected int selectedTile() {
        if (tableName() != null) return selectedEntry;
        List<? extends StudioTile> shown = tiles();
        for (int index = 0; index < shown.size(); index++) {
            if (((CacheTile) shown.get(index)).id() == selectedCache) return index;
        }
        return -1;
    }

    @Override
    protected void selectTile(int index) {
        adding = false;
        lootRows.rest();
        if (tableName() != null) {
            selectedEntry = index;
            return;
        }
        selectedCache = index < 0 ? -1 : ((CacheTile) tiles.get(index)).id();
    }

    @Override
    protected boolean tilesMovable() {
        return tableName() != null;
    }

    @Override
    protected void moveTile(int from, int to) {
        send(base() + from + " move " + to);
        selectedEntry = to;
        layout();
    }

    String base() {
        return "airdrop loot " + tableName() + " ";
    }

    @Override
    protected boolean shelfOpen() {
        return adding && tableName() != null;
    }

    // WHY: предмет добавляется в конец таблицы и сразу переносится туда, где его отпустили:
    // WHY: команды доходят до сервера по порядку, поэтому номер новой записи равен прежнему размеру
    @Override
    protected void addPick(ItemShelf.Pick pick, int slot) {
        if (tableName() == null) return;
        int total = table().entries().size();
        send(addCommand(pick));
        int wanted = slot >= 0 && slot < total ? slot : total;
        if (wanted < total) send(base() + total + " move " + wanted);
        selectedEntry = wanted;
        flashAt = wanted;
    }

    private String addCommand(ItemShelf.Pick pick) {
        String chance = LootChance.argument(lootRows.newPercent());
        if (pick.fromInventory()) {
            int count = Math.max(1, Math.min(LootEntry.MAX_COUNT, pick.stack().getCount()));
            return base() + "slot " + pick.slot() + " " + count + " " + count + " " + chance;
        }
        return base() + "add " + ItemShelf.spec(pick.stack()) + " 1 1 " + chance;
    }

    @Override
    protected Component emptyCanvas() {
        if (CACHES.equals(selectedNode())) return Component.translatable("studio.cache.empty");
        if (!LootSnapshot.arrived() || currentTable() == null) return Component.translatable("battlecraft.menu.waiting");
        return Component.translatable(adding ? "studio.loot.empty_adding" : "studio.loot.empty");
    }

    @Override
    protected void deleteSelected() {
        if (entry() != null) removeEntry();
    }

    void removeEntry() {
        if (entry() == null) return;
        flushCommits();
        send(base() + selectedEntry + " remove");
        int left = table().entries().size() - 1;
        selectedEntry = left <= 0 ? -1 : Math.max(0, Math.min(selectedEntry, left - 1));
        layout();
    }

    void copyEntry() {
        if (entry() == null) return;
        flushCommits();
        send(base() + selectedEntry + " duplicate");
    }

    void createTable(String name) {
        if (!LootTables.validName(name)) {
            MenuFeedback.show(Component.translatable("airdrop.loot.bad_name", LootTables.NAME_LIMIT), true);
            return;
        }
        if (LootSnapshot.find(name) != null) {
            MenuFeedback.show(Component.translatable("airdrop.loot.table_exists", name), true);
            return;
        }
        now("airdrop loot create " + name);
        wantTable(name);
    }

    // WHY: после удаления выбор встаёт на соседнюю таблицу, а не прыгает к первой
    void deleteTable() {
        String removed = tableName();
        if (removed == null) return;
        now("airdrop loot delete " + removed);
        List<String> names = LootSnapshot.names();
        int index = names.indexOf(removed);
        String next = index > 0 ? names.get(index - 1) : names.size() > 1 ? names.get(1) : null;
        if (next == null) {
            nodeKey = DROP;
            layout();
            return;
        }
        openTable(next);
    }

    @Override
    protected boolean modalOpen() {
        return trial.open();
    }

    @Override
    protected boolean modalClick(double mouseX, double mouseY, int button) {
        if (!trial.contains(mouseX, mouseY)) return false;
        if (button == 0) trial.roll(table());
        return true;
    }

    @Override
    protected void closeModal() {
        trial.close();
    }

    @Override
    protected void renderModal(GuiGraphics graphics, float centerX, float centerY, int mouseX, int mouseY) {
        trial.render(graphics, centerX, centerY, mouseX, mouseY);
    }

    @Override
    protected ItemStack modalHovered(double mouseX, double mouseY) {
        return trial.hovered(mouseX, mouseY);
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

    void now(String command) {
        if (!command.isEmpty()) send(command);
    }

    void relayout() {
        layout();
    }

    void selectCache(int id) {
        selectedCache = id;
    }
}
