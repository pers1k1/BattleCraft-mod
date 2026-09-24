package com.persiki84.shared.client.menu.pick;

import com.persiki84.shared.client.menu.GlassScreen;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.SearchField;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTitle;
import com.persiki84.shared.menu.MenuKind;
import com.persiki84.zones.client.menu.ItemTurntable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

// WHY: предмет для награды, дохода, цены или товара выбирается глазами - из инвентаря или всего
// WHY: реестра с поиском и с моделью рядом, - а не держится в руке перед нажатием строки меню
public final class ItemPickerScreen extends GlassScreen {
    private static final int SHELF_WIDTH = 216;
    private static final int SIDE_WIDTH = 220;
    private static final int GAP = 8;
    private static final int HEADER = 60;
    private static final int PANEL_LIMIT = 300;
    private static final int TITLE_TOP = 12;
    private static final int CONTROL = 16;
    private static final int ROW = 22;
    private static final float RADIUS = 9.0f;
    private static final float FIT_MARGIN = 10.0f;
    private static final float MODEL_HEIGHT = 96.0f;
    private static final long DOUBLE_CLICK_MS = 300L;

    public record Choice(int slot, ItemStack stack, int amount) {
        public boolean fromInventory() {
            return slot >= 0;
        }

        public String itemId() {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }
    }

    public record Options(boolean inventory, boolean registry, Component amountLabel, int amountMin,
                          int amountMax, int amountStart) {
        public static Options itemOnly() {
            return new Options(true, true, null, 1, 1, 1);
        }

        public static Options counted(Component label, int minimum, int maximum, int start) {
            return new Options(true, true, label, minimum, maximum, Math.max(minimum, Math.min(maximum, start)));
        }

        public Options inventoryOnly() {
            return new Options(true, false, amountLabel, amountMin, amountMax, amountStart);
        }
    }

    private final ItemShelf shelf = new ItemShelf();
    private final ItemTurntable turntable = new ItemTurntable();
    private final Screen parent;
    private final Options options;
    private final Consumer<Choice> done;
    private SearchField search;
    private UiButton inventoryButton;
    private UiButton everyButton;
    private UiButton pickButton;
    private NumberRow amountRow;
    private ItemShelf.Pick chosen;
    private int amount;
    private long clickedAt;

    private ItemPickerScreen(Component title, Screen parent, Options options, Consumer<Choice> done) {
        super(title);
        this.parent = parent;
        this.options = options;
        this.done = done;
        this.amount = options.amountStart();
        shelf.showInventory(options.inventory());
    }

    public static void open(Component title, Screen parent, Options options, Consumer<Choice> done) {
        Minecraft.getInstance().setScreen(new ItemPickerScreen(title, parent, options, done));
    }

    @Override
    public MenuKind presence() {
        return MenuKind.ADMIN;
    }

    @Override
    protected void closing() {
        if (parent == null) {
            super.closing();
            return;
        }
        if (parent instanceof GlassScreen glass) glass.reenter();
        Minecraft.getInstance().setScreen(parent);
    }

    private int contentWidth() {
        return SHELF_WIDTH + SIDE_WIDTH + GAP;
    }

    private int left() {
        return (this.width - contentWidth()) / 2;
    }

    private int top() {
        return Math.max(HEADER, (this.height - panelHeight()) / 2);
    }

    private int panelHeight() {
        return Math.min(this.height - HEADER - GAP * 2, PANEL_LIMIT);
    }

    private int sideLeft() {
        return left() + SHELF_WIDTH + GAP;
    }

    @Override
    protected float revealTop() {
        return top();
    }

    @Override
    protected float revealSpan() {
        return panelHeight();
    }

    @Override
    protected Area frameArea() {
        float top = TITLE_TOP - 8.0f;
        return new Area(left() - 8.0f, top, contentWidth() + 16.0f, top() + panelHeight() + 8.0f - top);
    }

    @Override
    protected float contentScale() {
        float needed = contentWidth() + FIT_MARGIN * 2.0f;
        return needed <= this.width ? 1.0f : this.width / needed;
    }

    @Override
    protected void init() {
        if (search == null) createWidgets();
        layout();
    }

    private void createWidgets() {
        search = new SearchField(Component.translatable("battlecraft.picker.search"), shelf::search);
        int half = (SHELF_WIDTH - (int) UiMetrics.PAD * 2 - 4) / 2;
        inventoryButton = new UiButton(0, 0, half, CONTROL, Component.translatable("battlecraft.picker.inventory"),
                pressed -> showSource(true)).lit();
        everyButton = new UiButton(0, 0, half, CONTROL, Component.translatable("battlecraft.picker.all"),
                pressed -> showSource(false)).lit().hint("battlecraft.picker.all.hint");
        int wide = SIDE_WIDTH - (int) UiMetrics.PAD * 2;
        pickButton = new UiButton(0, 0, wide, ROW - 2, Component.translatable("battlecraft.picker.pick"), pressed -> confirm());
        if (options.amountLabel() != null) {
            amountRow = new NumberRow(0, 0, wide, ROW, options.amountLabel(), () -> amount, value -> amount = value,
                    options.amountMin(), options.amountMax(), 1);
        }
    }

    private void layout() {
        boolean typing = search.typing();
        clearWidgets();
        int left = left() + (int) UiMetrics.PAD;
        int top = top() + (int) UiMetrics.PAD;
        if (options.inventory() && options.registry()) {
            inventoryButton.setPosition(left, top);
            inventoryButton.active = !shelf.showsInventory();
            everyButton.setPosition(left + inventoryButton.getWidth() + 4, top);
            everyButton.active = shelf.showsInventory();
            addRenderableWidget(inventoryButton);
            addRenderableWidget(everyButton);
        }
        if (!shelf.showsInventory()) {
            search.place(left, top + CONTROL + 6, SHELF_WIDTH - (int) UiMetrics.PAD * 2);
            addRenderableWidget(search.box());
        }
        placeSide();
        if (typing && !shelf.showsInventory()) setFocused(search.box());
    }

    private void placeSide() {
        int left = sideLeft() + (int) UiMetrics.PAD;
        int bottom = top() + panelHeight() - (int) UiMetrics.PAD;
        pickButton.setPosition(left, bottom - ROW + 2);
        pickButton.active = chosen != null;
        addRenderableWidget(pickButton);
        if (amountRow == null) return;

        amountRow.setX(left);
        amountRow.setY(bottom - ROW * 2 - 4);
        addRenderableWidget(amountRow);
    }

    private void showSource(boolean inventory) {
        shelf.showInventory(inventory);
        if (!inventory) shelf.search(search.query());
        layout();
    }

    private int shelfTop() {
        int top = top() + (int) UiMetrics.PAD;
        boolean tabs = options.inventory() && options.registry();
        int below = tabs ? CONTROL + 6 : 0;
        return top + below + (shelf.showsInventory() ? 0 : CONTROL + 6);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiTitle.render(graphics, this.font, getTitle(), this.width / 2.0f, TITLE_TOP, 1.0f, 0.0f, UiAccent.text());
        int top = top();
        int height = panelHeight();
        UiGlass.window(graphics, left(), top, SHELF_WIDTH, height, RADIUS, 1.0f);
        UiGlass.window(graphics, sideLeft(), top, SIDE_WIDTH, height, RADIUS, 1.0f);
        UiGlass.layer(graphics);
        if (!shelf.showsInventory()) search.render(graphics);
        shelf.place(left() + UiMetrics.PAD, shelfTop(), SHELF_WIDTH - UiMetrics.PAD * 2.0f,
                top + height - shelfTop() - UiMetrics.PAD);
        shelf.render(graphics, mouseX, mouseY);
        renderChoice(graphics);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        MenuFeedback.render(graphics, this.width / 2.0f, top + height + GAP);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderChoice(GuiGraphics graphics) {
        float left = sideLeft() + UiMetrics.PAD_WIDE;
        float width = SIDE_WIDTH - UiMetrics.PAD_WIDE * 2.0f;
        float y = top() + UiMetrics.PAD_WIDE;
        if (chosen == null) {
            UiRender.textCentered(graphics, this.font, Component.translatable("battlecraft.picker.none"),
                    left + width / 2.0f, y + MODEL_HEIGHT / 2.0f, 0.72f, UiAccent.textDim(), false);
            return;
        }
        turntable.render(graphics, chosen.stack(), left, y, width, MODEL_HEIGHT);
        UiRender.textTrackedFit(graphics, this.font, chosen.stack().getHoverName(), left + width / 2.0f,
                y + MODEL_HEIGHT + UiMetrics.GAP, 12.0f, width, 0.85f, 0.0f, UiAccent.text(), false);
        Component source = chosen.fromInventory()
                ? Component.translatable("battlecraft.picker.from_inventory")
                : Component.literal(BuiltInRegistries.ITEM.getKey(chosen.stack().getItem()).toString());
        UiRender.textTrackedFit(graphics, this.font, source, left + width / 2.0f, y + MODEL_HEIGHT + 20.0f, 10.0f,
                width, 0.7f, 0.0f, UiAccent.textDim(), false);
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!shelf.over(mouseX, mouseY)) return;
        ItemStack stack = shelf.hovered(mouseX, mouseY);
        if (stack.isEmpty()) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 400.0f);
        graphics.renderTooltip(this.font, stack, mouseX, mouseY);
        graphics.pose().popPose();
    }

    // WHY: двойной щелчок по предмету выбирает его сразу, одиночный только показывает рядом
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (leaving()) return true;

        double x = localX(mouseX);
        double y = localY(mouseY);
        if (button == 0 && chosen != null && turntable.over(x, y)) {
            turntable.beginDrag();
            return true;
        }
        if (button != 0 || !shelf.over(x, y)) return super.mouseClicked(mouseX, mouseY, button);

        ItemShelf.Pick pick = shelf.pick(x, y);
        if (pick == null) return true;
        boolean again = chosen != null && chosen.stack() == pick.stack()
                && System.currentTimeMillis() - clickedAt < DOUBLE_CLICK_MS;
        clickedAt = System.currentTimeMillis();
        choose(pick);
        if (again) confirm();
        return true;
    }

    private void choose(ItemShelf.Pick pick) {
        if (chosen == null || chosen.stack() != pick.stack()) turntable.rest();
        chosen = pick;
        shelf.mark(pick.stack());
        if (amountRow != null && pick.fromInventory() && options.amountLabel() != null) {
            amount = Math.max(options.amountMin(), Math.min(options.amountMax(), pick.stack().getCount()));
        }
        layout();
    }

    private void confirm() {
        if (chosen == null) return;
        Choice choice = new Choice(chosen.slot(), chosen.stack().copy(), amount);
        onClose();
        done.accept(choice);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (turntable.dragging()) {
            turntable.drag(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        turntable.endDrag();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amountScrolled) {
        double x = localX(mouseX);
        double y = localY(mouseY);
        if (shelf.over(x, y)) {
            shelf.scrollBy(amountScrolled > 0 ? -1 : 1);
        } else if (turntable.over(x, y)) {
            turntable.magnify(amountScrolled);
        }
        return true;
    }
}
