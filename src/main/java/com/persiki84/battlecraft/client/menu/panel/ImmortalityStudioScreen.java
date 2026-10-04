package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.client.menu.studio.StudioScreen;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.menu.studio.StudioTile;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

// WHY: игроки лежат головами: неуязвимый горит и показывает остаток секунд, остальные погашены.
// WHY: Двойной щелчок выдаёт или снимает, рамкой и Ctrl выбирается сразу несколько
public final class ImmortalityStudioScreen extends StudioScreen {
    private static final String COMMAND = "immortality ";
    private static final String PLAYERS = "players";
    private static final String SETTINGS = "settings";
    private static final int ROW = 22;
    private static final int MAX_SECONDS = 3600;
    private static final int ART = 40;
    private static final float LINE_SCALE = 0.72f;

    private final HeadingRow settingsHeading = new HeadingRow(0, 0, 10, ROW,
            Component.translatable("immortality.menu.tab.main"));
    private final ToggleRow enabledRow;
    private final NumberRow durationRow;
    private final ActionRow clearRow;
    private final ToggleRow playerRow;
    private String nodeKey = PLAYERS;
    private String chosen;
    private CompoundTag builtFor;
    private List<PlayerTile> tiles = List.of();
    private List<StudioNav.Node> nodes = List.of();

    record PlayerTile(String player, ItemStack icon, boolean immortal, int remaining) implements StudioTile {
        @Override
        public Component name() {
            return Component.literal(player);
        }

        @Override
        public Component corner() {
            return Component.empty();
        }

        @Override
        public Component plate() {
            return immortal ? Component.translatable("immortality.menu.remaining", remaining) : Component.empty();
        }

        @Override
        public boolean faded() {
            return !immortal;
        }
    }

    public ImmortalityStudioScreen() {
        this(null);
    }

    public ImmortalityStudioScreen(Screen parent) {
        super(Component.translatable("immortality.menu.title"), parent);
        enabledRow = new ToggleRow(0, 0, 10, ROW, Component.translatable("immortality.menu.enabled"),
                () -> state().getBoolean("enabled"), value -> send(COMMAND + (value ? "enable" : "disable")));
        durationRow = new NumberRow(0, 0, 10, ROW, Component.translatable("immortality.menu.duration"),
                () -> state().getInt("duration"), value -> commitLater("duration", COMMAND + "setduration " + value),
                1, MAX_SECONDS, 5);
        clearRow = new ActionRow(0, 0, 10, ROW, Component.translatable("immortality.menu.clear_all"),
                () -> Component.translatable("immortality.menu.action.clear"), () -> send(COMMAND + "clearall"));
        playerRow = new ToggleRow(0, 0, 10, ROW, Component.translatable("studio.immortality.player"),
                () -> chosenTile() != null && chosenTile().immortal(), this::grantChosen);
        playerRow.hint("immortality.menu.disabled");
    }

    private static CompoundTag state() {
        return MenuData.state(ModuleMenuStates.IMMORTALITY);
    }

    private static ItemStack head(String player) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.getOrCreateTag().putString("SkullOwner", player);
        return head;
    }

    private void ensureBuilt() {
        CompoundTag state = state();
        if (state == builtFor) return;
        builtFor = state;
        List<PlayerTile> built = new ArrayList<>();
        int immortal = 0;
        for (Tag stored : state.getList(ModuleMenuStates.IMMORTAL_PLAYERS, Tag.TAG_COMPOUND)) {
            CompoundTag player = (CompoundTag) stored;
            boolean on = player.getBoolean("immortal");
            if (on) immortal++;
            built.add(new PlayerTile(player.getString("name"), head(player.getString("name")), on,
                    player.getInt("remaining")));
        }
        tiles = built;
        nodes = List.of(StudioNav.Node.item(PLAYERS, Component.translatable("immortality.menu.tab.players"),
                        Component.literal(immortal + "/" + built.size()), 0, false),
                StudioNav.Node.item(SETTINGS, Component.translatable("immortality.menu.tab.main"), Component.empty(),
                        0, false));
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.IMMORTALITY;
    }

    @Override
    protected Object stamp() {
        return state();
    }

    @Override
    protected List<StudioNav.Node> nodes() {
        ensureBuilt();
        return nodes;
    }

    @Override
    protected String selectedNode() {
        return nodeKey;
    }

    @Override
    protected void selectNode(StudioNav.Node node) {
        nodeKey = node.key();
        chosen = null;
    }

    @Override
    protected Component stats() {
        if (state().isEmpty()) return Component.translatable("battlecraft.menu.waiting");
        return Component.translatable(state().getBoolean("enabled") ? "studio.immortality.stats.on"
                : "studio.immortality.stats.off", state().getInt("duration"));
    }

    @Override
    protected List<com.persiki84.shared.client.ui.UiButton> headerButtons() {
        return List.of();
    }

    @Override
    protected List<? extends StudioTile> tiles() {
        ensureBuilt();
        return PLAYERS.equals(nodeKey) ? tiles : List.of();
    }

    private PlayerTile chosenTile() {
        for (PlayerTile tile : tiles) {
            if (tile.player().equals(chosen)) return tile;
        }
        return null;
    }

    @Override
    protected int selectedTile() {
        List<? extends StudioTile> shown = tiles();
        for (int index = 0; index < shown.size(); index++) {
            if (((PlayerTile) shown.get(index)).player().equals(chosen)) return index;
        }
        return -1;
    }

    @Override
    protected void selectTile(int index) {
        chosen = index < 0 ? null : tiles.get(index).player();
    }

    @Override
    protected String tileKey(int index) {
        return PLAYERS.equals(nodeKey) && index < tiles.size() ? tiles.get(index).player() : null;
    }

    @Override
    protected void activateTile(int index) {
        if (index >= tiles.size()) return;
        PlayerTile tile = tiles.get(index);
        grant(tile.player(), !tile.immortal());
    }

    // WHY: выдача отказывается работать при выключенном модуле, а выключение чистит список
    // WHY: неуязвимых, поэтому переключатель игрока заперт, а не молча ничего не делает
    private void grantChosen(boolean wanted) {
        if (chosen != null) grant(chosen, wanted);
    }

    private void grant(String player, boolean wanted) {
        if (!state().getBoolean("enabled")) return;
        send(COMMAND + (wanted ? "give " : "remove ") + player);
    }

    @Override
    protected Object inspectorSubject() {
        return nodeKey + "/" + chosen;
    }

    @Override
    protected void buildInspector(StudioStack stack, int x, int width) {
        if (SETTINGS.equals(nodeKey) || chosen == null) {
            stack.add(sized(settingsHeading, width), x);
            stack.add(sized(enabledRow, width), x);
            stack.add(sized(durationRow, width), x);
            stack.add(sized(clearRow, width), x);
            return;
        }
        if (state().getBoolean("enabled")) playerRow.unblock();
        else playerRow.block(Component.translatable("immortality.menu.disabled"));
        stack.add(sized(playerRow, width), x);
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    @Override
    protected List<StudioMenu.Action> tileActions(int index) {
        PlayerTile tile = tiles.get(index);
        return List.of(StudioMenu.Action.of(tile.immortal() ? "studio.immortality.remove" : "studio.immortality.give",
                () -> grant(tile.player(), !tile.immortal())).when(state().getBoolean("enabled")));
    }

    @Override
    protected List<StudioMenu.Action> bulkActions(List<Integer> indices) {
        boolean enabled = state().getBoolean("enabled");
        return List.of(new StudioMenu.Action(Component.translatable("studio.immortality.give_many", indices.size()),
                        () -> grantAll(indices, true), false, false, enabled),
                new StudioMenu.Action(Component.translatable("studio.immortality.remove_many", indices.size()),
                        () -> grantAll(indices, false), true, false, enabled));
    }

    private void grantAll(List<Integer> indices, boolean wanted) {
        for (int index : indices) {
            if (index < tiles.size()) grant(tiles.get(index).player(), wanted);
        }
    }

    @Override
    protected int artHeight() {
        return PLAYERS.equals(nodeKey) && chosenTile() != null ? ART : 0;
    }

    @Override
    protected void renderArt(GuiGraphics graphics, float left, float top, float width, float appear,
                             int mouseX, int mouseY) {
        PlayerTile tile = chosenTile();
        if (tile == null) return;
        UiRender.textTrackedFit(graphics, this.font, tile.name(), left + width / 2.0f, top, 14.0f, width, 0.95f, 0.0f,
                UiTheme.alpha(UiAccent.text(), appear), false);
        Component detail = tile.immortal() ? tile.plate() : Component.translatable("studio.immortality.mortal");
        UiRender.textTrackedFit(graphics, this.font, detail, left + width / 2.0f, top + 16.0f, 10.0f, width,
                LINE_SCALE, 0.0f, UiTheme.alpha(UiAccent.textDim(), appear), false);
    }

    @Override
    protected Component emptyCanvas() {
        if (state().isEmpty()) return Component.translatable("battlecraft.menu.waiting");
        return Component.translatable(SETTINGS.equals(nodeKey) ? "studio.immortality.settings_canvas"
                : "immortality.menu.no_players");
    }

    @Override
    public void tick() {
        if (ticks % 20 == 0) MenuData.request(ModuleMenuStates.IMMORTALITY);
        super.tick();
    }
}
