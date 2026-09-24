package com.persiki84.shared.client.menu.gunsmith;

import com.persiki84.shared.client.menu.GlassScreen;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTitle;
import com.persiki84.shared.gunsmith.GunSlot;
import com.persiki84.shared.gunsmith.GunSmith;
import com.persiki84.shared.gunsmith.GunStat;
import com.persiki84.shared.menu.MenuKind;
import com.persiki84.zones.client.menu.ItemTurntable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// WHY: обвесы правятся глядя на оружие: слот это карточка с поставленным обвесом, варианты это
// WHY: плитки с иконками, а что даёт обвес, видно по наведению. Список строк с перебором стрелками
// WHY: заставлял помнить, что где стоит, и читать номер уровня вместо самой вещи
public final class GunsmithScreen extends GlassScreen {
    private static final int LIST_WIDTH = 150;
    private static final int MODEL_WIDTH = 230;
    private static final int SLOTS_WIDTH = 290;
    private static final int GAP = 8;
    private static final int HEADER = 60;
    private static final int PANEL_LIMIT = 320;
    private static final int TITLE_TOP = 12;
    private static final int ROW = 20;
    private static final float RADIUS = 9.0f;
    private static final float FIT_MARGIN = 10.0f;
    private static final float MODEL_HEIGHT = 120.0f;
    private static final float STAT_SCALE = 0.7f;
    private static final int REFRESH_TICKS = 20;

    private final GunBench bench;
    private final Screen parent;
    private final GunSlotBoard board = new GunSlotBoard();
    private final ItemTurntable turntable = new ItemTurntable();
    private final List<UiButton> gunButtons = new ArrayList<>();
    private final Map<String, Wanted> wanted = new HashMap<>();
    private int picked;
    private int ticks;
    private int shownGuns = -1;
    private ItemStack describedGun = ItemStack.EMPTY;
    private List<GunSlot> slots = List.of();
    private List<GunStat> stats = List.of();

    private record Wanted(String option, long at) {}

    public GunsmithScreen(GunBench bench, int picked, Screen parent) {
        super(Component.translatable("gunsmith.title"));
        this.bench = bench;
        this.picked = Math.max(0, picked);
        this.parent = parent;
    }

    public static void open(GunBench bench, int picked, Screen parent) {
        Minecraft.getInstance().setScreen(new GunsmithScreen(bench, picked, parent));
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
        return LIST_WIDTH + MODEL_WIDTH + SLOTS_WIDTH + GAP * 2;
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

    private int modelLeft() {
        return left() + LIST_WIDTH + GAP;
    }

    private int slotsLeft() {
        return modelLeft() + MODEL_WIDTH + GAP;
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

    private ItemStack gun() {
        List<ItemStack> guns = bench.guns();
        if (guns.isEmpty()) return ItemStack.EMPTY;
        picked = Math.floorMod(picked, guns.size());
        return guns.get(picked);
    }

    @Override
    protected void init() {
        layout();
    }

    // WHY: кнопки оружия живут между пересборками: доступность, наведение и нажатие у кнопки
    // WHY: анимированы, и новая кнопка на каждое обновление снимка обрывала бы их на полпути
    private void layout() {
        clearWidgets();
        List<ItemStack> guns = bench.guns();
        shownGuns = guns.size();
        while (gunButtons.size() < guns.size()) {
            int index = gunButtons.size();
            gunButtons.add(new UiButton(0, 0, LIST_WIDTH - (int) UiMetrics.GAP * 2, ROW - 2, Component.empty(),
                    pressed -> pick(index)).lit());
        }
        int y = top() + (int) UiMetrics.PAD;
        for (int index = 0; index < guns.size(); index++) {
            UiButton button = gunButtons.get(index);
            button.setPosition(left() + (int) UiMetrics.GAP, y);
            button.setMessage(guns.get(index).getHoverName());
            button.active = index != picked;
            addRenderableWidget(button);
            y += ROW;
        }
    }

    private void pick(int index) {
        picked = index;
        turntable.rest();
        board.choose(-1);
        wanted.clear();
        layout();
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks % REFRESH_TICKS == 0) bench.refresh();
        if (bench.guns().size() != shownGuns) layout();
    }

    private void describe(ItemStack gun) {
        if (gun == describedGun) return;
        describedGun = gun;
        slots = gun.isEmpty() ? List.of() : GunSmith.slots(gun);
        stats = gun.isEmpty() ? List.of() : GunSmith.stats(gun);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiTitle.render(graphics, this.font, Component.translatable("gunsmith.title.of", bench.title()),
                this.width / 2.0f, TITLE_TOP, 1.0f, 0.0f, UiAccent.text());
        int top = top();
        int height = panelHeight();
        UiGlass.window(graphics, left(), top, LIST_WIDTH, height, RADIUS, 1.0f);
        UiGlass.window(graphics, modelLeft(), top, MODEL_WIDTH, height, RADIUS, 1.0f);
        UiGlass.window(graphics, slotsLeft(), top, SLOTS_WIDTH, height, RADIUS, 1.0f);
        UiGlass.layer(graphics);

        ItemStack gun = gun();
        describe(gun);
        if (gun.isEmpty()) {
            UiRender.textCentered(graphics, this.font, Component.translatable("gunsmith.none"),
                    this.width / 2.0f, top + height / 2.0f, 0.8f, UiAccent.textDim(), false);
        } else {
            renderModel(graphics, gun);
            board.place(slotsLeft() + UiMetrics.PAD, top + UiMetrics.PAD, SLOTS_WIDTH - UiMetrics.PAD * 2.0f,
                    height - UiMetrics.PAD * 2.0f);
            board.render(graphics, slots, this::installed, mouseX, mouseY);
        }
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        renderGunIcons(graphics);
        MenuFeedback.render(graphics, this.width / 2.0f, top + height + GAP);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderModel(GuiGraphics graphics, ItemStack gun) {
        float left = modelLeft() + UiMetrics.PAD_WIDE;
        float width = MODEL_WIDTH - UiMetrics.PAD_WIDE * 2.0f;
        float y = top() + UiMetrics.PAD_WIDE;
        turntable.render(graphics, gun, left, y, width, MODEL_HEIGHT);
        y += MODEL_HEIGHT + UiMetrics.GAP;
        UiRender.textTrackedFit(graphics, this.font, gun.getHoverName(), left + width / 2.0f, y, 12.0f, width,
                0.85f, 0.0f, UiAccent.text(), false);
        y += 16.0f;
        float bottom = top() + panelHeight() - UiMetrics.PAD;
        for (GunStat stat : stats) {
            if (y + 10.0f > bottom) break;
            UiRender.textScaled(graphics, this.font, Component.translatable(stat.label()), left, y, STAT_SCALE,
                    UiAccent.textDim(), false);
            UiRender.textRight(graphics, this.font, Component.literal(stat.value()), left + width, y, STAT_SCALE,
                    UiAccent.text(), false);
            y += 10.0f;
        }
    }

    private void renderGunIcons(GuiGraphics graphics) {
        List<ItemStack> guns = bench.guns();
        for (int index = 0; index < guns.size() && index < gunButtons.size(); index++) {
            UiButton button = gunButtons.get(index);
            graphics.pose().pushPose();
            graphics.pose().translate(button.getX() + 2.0f, button.getY() + 1.0f, 0.0f);
            graphics.renderItem(guns.get(index), 0, 0);
            graphics.pose().popPose();
        }
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        GunSlotBoard.Hover hover = board.hovered(mouseX, mouseY, slots);
        if (hover == null || hover.option().isEmpty()) return;

        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.literal(hover.label()).getVisualOrderText());
        for (String note : GunSmith.notes(hover.option())) {
            lines.add(Component.literal(note).getVisualOrderText());
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 400.0f);
        graphics.renderTooltip(this.font, lines, mouseX, mouseY);
        graphics.pose().popPose();
    }

    // WHY: поставленный обвес показывается сразу, до ответа сервера, и держится до прихода того
    // WHY: же значения в данных или две секунды: иначе плитка мигала бы старым выбором
    private String installed(GunSlot slot) {
        Wanted want = wanted.get(slot.id());
        if (want == null) return slot.installed();
        if (want.option().equals(slot.installed()) || System.currentTimeMillis() - want.at() > 2000L) {
            wanted.remove(slot.id());
            return slot.installed();
        }
        return want.option();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (leaving()) return true;

        double x = localX(mouseX);
        double y = localY(mouseY);
        if (button == 0 && turntable.over(x, y)) {
            turntable.beginDrag();
            return true;
        }
        if (button == 0 && board.over(x, y)) {
            clickBoard(x, y);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void clickBoard(double x, double y) {
        int slotIndex = board.slotAt(x, y, slots.size());
        if (slotIndex >= 0) {
            board.choose(slotIndex == board.chosen() ? -1 : slotIndex);
            return;
        }
        GunSlotBoard.Hover hover = board.hovered(x, y, slots);
        if (hover == null) return;

        GunSlot slot = slots.get(board.chosen());
        wanted.put(slot.id(), new Wanted(hover.option(), System.currentTimeMillis()));
        if (hover.option().isEmpty()) {
            bench.detach(picked, slot.id());
        } else {
            bench.attach(picked, hover.option());
        }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        double x = localX(mouseX);
        double y = localY(mouseY);
        if (turntable.over(x, y)) {
            turntable.magnify(amount);
            return true;
        }
        if (board.over(x, y)) board.scroll(amount > 0 ? -1 : 1);
        return true;
    }
}
