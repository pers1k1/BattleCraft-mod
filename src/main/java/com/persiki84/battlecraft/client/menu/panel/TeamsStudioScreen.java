package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.client.menu.TeamSelectScreen;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.client.menu.studio.StudioScreen;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.menu.studio.StudioTile;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// WHY: команда видна знаменем своего цвета с числом игроков: состав и цвет правятся на ней самой,
// WHY: а новая создаётся из шапки с готовыми именами, как раньше строками
public final class TeamsStudioScreen extends StudioScreen {
    private static final String COMMAND = "battlecraft team ";
    private static final String NODE = "teams";
    private static final int ROW = 22;
    private static final int MAX_NAME = 32;
    private static final int ART = 40;
    private static final float LINE_SCALE = 0.72f;
    private static final String[] SUGGESTED = {"red", "blue", "green", "yellow"};
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_.+-]+");
    private static final ChatFormatting[] COLORS = {ChatFormatting.WHITE, ChatFormatting.RED, ChatFormatting.BLUE,
            ChatFormatting.GREEN, ChatFormatting.YELLOW, ChatFormatting.GOLD, ChatFormatting.AQUA,
            ChatFormatting.LIGHT_PURPLE, ChatFormatting.DARK_RED, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN,
            ChatFormatting.DARK_AQUA, ChatFormatting.DARK_PURPLE, ChatFormatting.GRAY, ChatFormatting.DARK_GRAY,
            ChatFormatting.BLACK};
    private static final Map<ChatFormatting, String> BANNERS = Map.ofEntries(
            Map.entry(ChatFormatting.WHITE, "white"), Map.entry(ChatFormatting.RED, "red"),
            Map.entry(ChatFormatting.BLUE, "light_blue"), Map.entry(ChatFormatting.GREEN, "lime"),
            Map.entry(ChatFormatting.YELLOW, "yellow"), Map.entry(ChatFormatting.GOLD, "orange"),
            Map.entry(ChatFormatting.AQUA, "cyan"), Map.entry(ChatFormatting.LIGHT_PURPLE, "magenta"),
            Map.entry(ChatFormatting.DARK_RED, "red"), Map.entry(ChatFormatting.DARK_BLUE, "blue"),
            Map.entry(ChatFormatting.DARK_GREEN, "green"), Map.entry(ChatFormatting.DARK_AQUA, "cyan"),
            Map.entry(ChatFormatting.DARK_PURPLE, "purple"), Map.entry(ChatFormatting.GRAY, "light_gray"),
            Map.entry(ChatFormatting.DARK_GRAY, "gray"), Map.entry(ChatFormatting.BLACK, "black"));

    private final List<UiButton> header;
    private final UiButton addButton;
    private final FieldRow nameRow;
    private final HeadingRow membersHeading = new HeadingRow(0, 0, 10, ROW,
            Component.translatable("studio.teams.group.members"));
    private final HeadingRow newHeading = new HeadingRow(0, 0, 10, ROW, Component.translatable("studio.teams.group.new"));
    private final Map<String, MenuRow> made = new HashMap<>();
    private final List<StudioNav.Node> nodes = List.of(StudioNav.Node.item(NODE,
            Component.translatable("battlecraft.teams.title"), Component.empty(), 0, false));
    private boolean creating;
    private String chosen;
    private String pendingName;
    private CompoundTag builtFor;
    private List<TeamTile> tiles = List.of();

    record TeamTile(String team, ItemStack icon, Component plate) implements StudioTile {
        @Override
        public Component corner() {
            return Component.empty();
        }

        @Override
        public Component name() {
            return Component.literal(team);
        }
    }

    public TeamsStudioScreen() {
        this(null);
    }

    public TeamsStudioScreen(Screen parent) {
        super(Component.translatable("battlecraft.teams.title"), parent);
        addButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.empty(), pressed -> toggleCreate());
        UiButton pickButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW,
                Component.translatable("battlecraft.teams.pick"), pressed -> TeamSelectScreen.open());
        header = List.of(addButton, pickButton);
        nameRow = new FieldRow(0, 0, 10, ROW, Component.translatable("battlecraft.teams.name"),
                Component.translatable("battlecraft.teams.name.placeholder"), "", MAX_NAME, value -> { });
        nameRow.hint("battlecraft.teams.name.hint");
    }

    private static CompoundTag state() {
        return MenuData.state(ModuleMenuStates.TEAMS);
    }

    private static PlayerTeam team(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null || name == null ? null : minecraft.level.getScoreboard().getPlayerTeam(name);
    }

    private static ItemStack banner(String name) {
        PlayerTeam team = team(name);
        String dye = BANNERS.getOrDefault(team == null ? ChatFormatting.WHITE : team.getColor(), "white");
        return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft", dye + "_banner")));
    }

    private void ensureBuilt() {
        CompoundTag state = state();
        if (state == builtFor) return;
        builtFor = state;
        List<TeamTile> built = new ArrayList<>();
        for (Tag stored : state.getList("teams", Tag.TAG_COMPOUND)) {
            CompoundTag team = (CompoundTag) stored;
            built.add(new TeamTile(team.getString("name"), banner(team.getString("name")),
                    Component.translatable("battlecraft.teams.players", team.getInt("players"))));
        }
        tiles = built;
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.TEAMS;
    }

    @Override
    protected Object stamp() {
        return state();
    }

    @Override
    protected List<StudioNav.Node> nodes() {
        return nodes;
    }

    @Override
    protected String selectedNode() {
        return NODE;
    }

    @Override
    protected void selectNode(StudioNav.Node node) {
    }

    @Override
    protected Component stats() {
        if (state().isEmpty()) return Component.translatable("battlecraft.menu.waiting");
        ensureBuilt();
        return Component.translatable("studio.teams.stats", tiles.size());
    }

    @Override
    protected List<UiButton> headerButtons() {
        addButton.setMessage(Component.translatable(creating ? "studio.points.cancel" : "studio.teams.add"));
        return header;
    }

    private void toggleCreate() {
        creating = !creating;
        layout();
        if (creating) setFocused(nameRow);
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
            if (tiles.get(index).team().equals(chosen)) return index;
        }
        return -1;
    }

    @Override
    protected void selectTile(int index) {
        creating = false;
        chosen = index < 0 ? null : tiles.get(index).team();
    }

    @Override
    protected Object inspectorSubject() {
        return creating ? "new" : chosen;
    }

    @Override
    protected void buildInspector(StudioStack stack, int x, int width) {
        if (creating) {
            buildCreate(stack, x, width);
            return;
        }
        PlayerTeam team = team(chosen);
        if (team == null) return;
        stack.add(sized(colorRow(), width), x);
        stack.add(sized(membersHeading, width), x, ROW / 3);
        for (String player : team.getPlayers()) {
            stack.add(sized(row("member:" + player, () -> reading(Component.literal(player))), width), x);
        }
        stack.add(sized(row("delete", () -> new ActionRow(0, 0, 10, ROW, Component.translatable("studio.teams.delete"),
                () -> Component.translatable(armed("drop-team") ? "studio.sure" : "studio.delete"),
                this::pressDelete).alerting()), width), x, ROW / 3);
    }

    private MenuRow colorRow() {
        return row("color", () -> {
            List<Component> options = new ArrayList<>();
            for (ChatFormatting color : COLORS) {
                options.add(Component.translatable("studio.teams.color." + color.getName()));
            }
            return new PickRow(0, 0, 10, ROW, Component.translatable("studio.teams.color"), options,
                    this::colorIndex, picked -> send("team modify " + chosen + " color " + COLORS[picked].getName()))
                    .icon(() -> banner(chosen));
        });
    }

    private int colorIndex() {
        PlayerTeam team = team(chosen);
        if (team == null) return 0;
        for (int index = 0; index < COLORS.length; index++) {
            if (COLORS[index] == team.getColor()) return index;
        }
        return 0;
    }

    private void buildCreate(StudioStack stack, int x, int width) {
        stack.add(sized(newHeading, width), x);
        stack.add(sized(nameRow, width), x);
        stack.add(sized(row("create", () -> new ActionRow(0, 0, 10, ROW, Component.translatable("battlecraft.teams.new"),
                () -> Component.translatable("battlecraft.teams.action.create"), this::createTyped)), width), x);
        for (String name : SUGGESTED) {
            if (taken(name)) continue;
            stack.add(sized(row("suggest:" + name, () -> new ActionRow(0, 0, 10, ROW,
                    Component.translatable("battlecraft.teams.create", name),
                    () -> Component.translatable("battlecraft.teams.action.create"), () -> create(name))), width), x);
        }
    }

    private boolean taken(String name) {
        ensureBuilt();
        for (TeamTile tile : tiles) {
            if (tile.team().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private void createTyped() {
        if (create(nameRow.value().trim())) nameRow.box().setValue("");
    }

    // WHY: команда ждёт brigadier word(): пробел или кириллица в имени уронят разбор ещё до сервера
    private boolean create(String name) {
        if (name.isEmpty()) {
            MenuFeedback.show(Component.translatable("battlecraft.teams.error.name"), true);
            return false;
        }
        if (!WORD.matcher(name).matches()) {
            MenuFeedback.show(Component.translatable("battlecraft.teams.error.chars"), true);
            return false;
        }
        send(COMMAND + "add " + name);
        pendingName = name;
        creating = false;
        layout();
        return true;
    }

    private void pressDelete() {
        if (chosen != null && confirm("drop-team")) remove(chosen);
    }

    private void remove(String name) {
        send(COMMAND + "remove " + name);
        chosen = null;
        layout();
    }

    @Override
    protected void deleteSelected() {
        if (chosen == null) return;
        if (confirm("delete:" + chosen)) {
            remove(chosen);
            return;
        }
        MenuFeedback.show(Component.translatable("studio.delete_again"), false);
    }

    @Override
    protected boolean submit() {
        if (creating && nameRow.capturing()) {
            createTyped();
            return true;
        }
        return super.submit();
    }

    @Override
    protected List<StudioMenu.Action> tileActions(int index) {
        String name = tiles.get(index).team();
        return List.of(StudioMenu.Action.careful("studio.teams.delete", () -> remove(name)));
    }

    @Override
    protected List<StudioMenu.Action> canvasActions() {
        return List.of(StudioMenu.Action.of("studio.teams.add", this::toggleCreate));
    }

    @Override
    protected int artHeight() {
        return creating || team(chosen) == null ? 0 : ART;
    }

    @Override
    protected void renderArt(GuiGraphics graphics, float left, float top, float width, float appear,
                             int mouseX, int mouseY) {
        PlayerTeam team = team(chosen);
        if (team == null || creating) return;
        Integer rgb = team.getColor().getColor();
        int tone = rgb == null ? UiAccent.text() : 0xFF000000 | rgb;
        UiRender.textTrackedFit(graphics, this.font, Component.literal(team.getName()), left + width / 2.0f, top, 14.0f,
                width, 0.95f, 0.0f, UiTheme.alpha(tone, appear), false);
        UiRender.textTrackedFit(graphics, this.font, Component.translatable("battlecraft.teams.players",
                team.getPlayers().size()), left + width / 2.0f, top + 16.0f, 10.0f, width, LINE_SCALE, 0.0f,
                UiTheme.alpha(UiAccent.textDim(), appear), false);
    }

    @Override
    protected Component emptyCanvas() {
        if (state().isEmpty()) return Component.translatable("battlecraft.menu.waiting");
        return Component.translatable("battlecraft.teams.empty");
    }

    @Override
    protected void refreshed() {
        if (pendingName != null && taken(pendingName)) {
            chosen = pendingName;
            pendingName = null;
        }
    }

    @Override
    public void tick() {
        if (ticks % 20 == 0) MenuData.request(ModuleMenuStates.TEAMS);
        super.tick();
    }

    private MenuRow row(String key, java.util.function.Supplier<MenuRow> maker) {
        return made.computeIfAbsent(key, unused -> maker.get());
    }

    private static MenuRow reading(Component label) {
        ActionRow row = new ActionRow(0, 0, 10, ROW, label, Component::empty, () -> { });
        row.active = false;
        return row;
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }
}
