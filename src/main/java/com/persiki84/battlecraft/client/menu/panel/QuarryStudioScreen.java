package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.minimap.client.MapStudioScreen;
import com.persiki84.minimap.client.MapThing;
import com.persiki84.shared.Names;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.menu.studio.StudioTile;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

// WHY: карьер виден, как он стоит в мире: каждый блок точкой на карте, восстанавливающиеся
// WHY: погашены, у выбранного свой откат правится справа по его координатам, а виды блоков
// WHY: лежат плитками самих блоков с откатом и множителем добычи
public final class QuarryStudioScreen extends MapStudioScreen {
    static final String COMMAND = "quarry ";
    static final String MAP = "map";
    static final String TYPES = "types";
    static final String COMMON = "common";

    private static final int READY = 0xFF8FE3A0;
    private static final int RESTING = 0xFF7A808C;
    private static final int ART = 40;
    private static final float LINE_SCALE = 0.72f;

    private final QuarryInspector rows = new QuarryInspector(this);
    private final UiButton addButton;
    private final UiButton meButton;
    private String nodeKey = MAP;
    private String chosenBlock;
    private String chosenType;
    private CompoundTag builtFor;
    private List<StudioNav.Node> nodes = List.of();
    private List<MapThing> things = List.of();
    private List<TypeTile> types = List.of();

    record TypeTile(String block, ItemStack icon, Component name, Component plate, Component corner)
            implements StudioTile {
    }

    public QuarryStudioScreen() {
        this(null);
    }

    public QuarryStudioScreen(Screen parent) {
        super(Component.translatable("quarrymod.menu.title"), parent);
        addButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.translatable("studio.quarry.add"),
                pressed -> now("add")).hint("studio.quarry.add.hint");
        meButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.translatable("studio.map.me"),
                pressed -> centerOnPlayer()).hint("studio.map.me.hint");
    }

    static CompoundTag state() {
        return MenuData.state(ModuleMenuStates.QUARRY);
    }

    static ItemStack iconOf(String block) {
        ResourceLocation id = ResourceLocation.tryParse(block);
        Block found = id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(id);
        return found == Blocks.AIR ? ItemStack.EMPTY : new ItemStack(found.asItem());
    }

    static String key(CompoundTag block) {
        return block.getInt("x") + " " + block.getInt("y") + " " + block.getInt("z");
    }

    private void ensureBuilt() {
        CompoundTag state = state();
        if (state == builtFor) return;
        builtFor = state;
        things = buildThings(state);
        types = buildTypes(state);
        nodes = List.of(
                StudioNav.Node.item(MAP, Component.translatable("studio.quarry.node.map"),
                        Component.literal(String.valueOf(state.getInt("blocks"))), 0, false),
                StudioNav.Node.item(TYPES, Component.translatable("studio.quarry.node.types"),
                        Component.literal(String.valueOf(types.size())), 0, false),
                StudioNav.Node.item(COMMON, Component.translatable("studio.quarry.node.common"), Component.empty(), 0,
                        false));
    }

    private static List<MapThing> buildThings(CompoundTag state) {
        Minecraft minecraft = Minecraft.getInstance();
        String here = minecraft.level == null ? "" : minecraft.level.dimension().location().toString();
        List<MapThing> list = new ArrayList<>();
        for (Tag stored : state.getList(ModuleMenuStates.QUARRY_BLOCKS, Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) stored;
            list.add(new MapThing(key(block), Names.block(block.getString("block")), block.getInt("x"),
                    block.getInt("y"), block.getInt("z"), null, MapThing.PIN_ONLY,
                    block.getInt("left") > 0 ? RESTING : READY, here.equals(block.getString("dimension"))));
        }
        return list;
    }

    private static List<TypeTile> buildTypes(CompoundTag state) {
        List<TypeTile> list = new ArrayList<>();
        for (Tag stored : state.getList(ModuleMenuStates.QUARRY_RULES, Tag.TAG_COMPOUND)) {
            CompoundTag rule = (CompoundTag) stored;
            String block = rule.getString("block");
            int cooldown = rule.getInt("cooldown");
            Component plate = cooldown < 0 ? Component.translatable("studio.quarry.type.global")
                    : Component.translatable("studio.quarry.type.seconds", cooldown);
            list.add(new TypeTile(block, iconOf(block), Names.block(block), plate,
                    Component.literal("×" + Math.max(1, rule.getInt("multiplier")))));
        }
        return list;
    }

    CompoundTag block() {
        if (chosenBlock == null) return null;
        for (Tag stored : state().getList(ModuleMenuStates.QUARRY_BLOCKS, Tag.TAG_COMPOUND)) {
            if (key((CompoundTag) stored).equals(chosenBlock)) return (CompoundTag) stored;
        }
        return null;
    }

    CompoundTag rule() {
        if (chosenType == null) return null;
        for (Tag stored : state().getList(ModuleMenuStates.QUARRY_RULES, Tag.TAG_COMPOUND)) {
            if (((CompoundTag) stored).getString("block").equals(chosenType)) return (CompoundTag) stored;
        }
        return null;
    }

    String node() {
        return nodeKey;
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.QUARRY;
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
        grid.reset();
    }

    @Override
    protected boolean showsMap() {
        return MAP.equals(nodeKey);
    }

    @Override
    protected boolean movable(MapThing thing) {
        return false;
    }

    @Override
    protected boolean labelsAlways() {
        return false;
    }

    @Override
    protected List<MapThing> things() {
        ensureBuilt();
        return things;
    }

    @Override
    protected String chosenThing() {
        return chosenBlock;
    }

    @Override
    protected void chooseThing(String key) {
        chosenBlock = key;
    }

    @Override
    protected void moveThing(MapThing thing, int x, int z) {
    }

    @Override
    protected List<? extends StudioTile> tiles() {
        ensureBuilt();
        return TYPES.equals(nodeKey) ? types : List.of();
    }

    @Override
    protected int selectedTile() {
        List<? extends StudioTile> shown = tiles();
        for (int index = 0; index < shown.size(); index++) {
            if (((TypeTile) shown.get(index)).block().equals(chosenType)) return index;
        }
        return -1;
    }

    @Override
    protected void selectTile(int index) {
        chosenType = index < 0 ? null : types.get(index).block();
    }

    @Override
    protected Component stats() {
        if (state().isEmpty()) return Component.translatable("battlecraft.menu.waiting");
        return Component.translatable("studio.quarry.stats", state().getInt("blocks"), state().getInt("globalCooldown"));
    }

    @Override
    protected List<UiButton> headerButtons() {
        return List.of(addButton, meButton);
    }

    @Override
    protected Object inspectorSubject() {
        return nodeKey + "/" + (MAP.equals(nodeKey) ? chosenBlock : TYPES.equals(nodeKey) ? chosenType : "");
    }

    @Override
    protected void buildInspector(StudioStack stack, int x, int width) {
        rows.build(stack, x, width);
    }

    @Override
    protected int artHeight() {
        return MAP.equals(nodeKey) && block() != null || TYPES.equals(nodeKey) && rule() != null ? ART : 0;
    }

    @Override
    protected void renderArt(GuiGraphics graphics, float left, float top, float width, float appear,
                             int mouseX, int mouseY) {
        CompoundTag block = MAP.equals(nodeKey) ? block() : null;
        CompoundTag rule = TYPES.equals(nodeKey) ? rule() : null;
        if (block == null && rule == null) return;
        String id = block != null ? block.getString("block") : rule.getString("block");
        UiRender.textTrackedFit(graphics, this.font, Names.block(id), left + width / 2.0f, top, 14.0f, width, 0.95f,
                0.0f, UiTheme.alpha(UiAccent.text(), appear), false);
        Component detail = block != null ? blockState(block) : Component.literal(id);
        UiRender.textTrackedFit(graphics, this.font, detail, left + width / 2.0f, top + 16.0f, 10.0f, width,
                LINE_SCALE, 0.0f, UiTheme.alpha(UiAccent.textDim(), appear), false);
    }

    private static Component blockState(CompoundTag block) {
        int left = block.getInt("left");
        Component state = left > 0 ? Component.translatable("studio.quarry.resting", left)
                : Component.translatable("studio.quarry.ready");
        return Component.translatable("studio.points.detail", Component.literal(key(block)), state);
    }

    @Override
    protected List<StudioMenu.Action> thingActions(MapThing thing) {
        return List.of(StudioMenu.Action.of("studio.menu.tp", () -> teleport(thing)),
                StudioMenu.Action.of("studio.quarry.reset", () -> at(thing.key(), "cooldown reset")),
                StudioMenu.Action.careful("studio.quarry.remove", () -> removeBlock(thing.key())));
    }

    @Override
    protected List<StudioMenu.Action> groundActions(int x, int z) {
        return List.of(StudioMenu.Action.of("studio.map.me", this::centerOnPlayer));
    }

    @Override
    protected List<StudioMenu.Action> tileActions(int index) {
        String block = types.get(index).block();
        return List.of(StudioMenu.Action.of("studio.quarry.type.global_action",
                () -> now("type " + block + " cooldown -1")));
    }

    void teleport(MapThing thing) {
        send("tp @s " + thing.x() + " " + (thing.y() + 1) + " " + thing.z());
    }

    void teleportTo(CompoundTag block) {
        send("execute in " + block.getString("dimension") + " run tp @s " + block.getInt("x") + " "
                + (block.getInt("y") + 1) + " " + block.getInt("z"));
    }

    boolean isArmed(String key) {
        return armed(key);
    }

    boolean confirmed(String key) {
        return confirm(key);
    }

    void at(String position, String tail) {
        now("at " + position + " " + tail);
    }

    void removeBlock(String position) {
        at(position, "remove");
        chosenBlock = null;
        layout();
    }

    @Override
    protected Component emptyCanvas() {
        if (state().isEmpty()) return Component.translatable("battlecraft.menu.waiting");
        return Component.translatable(TYPES.equals(nodeKey) ? "quarrymod.menu.type.empty" : "studio.quarry.common_canvas");
    }

    @Override
    protected Component mapHint() {
        return Component.translatable("studio.quarry.map_hint");
    }

    @Override
    public void tick() {
        if (ticks % 20 == 0) MenuData.request(ModuleMenuStates.QUARRY);
        rows.request(ticks);
        super.tick();
    }

    void now(String tail) {
        send(COMMAND + tail);
    }

    void later(String key, String tail) {
        commitLater(key, COMMAND + tail);
    }
}
