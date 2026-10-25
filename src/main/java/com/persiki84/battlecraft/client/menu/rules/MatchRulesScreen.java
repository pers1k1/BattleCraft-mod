package com.persiki84.battlecraft.client.menu.rules;

import com.persiki84.battlecraft.client.menu.panel.GameRuleGroups;
import com.persiki84.battlecraft.client.menu.panel.craft.CraftTile;
import com.persiki84.battlecraft.client.menu.panel.craft.CraftView;
import com.persiki84.battlecraft.client.menu.panel.mode.KitTile;
import com.persiki84.battlecraft.client.menu.panel.mode.ModeView;
import com.persiki84.battlecraft.craft.CraftBan;
import com.persiki84.battlecraft.craft.CraftPhase;
import com.persiki84.battlecraft.craft.CraftRules;
import com.persiki84.battlecraft.mode.ModeId;
import com.persiki84.battlecraft.rules.MatchRulesMenuState;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.ParagraphRow;
import com.persiki84.shared.client.menu.SearchField;
import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.client.menu.studio.StudioScreen;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.menu.studio.StudioTile;
import com.persiki84.shared.client.ui.UiButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// WHY: правила для игрока стоят на студии /bc, а не на своём экране: тот же вид, поиск, плитки и
// WHY: анимации, что у оператора, без второй копии. Правок нет: меню без прав, а сервер всё равно
// WHY: проверяет права у каждой команды
public final class MatchRulesScreen extends StudioScreen {
    private static final int REQUEST_TICKS = 40;
    private static final String MODE = "mode:";
    private static final String CRAFT = "craft:";
    private static final String RULES = "rules:";
    private static final Component WAITING = Component.translatable("battlecraft.menu.waiting");
    private static final Component NO_KIT = Component.translatable("rules.match.kit.empty");
    private static final Component NO_BANS = Component.translatable("rules.match.craft.empty");
    private static final Component FRESH = Component.translatable("rules.match.craft.fresh");

    private final SearchField search;
    private final RulesModeRows modeRows = new RulesModeRows();
    private final Map<String, RulesGameRows> ruleRows = new LinkedHashMap<>();
    private final RulesInspector inspector = new RulesInspector();
    private final List<ParagraphRow> paragraphs = new ArrayList<>();
    private ModeView modes = ModeView.EMPTY;
    private CraftView crafts = CraftView.EMPTY;
    private CompoundTag builtFor;
    private List<CraftBan> bansFor;
    private boolean runningFor;
    private int version;
    private Component statsText = WAITING;
    private List<StudioNav.Node> nodes = List.of();
    private List<StudioTile> shown = List.of();
    private String shownQuery = "";
    private String node = "";
    private String subject = "";
    private int selected = -1;

    public MatchRulesScreen(Screen parent) {
        super(Component.translatable("rules.match.title"), parent);
        search = new SearchField(Component.translatable("battlecraft.menu.search"), query -> searched());
        ruleRows.put(RULES + "client", new RulesGameRows(GameRuleGroups.CLIENT_RULES));
        ruleRows.put(RULES + "combat", new RulesGameRows(GameRuleGroups.COMBAT_RULES));
        ruleRows.put(RULES + "parkour", new RulesGameRows(GameRuleGroups.PARKOUR_RULES));
    }

    public static void open(Screen parent) {
        MenuData.request(MatchRulesMenuState.MENU_ID);
        Minecraft.getInstance().setScreen(new MatchRulesScreen(parent));
    }

    @Override
    protected String menuId() {
        return MatchRulesMenuState.MENU_ID;
    }

    // WHY: режимы приходят снимком меню, а запреты клиент держит сам с момента входа: штамп экрана
    // WHY: меняется от любого из двух источников, иначе снятый запрет оставался бы в инспекторе
    @Override
    protected Object stamp() {
        ensureBuilt();
        return version;
    }

    private void ensureBuilt() {
        CompoundTag state = MenuData.state(MatchRulesMenuState.MENU_ID);
        List<CraftBan> bans = CraftRules.clientBans();
        if (state == builtFor && bans == bansFor && CraftRules.clientRunning() == runningFor) return;
        builtFor = state;
        bansFor = bans;
        runningFor = CraftRules.clientRunning();
        version++;
        modes = state.contains(MatchRulesMenuState.MODE)
                ? ModeView.of(state.getCompound(MatchRulesMenuState.MODE)) : ModeView.EMPTY;
        crafts = CraftView.of(bans, runningFor);
        if (node.isEmpty() && modes.arrived()) node = MODE + current().id();
        nodes = RulesNodes.build(modes, crafts, ruleRows);
        statsText = modes.arrived() ? RulesNodes.stats(modes, current()) : WAITING;
        refreshNode();
    }

    // WHY: игрок первым видит режим, который идёт, а в лобби - тот, что выбран по умолчанию
    private ModeId current() {
        ModeId running = ModeId.byId(modes.running());
        return running != null ? running : modes.active();
    }

    private void refreshNode() {
        ModeId mode = modeOf(node);
        if (mode != null) modeRows.refresh(modes.settings(mode));
        paragraphs.clear();
        for (Component text : texts(mode)) {
            paragraphs.add(new ParagraphRow(text));
        }
        refreshTiles();
    }

    private List<Component> texts(ModeId mode) {
        if (mode != null) return RulesModeRows.about(modes.settings(mode));
        CraftPhase phase = phaseOf(node);
        if (phase != null) return List.of(Component.translatable("rules.match.craft.about." + phase.id()));
        return node.startsWith(RULES) ? List.of(Component.translatable("rules.match.rules.about")) : List.of();
    }

    private static ModeId modeOf(String key) {
        return key.startsWith(MODE) ? ModeId.byId(key.substring(MODE.length())) : null;
    }

    private static CraftPhase phaseOf(String key) {
        return key.startsWith(CRAFT) ? CraftPhase.byId(key.substring(CRAFT.length())) : null;
    }

    private void refreshTiles() {
        shownQuery = search.query().strip().toLowerCase(Locale.ROOT);
        List<StudioTile> tiles = new ArrayList<>();
        ModeId mode = modeOf(node);
        CraftPhase phase = phaseOf(node);
        if (mode != null) {
            for (KitTile tile : modes.settings(mode).kit()) {
                if (matches(tile.name())) tiles.add(tile);
            }
        } else if (phase != null) {
            for (CraftTile tile : crafts.tiles(phase)) {
                if (!matches(tile.name())) continue;
                tiles.add(new RuleBanTile(tile, CraftBanToasts.fresh(tile.key()) ? FRESH : Component.empty()));
            }
        }
        shown = List.copyOf(tiles);
        if (selected >= shown.size()) selected = -1;
        subject = node + ":" + selected;
    }

    private boolean matches(Component name) {
        return shownQuery.isEmpty() || name.getString().toLowerCase(Locale.ROOT).contains(shownQuery);
    }

    private void searched() {
        selected = -1;
        grid.reset();
        refreshTiles();
        layout();
    }

    @Override
    protected List<StudioNav.Node> nodes() {
        ensureBuilt();
        return nodes;
    }

    @Override
    protected String selectedNode() {
        return node;
    }

    @Override
    protected void selectNode(StudioNav.Node picked) {
        if (picked.key().equals(node) || !RulesNodes.selectable(picked.key())) return;
        markSeen();
        node = picked.key();
        selected = -1;
        grid.reset();
        refreshNode();
    }

    // WHY: метка «новое» снимается с фазы, которую игрок открывал, а не со всех разом при закрытии
    private void markSeen() {
        CraftPhase phase = phaseOf(node);
        if (phase == null) return;
        for (CraftTile tile : crafts.tiles(phase)) {
            CraftBanToasts.seen(tile.key());
        }
    }

    @Override
    protected Component stats() {
        ensureBuilt();
        return statsText;
    }

    @Override
    protected List<UiButton> headerButtons() {
        return List.of();
    }

    @Override
    protected SearchField search() {
        return search;
    }

    @Override
    protected boolean gridCanvas() {
        return !node.startsWith(RULES);
    }

    @Override
    protected List<? extends StudioTile> tiles() {
        ensureBuilt();
        return shown;
    }

    @Override
    protected int selectedTile() {
        return selected >= 0 && selected < shown.size() ? selected : -1;
    }

    @Override
    protected void selectTile(int index) {
        selected = index;
        subject = node + ":" + selected;
    }

    @Override
    protected void buildCanvas(StudioStack stack, int x, int width) {
        RulesGameRows rows = ruleRows.get(node);
        if (rows != null) rows.build(stack, x, width, shownQuery);
    }

    @Override
    protected Object inspectorSubject() {
        return subject;
    }

    @Override
    protected void buildInspector(StudioStack stack, int x, int width) {
        ensureBuilt();
        int index = selectedTile();
        if (index >= 0) {
            inspector.build(stack, x, width, shown.get(index));
            return;
        }
        for (ParagraphRow paragraph : paragraphs) {
            paragraph.setWidth(width);
            stack.add(paragraph, x);
        }
        if (modeOf(node) != null) modeRows.build(stack, x, width);
    }

    @Override
    protected Component emptyCanvas() {
        ensureBuilt();
        if (!modes.arrived()) return WAITING;
        return node.startsWith(CRAFT) ? NO_BANS : NO_KIT;
    }

    @Override
    public void tick() {
        if (ticks % REQUEST_TICKS == 0) MenuData.request(MatchRulesMenuState.MENU_ID);
        super.tick();
    }

    @Override
    public void onClose() {
        markSeen();
        super.onClose();
    }
}
