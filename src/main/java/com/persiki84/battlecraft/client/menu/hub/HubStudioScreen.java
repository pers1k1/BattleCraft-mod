package com.persiki84.battlecraft.client.menu.hub;

import com.persiki84.battlecraft.menu.BattleCraftMenuState;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuScreens;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.client.menu.studio.StudioScreen;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.menu.studio.StudioTile;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// WHY: хаб /bc собран как студия: слева всё, чем управляют, в центре разделы и модули плитками со
// WHY: значками, чек-лист старта матча плитками требований, а числа матча строками своей группы.
// WHY: Двойной щелчок по разделу открывает его, правая кнопка даёт те же действия, что справа
public final class HubStudioScreen extends StudioScreen {
    static final String MATCH = "match";
    static final String SECTIONS = "sections";
    static final String MODULES = "modules";
    static final String GROUP = "group:";
    static final String COMMANDS = "commands:";

    private static final String MENU_ID = BattleCraftMenuState.MENU_ID;
    private static final String COMMAND = "battlecraft ";
    private static final String LOBBY = "LOBBY";
    private static final int ART = 52;
    private static final float LINE_SCALE = 0.72f;
    private static final Map<String, String> RULE_ICONS = Map.of(
            "mod_enabled", "minecraft:lever", "capture_points", "minecraft:beacon",
            "final_point", "minecraft:nether_star", "teams", "minecraft:white_banner",
            "team_bases", "minecraft:red_bed", "lobby_point", "minecraft:compass",
            "join_grace", "minecraft:clock", "min_players", "minecraft:player_head",
            "ready_share", "minecraft:lime_dye");

    private final HubInspector rows = new HubInspector(this);
    private final HubSettings settings = new HubSettings(this);
    private final UiButton matchButton;
    private String nodeKey = MATCH;
    private String chosenTile;
    private CompoundTag builtFor;
    private List<StudioNav.Node> nodes = List.of();
    private List<HubTile> tiles = List.of();

    record HubTile(String key, ItemStack icon, Component name, Component corner, Component plate, boolean faded)
            implements StudioTile {
    }

    public HubStudioScreen() {
        this(null);
    }

    public HubStudioScreen(Screen parent) {
        super(Component.translatable("battlecraft.menu.hub.title"), parent);
        matchButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.empty(), pressed -> pressMatch())
                .hint("studio.hub.match_button.hint");
    }

    static CompoundTag state() {
        return MenuData.state(MENU_ID);
    }

    boolean admin() {
        return MenuData.admin(MENU_ID);
    }

    static boolean moduleOn(ModuleId module) {
        return state().getBoolean(BattleCraftMenuState.moduleKey(module));
    }

    private static boolean lobby() {
        return LOBBY.equals(state().getString("phase"));
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
        nodes = buildNodes();
        tiles = buildTiles();
    }

    private List<StudioNav.Node> buildNodes() {
        List<StudioNav.Node> list = new ArrayList<>();
        list.add(StudioNav.Node.item(MATCH, Component.translatable("studio.hub.node.match"), HubInspector.phase(), 0,
                false));
        if (!admin()) return list;
        list.add(StudioNav.Node.heading("heading:manage", Component.translatable("studio.hub.node.manage")));
        list.add(StudioNav.Node.item(SECTIONS, Component.translatable("studio.hub.node.sections"),
                Component.literal(String.valueOf(HubSection.values().length)), 0, false));
        list.add(StudioNav.Node.item(MODULES, Component.translatable("studio.hub.node.modules"),
                Component.literal(modulesOn() + "/" + ModuleId.values().length), 0, false));
        list.add(StudioNav.Node.heading("heading:settings", Component.translatable("battlecraft.menu.tab.settings")));
        for (int group = 0; group < HubSettings.GROUPS; group++) {
            list.add(StudioNav.Node.item(GROUP + group, Component.translatable(HubSettings.groupLabel(group)),
                    Component.empty(), 0, false));
        }
        list.add(StudioNav.Node.heading("heading:commands", Component.translatable("battlecraft.menu.tab.commands")));
        for (HubSettings.CommandList command : HubSettings.CommandList.values()) {
            list.add(StudioNav.Node.item(COMMANDS + command.name(), Component.translatable(command.group()),
                    Component.empty(), 0, false));
        }
        return list;
    }

    private static int modulesOn() {
        int on = 0;
        for (ModuleId module : ModuleId.values()) {
            if (moduleOn(module)) on++;
        }
        return on;
    }

    private List<HubTile> buildTiles() {
        if (SECTIONS.equals(nodeKey)) return sectionTiles();
        if (MODULES.equals(nodeKey)) return moduleTiles();
        if (MATCH.equals(nodeKey)) return requirementTiles();
        return List.of();
    }

    private static List<HubTile> sectionTiles() {
        List<HubTile> list = new ArrayList<>();
        for (HubSection section : HubSection.values()) {
            list.add(new HubTile(section.name(), section.icon(), Component.translatable(section.label()),
                    Component.empty(), Component.empty(), false));
        }
        return list;
    }

    private static List<HubTile> moduleTiles() {
        List<HubTile> list = new ArrayList<>();
        for (ModuleId module : ModuleId.values()) {
            boolean on = moduleOn(module);
            list.add(new HubTile(module.name(), HubSection.iconOf(module), Component.translatable(module.label()),
                    Component.empty(), Component.translatable(on ? "studio.kill.on" : "studio.kill.off"), !on));
        }
        return list;
    }

    // WHY: в лобби центр показывает, чего не хватает для старта: каждое условие плиткой, выполненное
    // WHY: гаснет, и сразу видно, что осталось сделать
    private static List<HubTile> requirementTiles() {
        if (!lobby()) return List.of();
        List<HubTile> list = new ArrayList<>();
        for (CompoundTag check : BattleCraftMenuState.requirementsOf(state())) {
            String rule = check.getString(BattleCraftMenuState.RULE_ID);
            boolean met = check.getBoolean(BattleCraftMenuState.RULE_MET);
            list.add(new HubTile(rule, HubSection.stackOf(RULE_ICONS.getOrDefault(rule, "minecraft:paper")),
                    Component.translatable("battlecraft.requirement." + rule), Component.empty(),
                    requirementValue(check), met));
        }
        return list;
    }

    static Component requirementValue(CompoundTag check) {
        if (check == null) return Component.empty();
        if (check.getBoolean(BattleCraftMenuState.RULE_MET)) return Component.translatable("battlecraft.menu.requirement.met");
        int need = check.getInt(BattleCraftMenuState.RULE_NEED);
        if (need <= 0) return Component.translatable("battlecraft.menu.requirement.unmet");
        return Component.translatable("battlecraft.menu.status.ratio", check.getInt(BattleCraftMenuState.RULE_HAVE), need);
    }

    CompoundTag requirement(String rule) {
        for (CompoundTag check : BattleCraftMenuState.requirementsOf(state())) {
            if (check.getString(BattleCraftMenuState.RULE_ID).equals(rule)) return check;
        }
        return null;
    }

    static boolean solvable(String rule) {
        return switch (rule) {
            case "mod_enabled", "capture_points", "final_point", "teams", "min_players", "ready_share", "join_grace" -> true;
            default -> false;
        };
    }

    void solve(String rule) {
        switch (rule) {
            case "mod_enabled" -> now("toggle");
            case "capture_points", "final_point" -> MenuScreens.open("capturepoints", this);
            case "teams" -> MenuScreens.open(ModuleMenuStates.TEAMS, this);
            case "min_players", "ready_share" -> now("ready");
            case "join_grace" -> now("force grace");
            default -> { }
        }
    }

    void open(HubSection section) {
        flushCommits();
        MenuScreens.open(section.menuId(), this);
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
        chosenTile = null;
        builtFor = null;
        grid.reset();
    }

    @Override
    protected boolean gridCanvas() {
        return MATCH.equals(nodeKey) || SECTIONS.equals(nodeKey) || MODULES.equals(nodeKey);
    }

    @Override
    protected List<? extends StudioTile> tiles() {
        ensureBuilt();
        return tiles;
    }

    @Override
    protected int selectedTile() {
        ensureBuilt();
        for (int index = 0; index < tiles.size(); index++) {
            if (tiles.get(index).key().equals(chosenTile)) return index;
        }
        return -1;
    }

    @Override
    protected void selectTile(int index) {
        chosenTile = index < 0 ? null : tiles.get(index).key();
    }

    @Override
    protected String tileKey(int index) {
        ensureBuilt();
        return MODULES.equals(nodeKey) && index < tiles.size() ? tiles.get(index).key() : null;
    }

    @Override
    protected void activateTile(int index) {
        ensureBuilt();
        if (index >= tiles.size()) return;
        String key = tiles.get(index).key();
        if (SECTIONS.equals(nodeKey)) open(HubSection.valueOf(key));
        if (MODULES.equals(nodeKey)) toggleModule(ModuleId.valueOf(key));
        if (MATCH.equals(nodeKey) && admin() && solvable(key)) solve(key);
    }

    private void toggleModule(ModuleId module) {
        now("module " + module.id() + " " + !moduleOn(module));
    }

    @Override
    protected Component stats() {
        if (state().isEmpty()) return Component.translatable("battlecraft.menu.waiting");
        return Component.translatable("studio.hub.stats", HubInspector.phase(), modulesOn(), ModuleId.values().length);
    }

    @Override
    protected List<UiButton> headerButtons() {
        if (!admin()) return List.of();
        String key = !lobby() ? (isArmed("force-stop") ? "studio.sure" : "studio.hub.stop") : "studio.hub.start";
        matchButton.setMessage(Component.translatable(key));
        return List.of(matchButton);
    }

    private void pressMatch() {
        if (lobby()) {
            now("force start");
            return;
        }
        rows.pressStop();
        layout();
    }

    @Override
    protected Object inspectorSubject() {
        return nodeKey + "/" + chosenTile;
    }

    @Override
    protected void buildInspector(StudioStack stack, int x, int width) {
        if (MATCH.equals(nodeKey)) {
            if (chosenTile != null && requirement(chosenTile) != null) {
                rows.buildRequirement(stack, x, width, requirement(chosenTile));
            } else {
                rows.buildMatch(stack, x, width);
            }
            return;
        }
        if (SECTIONS.equals(nodeKey) && chosenTile != null) rows.buildSection(stack, x, width, HubSection.valueOf(chosenTile));
        if (MODULES.equals(nodeKey) && chosenTile != null) rows.buildModule(stack, x, width, ModuleId.valueOf(chosenTile));
    }

    @Override
    protected void buildCanvas(StudioStack stack, int x, int width) {
        if (nodeKey.startsWith(GROUP)) {
            settings.buildGroup(stack, x, width, Integer.parseInt(nodeKey.substring(GROUP.length())));
            return;
        }
        if (nodeKey.startsWith(COMMANDS)) {
            settings.buildCommands(stack, x, width, commandList());
        }
    }

    private HubSettings.CommandList commandList() {
        if (!nodeKey.startsWith(COMMANDS)) return null;
        return HubSettings.CommandList.valueOf(nodeKey.substring(COMMANDS.length()));
    }

    @Override
    protected int artHeight() {
        return artTitle() == null ? 0 : ART;
    }

    private Component artTitle() {
        if (SECTIONS.equals(nodeKey) || MODULES.equals(nodeKey)) {
            if (chosenTile == null) return Component.translatable(SECTIONS.equals(nodeKey)
                    ? "studio.hub.node.sections" : "studio.hub.node.modules");
            return SECTIONS.equals(nodeKey) ? Component.translatable(HubSection.valueOf(chosenTile).label())
                    : Component.translatable(ModuleId.valueOf(chosenTile).label());
        }
        if (nodeKey.startsWith(GROUP)) {
            return Component.translatable(HubSettings.groupLabel(Integer.parseInt(nodeKey.substring(GROUP.length()))));
        }
        HubSettings.CommandList list = commandList();
        return list == null ? null : Component.translatable(list.group());
    }

    private Component artText() {
        if (SECTIONS.equals(nodeKey)) {
            return chosenTile == null ? Component.translatable("studio.hub.sections.about")
                    : Component.translatable(HubSection.valueOf(chosenTile).label() + ".hint");
        }
        if (MODULES.equals(nodeKey)) {
            return chosenTile == null ? Component.translatable("studio.hub.modules.about")
                    : Component.translatable(ModuleId.valueOf(chosenTile).label() + ".hint");
        }
        if (nodeKey.startsWith(GROUP)) {
            return Component.translatable("studio.hub.group." + HubSettings.groupKey(
                    Integer.parseInt(nodeKey.substring(GROUP.length()))) + ".about");
        }
        return Component.translatable("studio.hub.commands.about");
    }

    @Override
    protected void renderArt(GuiGraphics graphics, float left, float top, float width, float appear,
                             int mouseX, int mouseY) {
        Component title = artTitle();
        if (title == null) return;
        UiRender.textTrackedFit(graphics, this.font, title, left + width / 2.0f, top, 14.0f, width, 0.95f, 0.0f,
                UiTheme.alpha(UiAccent.text(), appear), false);
        List<net.minecraft.util.FormattedCharSequence> lines = UiRender.split(graphics, this.font, artText(),
                LINE_SCALE, (int) (width / LINE_SCALE));
        float y = top + 17.0f;
        for (int index = 0; index < Math.min(3, lines.size()); index++) {
            UiRender.textCentered(graphics, this.font, Component.literal(UiRender.flatten(lines.get(index))),
                    left + width / 2.0f, y, LINE_SCALE, UiTheme.alpha(UiAccent.textDim(), appear), false);
            y += this.font.lineHeight * LINE_SCALE + 2.0f;
        }
    }

    @Override
    protected List<StudioMenu.Action> tileActions(int index) {
        ensureBuilt();
        if (index >= tiles.size()) return List.of();
        String key = tiles.get(index).key();
        if (SECTIONS.equals(nodeKey)) {
            return List.of(StudioMenu.Action.of("studio.hub.open", () -> open(HubSection.valueOf(key))));
        }
        if (MODULES.equals(nodeKey)) return moduleActions(ModuleId.valueOf(key));
        if (admin() && solvable(key)) return List.of(StudioMenu.Action.of("studio.hub.requirement.solve", () -> solve(key)));
        return List.of();
    }

    private List<StudioMenu.Action> moduleActions(ModuleId module) {
        List<StudioMenu.Action> actions = new ArrayList<>();
        actions.add(StudioMenu.Action.of(moduleOn(module) ? "studio.hub.module.disable" : "studio.hub.module.enable",
                () -> toggleModule(module)));
        HubSection section = HubSection.of(module);
        if (section != null) actions.add(StudioMenu.Action.of("studio.hub.open", () -> open(section)));
        return actions;
    }

    @Override
    protected List<StudioMenu.Action> bulkActions(List<Integer> indices) {
        return List.of(new StudioMenu.Action(Component.translatable("studio.hub.module.enable_many", indices.size()),
                        () -> setModules(indices, true), false, false, true),
                new StudioMenu.Action(Component.translatable("studio.hub.module.disable_many", indices.size()),
                        () -> setModules(indices, false), true, false, true));
    }

    private void setModules(List<Integer> indices, boolean on) {
        for (int index : indices) {
            if (index < tiles.size()) now("module " + ModuleId.valueOf(tiles.get(index).key()).id() + " " + on);
        }
    }

    @Override
    protected void buildBulk(StudioStack stack, int x, int width, List<Integer> indices) {
    }

    @Override
    protected Component emptyCanvas() {
        if (state().isEmpty()) return Component.translatable("battlecraft.menu.waiting");
        return Component.translatable("studio.hub.match.running", HubInspector.phase());
    }

    @Override
    protected boolean submit() {
        return settings.submit(commandList()) || super.submit();
    }

    @Override
    protected void refreshed() {
        if (!admin() && !MATCH.equals(nodeKey)) nodeKey = MATCH;
    }

    @Override
    public void tick() {
        if (ticks % 10 == 0) MenuData.request(MENU_ID);
        super.tick();
    }

    void now(String tail) {
        send(COMMAND + tail);
    }

    void later(String key, String tail) {
        commitLater(key, COMMAND + tail);
    }

    boolean isArmed(String key) {
        return armed(key);
    }

    boolean confirmed(String key) {
        return confirm(key);
    }
}
