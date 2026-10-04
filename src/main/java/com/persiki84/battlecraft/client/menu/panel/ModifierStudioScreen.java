package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.Names;
import com.persiki84.shared.client.menu.MenuData;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

// WHY: модификаторы правятся на предмете, который ими обвешан: предметы лежат плитками, первая -
// WHY: то, что в руке, справа всё, что на выбранном уже висит, и формы нового эффекта и атрибута.
// WHY: Новый вид предмета берётся с полки, а не выбором строки и отдельным окном
public final class ModifierStudioScreen extends StudioScreen {
    static final String HAND = "hand";
    static final String ITEMS = "items";
    static final String COMMON = "common";

    private static final int ART = 104;
    private static final float TURNTABLE_HEIGHT = 66.0f;
    private static final float LINE_SCALE = 0.72f;

    private final ModifierInspector rows = new ModifierInspector(this);
    private final ItemTurntable turntable = new ItemTurntable();
    private final UiButton addButton;
    private String nodeKey = ITEMS;
    private String chosen = HAND;
    private String fresh;
    private boolean adding;
    private Object builtFor;
    private List<ModTile> tiles = List.of();

    record ModTile(String item, ItemStack icon, Component name, Component corner, Component plate, boolean faded)
            implements StudioTile {
    }

    public ModifierStudioScreen() {
        this(null);
    }

    public ModifierStudioScreen(Screen parent) {
        super(Component.translatable("itemmodifiers.menu.title"), parent);
        addButton = new UiButton(0, 0, HEADER_BUTTON, HEADER_ROW, Component.empty(), pressed -> switchShelf())
                .hint("studio.modifiers.add.hint");
    }

    static CompoundTag state() {
        return MenuData.state(ModuleMenuStates.MODIFIERS);
    }

    static ItemStack held() {
        return Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
    }

    static ItemStack stackOf(String item) {
        ResourceLocation key = ResourceLocation.tryParse(item);
        Item found = key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
        return found == Items.AIR ? ItemStack.EMPTY : new ItemStack(found);
    }

    static CompoundTag tracked(String item) {
        for (Tag stored : state().getList(ModuleMenuStates.TRACKED_ITEMS, Tag.TAG_COMPOUND)) {
            if (((CompoundTag) stored).getString("item").equals(item)) return (CompoundTag) stored;
        }
        return new CompoundTag();
    }

    private void ensureBuilt() {
        Object stamp = stamp();
        if (stamp.equals(builtFor)) return;
        builtFor = stamp;
        CompoundTag state = state();
        List<ModTile> built = new ArrayList<>();
        int inHand = ModifierInspector.handCount();
        built.add(new ModTile(HAND, held(), Component.translatable("studio.modifiers.hand"),
                Component.translatable("studio.modifiers.hand_corner"),
                Component.translatable("itemmodifiers.menu.item.count", inHand), inHand == 0));
        for (Tag stored : state.getList(ModuleMenuStates.TRACKED_ITEMS, Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) stored;
            String id = item.getString("item");
            int count = item.getList("effects", Tag.TAG_STRING).size() + item.getList("attributes", Tag.TAG_STRING).size();
            built.add(new ModTile(id, stackOf(id), Names.item(id), Component.empty(),
                    Component.translatable("itemmodifiers.menu.item.count", count), false));
            if (id.equals(fresh)) fresh = null;
        }
        if (fresh != null) {
            built.add(new ModTile(fresh, stackOf(fresh), Names.item(fresh), Component.translatable("studio.modifiers.new"),
                    Component.translatable("itemmodifiers.menu.item.count", 0), true));
        }
        tiles = built;
    }

    String chosen() {
        return chosen;
    }

    boolean handChosen() {
        return HAND.equals(chosen);
    }

    String node() {
        return nodeKey;
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.MODIFIERS;
    }

    // WHY: плитка руки зависит от того, что игрок держит, а снимок сервера об этом не знает:
    // WHY: штамп собран из ссылки на снимок и подписи стака в руке
    @Override
    protected Object stamp() {
        ItemStack hand = held();
        return System.identityHashCode(state()) + ":" + BuiltInRegistries.ITEM.getKey(hand.getItem()) + ":"
                + (hand.hasTag() ? hand.getTag().hashCode() : 0);
    }

    @Override
    protected List<StudioNav.Node> nodes() {
        ensureBuilt();
        return List.of(StudioNav.Node.item(ITEMS, Component.translatable("itemmodifiers.menu.tab.items"),
                        Component.literal(String.valueOf(tiles.size() - 1)), 0, false),
                StudioNav.Node.item(COMMON, Component.translatable("itemmodifiers.menu.tab.main"), Component.empty(), 0,
                        false));
    }

    @Override
    protected String selectedNode() {
        return nodeKey;
    }

    @Override
    protected void selectNode(StudioNav.Node node) {
        nodeKey = node.key();
        adding = false;
        grid.reset();
    }

    @Override
    protected List<? extends StudioTile> tiles() {
        ensureBuilt();
        return ITEMS.equals(nodeKey) ? tiles : List.of();
    }

    @Override
    protected int selectedTile() {
        List<? extends StudioTile> shown = tiles();
        for (int index = 0; index < shown.size(); index++) {
            if (((ModTile) shown.get(index)).item().equals(chosen)) return index;
        }
        return -1;
    }

    @Override
    protected void selectTile(int index) {
        adding = false;
        turntable.rest();
        chosen = index < 0 ? HAND : tiles.get(index).item();
    }

    @Override
    protected Component stats() {
        if (state().isEmpty()) return Component.translatable("battlecraft.menu.waiting");
        return Component.translatable(state().getBoolean("modEnabled") ? "studio.modifiers.stats.on"
                : "studio.modifiers.stats.off", tiles().isEmpty() ? 0 : tiles.size() - 1);
    }

    @Override
    protected List<UiButton> headerButtons() {
        addButton.setMessage(Component.translatable(adding ? "studio.mode.done" : "studio.modifiers.add"));
        return List.of(addButton);
    }

    private void switchShelf() {
        flushCommits();
        nodeKey = ITEMS;
        adding = !adding;
        layout();
    }

    @Override
    protected boolean shelfOpen() {
        return adding;
    }

    @Override
    protected void addPick(ItemShelf.Pick pick, int slot) {
        String item = BuiltInRegistries.ITEM.getKey(pick.stack().getItem()).toString();
        adding = false;
        chosen = item;
        if (tracked(item).isEmpty()) fresh = item;
        builtFor = null;
        layout();
    }

    @Override
    protected Component shelfHint() {
        return Component.translatable("studio.modifiers.shelf.hint");
    }

    @Override
    protected Object inspectorSubject() {
        return adding ? "shelf" : nodeKey + "/" + chosen;
    }

    @Override
    protected void buildInspector(StudioStack stack, int x, int width) {
        if (adding) return;
        rows.build(stack, x, width);
    }

    @Override
    protected int artHeight() {
        return adding || !ITEMS.equals(nodeKey) ? 0 : ART;
    }

    @Override
    protected void renderArt(GuiGraphics graphics, float left, float top, float width, float appear,
                             int mouseX, int mouseY) {
        if (adding || !ITEMS.equals(nodeKey)) return;
        ItemStack stack = handChosen() ? held() : stackOf(chosen);
        turntable.render(graphics, stack, left, top, width, TURNTABLE_HEIGHT);
        Component name = handChosen() ? Component.translatable("studio.modifiers.hand") : Names.item(chosen);
        float y = top + TURNTABLE_HEIGHT + 4.0f;
        UiRender.textTrackedFit(graphics, this.font, name, left + width / 2.0f, y, 12.0f, width, 0.85f, 0.0f,
                UiTheme.alpha(UiAccent.text(), appear), false);
        Component detail = Component.translatable(handChosen() ? "studio.modifiers.hand_about" : "studio.modifiers.kind_about");
        UiRender.textTrackedFit(graphics, this.font, detail, left + width / 2.0f, y + 15.0f, 10.0f, width, LINE_SCALE,
                0.0f, UiTheme.alpha(UiAccent.textDim(), appear), false);
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
    protected List<StudioMenu.Action> tileActions(int index) {
        String item = tiles.get(index).item();
        if (HAND.equals(item)) {
            return List.of(StudioMenu.Action.careful("itemmodifiers.menu.hand_clear", () -> send("ie hand clear")));
        }
        return List.of(StudioMenu.Action.careful("itemmodifiers.menu.item.clear", () -> send("ie item " + item + " clear")));
    }

    @Override
    protected List<StudioMenu.Action> canvasActions() {
        return List.of(StudioMenu.Action.of("studio.modifiers.add", this::switchShelf));
    }

    @Override
    protected Component emptyCanvas() {
        if (state().isEmpty()) return Component.translatable("battlecraft.menu.waiting");
        return Component.translatable("studio.modifiers.common_canvas");
    }

    @Override
    protected boolean submit() {
        return rows.submit() || super.submit();
    }

    @Override
    public void tick() {
        if (ticks % 20 == 0) MenuData.request(ModuleMenuStates.MODIFIERS);
        super.tick();
    }

    void now(String command) {
        send(command);
    }
}
