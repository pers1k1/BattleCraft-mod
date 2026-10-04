package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.Names;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.pick.ItemShelf;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.client.menu.studio.StudioScreen;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.menu.studio.StudioTile;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.zones.client.menu.ItemTurntable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

// WHY: награда за убийство показывается самим предметом с числом, как товар в скупке: что
// WHY: получит игрок, видно сразу, а замена идёт с полки двойным щелчком, без отдельного окна
public final class KillRewardStudioScreen extends StudioScreen {
    private static final String COMMAND = "killreward ";
    private static final String NODE = "reward";
    private static final int ROW = 22;
    private static final int MAX_AMOUNT = 64;
    private static final int ART = 110;
    private static final float TURNTABLE_HEIGHT = 70.0f;
    private static final float LINE_SCALE = 0.72f;

    private final ItemTurntable turntable = new ItemTurntable();
    private final UiButton shelfButton;
    private final List<UiButton> header;
    private final HeadingRow rewardHeading = new HeadingRow(0, 0, 10, ROW, Component.translatable("studio.kill.group"));
    private final ToggleRow enabledRow;
    private final NumberRow amountRow;
    private final ToggleRow teamRow;
    private final List<StudioNav.Node> nodes = List.of(StudioNav.Node.item(NODE,
            Component.translatable("studio.kill.node"), Component.empty(), 0, false));
    private boolean adding;
    private CompoundTag builtFor;
    private List<KillTile> tiles = List.of();

    record KillTile(ItemStack icon, Component name, Component corner, Component plate, boolean off)
            implements StudioTile {
        @Override
        public boolean faded() {
            return off || icon.isEmpty();
        }
    }

    public KillRewardStudioScreen() {
        this(null);
    }

    public KillRewardStudioScreen(Screen parent) {
        super(Component.translatable("killreward.menu.title"), parent);
        shelfButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.empty(), pressed -> switchShelf())
                .hint("studio.kill.change.hint");
        header = List.of(shelfButton);
        enabledRow = new ToggleRow(0, 0, 10, ROW, Component.translatable("killreward.menu.enabled"),
                () -> state().getBoolean("modEnabled"), value -> send(COMMAND + (value ? "enable" : "disable")));
        amountRow = new NumberRow(0, 0, 10, ROW, Component.translatable("killreward.menu.amount"),
                () -> state().getInt("rewardAmount"), value -> commitLater("amount", COMMAND + "setamount " + value),
                1, MAX_AMOUNT, 1);
        teamRow = new ToggleRow(0, 0, 10, ROW, Component.translatable("killreward.menu.team_kills"),
                () -> state().getBoolean("rewardTeamKills"), value -> send(COMMAND + "teamkills " + value));
        teamRow.hint("studio.kill.team.hint");
    }

    private static CompoundTag state() {
        return MenuData.state(ModuleMenuStates.KILL_REWARD);
    }

    private static ItemStack stackOf(String item) {
        ResourceLocation key = ResourceLocation.tryParse(item);
        Item found = key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
        return found == Items.AIR ? ItemStack.EMPTY : new ItemStack(found);
    }

    private void ensureBuilt() {
        CompoundTag state = state();
        if (state == builtFor) return;
        builtFor = state;
        if (state.isEmpty()) {
            tiles = List.of();
            return;
        }
        String item = state.getString("rewardItem");
        tiles = List.of(new KillTile(stackOf(item), Names.item(item),
                Component.translatable(state.getBoolean("modEnabled") ? "studio.kill.on" : "studio.kill.off"),
                Component.translatable("studio.kill.amount", state.getInt("rewardAmount")),
                !state.getBoolean("modEnabled")));
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.KILL_REWARD;
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
        return Component.translatable(state().getBoolean("modEnabled") ? "studio.kill.stats.on" : "studio.kill.stats.off");
    }

    @Override
    protected List<UiButton> headerButtons() {
        shelfButton.setMessage(Component.translatable(adding ? "studio.mode.done" : "studio.kill.change"));
        return header;
    }

    private void switchShelf() {
        flushCommits();
        adding = !adding;
        turntable.rest();
        layout();
    }

    @Override
    protected Object inspectorSubject() {
        return adding ? "shelf" : NODE;
    }

    @Override
    protected void buildInspector(StudioStack stack, int x, int width) {
        stack.add(sized(rewardHeading, width), x);
        if (!adding) stack.add(sized(enabledRow, width), x);
        stack.add(sized(amountRow, width), x);
        if (!adding) stack.add(sized(teamRow, width), x);
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    @Override
    protected List<? extends StudioTile> tiles() {
        ensureBuilt();
        return tiles;
    }

    @Override
    protected int selectedTile() {
        return tiles().isEmpty() ? -1 : 0;
    }

    @Override
    protected boolean shelfOpen() {
        return adding;
    }

    // WHY: награда хранится идентификатором в конфиге мода, поэтому теги предмета в неё не входят:
    // WHY: из инвентаря и из реестра предмет уходит одинаково, именем в реестре
    @Override
    protected void addPick(ItemShelf.Pick pick, int slot) {
        String item = BuiltInRegistries.ITEM.getKey(pick.stack().getItem()).toString();
        send(COMMAND + "setitem \"" + item + "\"");
        adding = false;
        turntable.rest();
        layout();
    }

    @Override
    protected Component shelfHint() {
        return Component.translatable("studio.kill.shelf.hint");
    }

    @Override
    protected List<StudioMenu.Action> tileActions(int index) {
        return canvasActions();
    }

    @Override
    protected List<StudioMenu.Action> nodeActions(StudioNav.Node node) {
        return canvasActions();
    }

    @Override
    protected List<StudioMenu.Action> canvasActions() {
        boolean enabled = state().getBoolean("modEnabled");
        return List.of(StudioMenu.Action.of("studio.kill.change", this::switchShelf),
                StudioMenu.Action.of(enabled ? "studio.kill.disable" : "studio.kill.enable",
                        () -> send(COMMAND + (enabled ? "disable" : "enable"))));
    }

    @Override
    protected int artHeight() {
        return adding || tiles().isEmpty() ? 0 : ART;
    }

    @Override
    protected void renderArt(GuiGraphics graphics, float left, float top, float width, float appear,
                             int mouseX, int mouseY) {
        if (adding || tiles().isEmpty()) return;
        KillTile tile = tiles.get(0);
        turntable.render(graphics, tile.icon(), left, top, width, TURNTABLE_HEIGHT);
        float y = top + TURNTABLE_HEIGHT + 4.0f;
        UiRender.textTrackedFit(graphics, this.font, tile.name(), left + width / 2.0f, y, 12.0f, width, 0.85f, 0.0f,
                UiTheme.alpha(UiAccent.text(), appear), false);
        UiRender.textTrackedFit(graphics, this.font, Component.translatable("studio.kill.art",
                state().getInt("rewardAmount")), left + width / 2.0f, y + 15.0f, 10.0f, width, LINE_SCALE, 0.0f,
                UiTheme.alpha(UiAccent.textDim(), appear), false);
    }

    @Override
    protected boolean pressArt(double mouseX, double mouseY) {
        if (artHeight() == 0 || !turntable.over(mouseX, mouseY)) return false;
        turntable.beginDrag();
        return true;
    }

    @Override
    protected boolean dragArt(double dragX, double dragY) {
        if (!turntable.dragging()) return false;
        turntable.drag(dragX, dragY);
        return true;
    }

    @Override
    protected void releaseArt() {
        turntable.endDrag();
    }

    @Override
    protected boolean scrollArt(double mouseX, double mouseY, double amount) {
        if (artHeight() == 0 || !turntable.over(mouseX, mouseY)) return false;
        turntable.magnify(amount);
        return true;
    }

    @Override
    protected Component emptyCanvas() {
        return Component.translatable("battlecraft.menu.waiting");
    }

    @Override
    public void tick() {
        if (ticks % 20 == 0) MenuData.request(ModuleMenuStates.KILL_REWARD);
        super.tick();
    }
}
