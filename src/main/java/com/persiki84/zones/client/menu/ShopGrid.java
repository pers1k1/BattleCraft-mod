package com.persiki84.zones.client.menu;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.zones.shop.ShopEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;

public final class ShopGrid {
    public static final int TILE_SIZE = 68;
    public static final int TILE_GAP = 6;

    private static final float TILE_RADIUS = 7.0f;
    private static final float TILE_LEAD = 3.0f;
    private static final float HOVER_SPEED = 16.0f;
    private static final int SOLD_OUT_SCRIM = 0xB2101014;
    private static final int ITEM_TOP = 13;
    private static final int SECTION_TOP = 4;
    private static final int NAME_TOP = 34;
    private static final int PRICE_HEIGHT = 12;
    private static final int PRICE_BOTTOM = 5;
    private static final float ROW_HEIGHT = 11.0f;
    private static final float NAME_SCALE = 0.75f;
    private static final float NAME_SCALE_TIGHT = 0.62f;
    private static final float NAME_INSET = 6.0f;
    private static final float NAME_LEADING = 0.5f;
    private static final int NAME_LINES = 2;
    private static final float PRICE_SCALE = 0.75f;
    private static final float SECTION_SCALE = 0.6f;
    private static final float CHOSEN_RIM = 1.2f;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final float APPEAR_MS = 200.0f;
    private static final float APPEAR_LIFT = 5.0f;
    private static final float STAGGER_MS = 16.0f;
    private static final float GLIDE_SPEED = 19.0f;
    private static final float HOVER_LIFT = 1.1f;
    private static final float PRESS_MS = 150.0f;
    private static final float PRESS_SQUASH = 0.024f;
    private static final float CHOICE_SPEED = 13.0f;

    private static final float SCROLL_LANE = 12.0f;
    private static final float SCROLL_WIDTH = 5.0f;
    private static final float SCROLL_GRAB = 4.0f;
    private static final float MIN_THUMB = 18.0f;
    private static final float THUMB_SPEED = 17.0f;

    private static final float SWITCH_SPEED = 11.0f;
    private static final float SWITCH_SHIFT = 34.0f;

    private static final float SLIDE_SPEED = 17.0f;
    private static final float CARRY_LIFT = 1.12f;
    private static final float CARRY_ALPHA = 0.92f;
    private static final float LIFT_SPEED = 13.0f;
    private static final float LIFT_DONE = 0.01f;

    private final Map<String, Slide> slides = new HashMap<>();
    private final List<Smooth> hover = new ArrayList<>();
    private final Smooth glide = new Smooth(0.0f, GLIDE_SPEED);
    private final Smooth thumb = new Smooth(0.0f, THUMB_SPEED);
    private final Smooth choice = new Smooth(1.0f, CHOICE_SPEED);
    private final Smooth switching = new Smooth(1.0f, SWITCH_SPEED);
    private int switchFrom;

    private long shownAt = System.currentTimeMillis();
    private long pressedAt;
    private int pressedIndex = -1;
    private String chosen;
    private String leaving;
    private float left;
    private float top;
    private float width;
    private float height;
    private int scroll;
    private boolean scrolling;
    private boolean dragging;
    private final Smooth carryLift = new Smooth(0.0f, LIFT_SPEED);
    private String carried;
    private String released;
    private int carriedFrom = -1;
    private int carriedTo = -1;
    private float carryX;
    private float carryY;

    public void place(float gridLeft, float gridTop, float gridWidth, float gridHeight) {
        left = gridLeft;
        top = gridTop;
        width = gridWidth;
        height = gridHeight;
    }

    // WHY: ряду нужен зазор только между соседями, поэтому свободного места хватает на ряд
    // WHY: больше, чем давало деление с зазором на каждый: последний ряд рисовался, но не ловил
    // WHY: щелчок, потому что ёмкость сетки его не знала
    public int columns() {
        return fits(width - UiMetrics.PAD_WIDE - SCROLL_LANE);
    }

    public int rows() {
        return fits(height - UiMetrics.PAD - TILE_LEAD);
    }

    private static int fits(float room) {
        return Math.max(1, (int) ((room + TILE_GAP) / (TILE_SIZE + TILE_GAP)));
    }

    public int capacity() {
        return columns() * rows();
    }

    public void scrollBy(int step, int total) {
        moveTo(scroll + step * columns(), total);
    }

    // WHY: прокрутка идёт рядами: остаток последнего ряда сдвигал сетку на неполный ряд, и
    // WHY: товары переезжали в чужие столбцы, а бегунок не доходил до низа
    private void moveTo(int target, int total) {
        int next = clamp(target, total);
        if (next == scroll) return;

        glide.snap(capped(glide.get() + (next - scroll) / (float) columns() * (TILE_SIZE + TILE_GAP)));
        scroll = next;
    }

    private Slide slideOf(String id, float x, float y) {
        Slide known = slides.get(id);
        if (known != null) return known;

        Slide fresh = new Slide(x, y);
        slides.put(id, fresh);
        return fresh;
    }

    private static final class Slide {
        private final Smooth x;
        private final Smooth y;

        private Slide(float atX, float atY) {
            this.x = new Smooth(atX, SLIDE_SPEED);
            this.y = new Smooth(atY, SLIDE_SPEED);
        }
    }

    public void reset() {
        reset(0);
    }

    public void reset(int direction) {
        scroll = 0;
        glide.snap(0.0f);
        switchFrom = direction;
        switching.snap(0.0f);
        refresh();
    }

    public void refresh() {
        shownAt = System.currentTimeMillis();
    }

    // WHY: смена раздела и отдела меняла содержимое сетки в один кадр, и переход читался
    // WHY: подменой картинки: плитки въезжают со стороны выбранного раздела
    private float switchShift() {
        if (switchFrom == 0) return 0.0f;
        return switchFrom * SWITCH_SHIFT * (1.0f - UiAnim.easeOut(switching.get()));
    }

    private float appear(int index) {
        return UiAnim.easeOut((System.currentTimeMillis() - shownAt - Math.max(0, index) * STAGGER_MS) / APPEAR_MS);
    }

    public ShopView.Found pick(double mouseX, double mouseY, List<ShopView.Found> entries) {
        scrolling = entries.size() > capacity();
        int shown = Math.min(capacity(), Math.max(0, entries.size() - scroll));

        if (mouseY < bandTop() || mouseY >= bandBottom()) return null;

        for (int index = 0; index < shown; index++) {
            float x = tileX(index) + switchShift();
            float y = tileY(index);
            if (mouseX >= x && mouseX < x + TILE_SIZE && mouseY >= y && mouseY < y + TILE_SIZE) {
                pressedIndex = index;
                pressedAt = System.currentTimeMillis();
                return entries.get(index + scroll);
            }
        }
        return null;
    }

    public boolean grabScrollbar(double mouseX, double mouseY, int total) {
        if (total <= capacity()) return false;
        if (mouseX < trackX() - SCROLL_GRAB || mouseX > trackX() + SCROLL_WIDTH + SCROLL_GRAB) return false;
        if (mouseY < trackTop() || mouseY > trackTop() + trackHeight()) return false;

        dragging = true;
        dragScrollbar(mouseY, total);
        return true;
    }

    public void dragScrollbar(double mouseY, int total) {
        float span = trackHeight() - thumbHeight(total);
        if (span <= 0.0f) return;

        float fraction = UiAnim.clamp01((float) (mouseY - trackTop() - thumbHeight(total) / 2.0f) / span);
        int lastRow = Math.max(0, rowsOf(total) - rows());
        moveTo(Math.round(fraction * lastRow) * columns(), total);
    }

    public boolean dragging() {
        return dragging;
    }

    // WHY: порядок правится тем же жестом, каким его читают - плитку берут и кладут, а соседи
    // WHY: расступаются на глазах: список со стрелками показывает не витрину, а таблицу
    // WHY: плитка поднимается и опускается движением, а не подменой размера: щипок без подъёма
    // WHY: не читается как «взял», а мгновенная посадка - как «положил»
    public void carry(ShopView.Found found, int index, double mouseX, double mouseY) {
        carried = found.entry().id();
        released = null;
        carriedFrom = index;
        carriedTo = index;
        carryX = (float) mouseX;
        carryY = (float) mouseY;
        carryLift.snap(0.0f);
    }

    public void carryTo(double mouseX, double mouseY, int total) {
        carryX = (float) mouseX;
        carryY = (float) mouseY;
        carriedTo = slotAt(mouseX, mouseY, total);
    }

    // WHY: отпущенная плитка обязана поехать от места, где её отпустили, а не от прежнего слота:
    // WHY: её место всё время переноса держится на курсоре, поэтому посадка идёт оттуда
    public int dropDelta() {
        int delta = carriedTo - carriedFrom;
        released = carried;
        carried = null;
        carriedFrom = -1;
        carriedTo = -1;
        return delta;
    }

    private int slotAt(double mouseX, double mouseY, int total) {
        int column = (int) Math.floor((mouseX - tileX(0) + TILE_GAP / 2.0f) / (TILE_SIZE + TILE_GAP));
        int row = (int) Math.floor((mouseY - tileY(0) + TILE_GAP / 2.0f) / (TILE_SIZE + TILE_GAP));
        int slot = Math.max(0, Math.min(columns() - 1, column)) + Math.max(0, row) * columns() + scroll;
        return Math.max(0, Math.min(total - 1, slot));
    }

    // WHY: место плитки при переносе считается по её положению в переставленном списке, а не по
    // WHY: индексу в данных: данные меняет только сервер, и до его ответа сетка обязана жить сама
    private int slotOf(int index) {
        if (carried == null || carriedFrom < 0 || carriedTo == carriedFrom) return index;
        if (index == carriedFrom) return carriedTo;
        if (carriedFrom < carriedTo) return index > carriedFrom && index <= carriedTo ? index - 1 : index;
        return index >= carriedTo && index < carriedFrom ? index + 1 : index;
    }

    public void endDrag() {
        dragging = false;
    }

    public void render(GuiGraphics graphics, List<ShopView.Found> entries, String chosenId,
                       boolean showSection, int mouseX, int mouseY) {
        scroll = clamp(scroll, entries.size());
        scrolling = entries.size() > capacity();
        int shown = Math.min(capacity(), Math.max(0, entries.size() - scroll));
        keepHoverStates(shown);
        trackChoice(chosenId);

        float offset = glide.to(0.0f, UiFrame.delta());
        switching.to(1.0f, UiFrame.delta());
        UiRender.clip(graphics, left, bandTop(), width, bandBottom() - bandTop());
        try {
            paintTiles(graphics, entries, shown, showSection, mouseX, mouseY, offset);
            renderCarried(graphics, entries, showSection);
            graphics.flush();
        } finally {
            graphics.disableScissor();
        }
        renderScrollbar(graphics, entries.size());
    }

    // WHY: соседние ряды плиток кладутся за кромку сетки и режутся ею, иначе ряд с прокруткой
    // WHY: исчезает целиком в один кадр, а входящий возникает сразу целым
    private void paintTiles(GuiGraphics graphics, List<ShopView.Found> entries, int shown,
                            boolean showSection, int mouseX, int mouseY, float offset) {
        for (int index = -columns(); index < shown + columns(); index++) {
            int place = index + scroll;
            if (place < 0 || place >= entries.size()) continue;

            renderTile(graphics, entries.get(place), index, showSection, mouseX, mouseY, offset);
        }
    }

    private void trackChoice(String chosenId) {
        if (!Objects.equals(chosenId, chosen)) {
            leaving = chosen;
            chosen = chosenId;
            choice.snap(0.0f);
        }
        choice.to(1.0f, UiFrame.delta());
    }

    private float chosenAmount(ShopEntry entry) {
        if (entry.id().equals(chosen)) return choice.get();
        return entry.id().equals(leaving) ? 1.0f - choice.get() : 0.0f;
    }

    private void keepHoverStates(int shown) {
        while (hover.size() < shown + columns() * 2) {
            hover.add(new Smooth(0.0f, HOVER_SPEED));
        }
    }

    private static float capped(float offset) {
        float limit = TILE_SIZE + TILE_GAP;
        return Math.max(-limit, Math.min(limit, offset));
    }

    // WHY: полоса отсечения равна площади рядов, а не всей панели: лишний ряд рисовался по её
    // WHY: остатку целиком и выглядел живым, хотя ёмкость сетки его не держала и щелчок не ловил
    private float bandTop() {
        return trackTop() - TILE_LEAD;
    }

    private float bandBottom() {
        return trackTop() + trackHeight() + TILE_LEAD;
    }

    private void renderTile(GuiGraphics graphics, ShopView.Found found, int index,
                            boolean showSection, int mouseX, int mouseY, float offset) {
        if (found.entry().id().equals(carried)) return;

        float appear = appear(index);
        Slide slide = settled(found, slotOf(index + scroll) - scroll, offset);
        float x = slide.x.get() + switchShift();
        float rawY = slide.y.get() + (1.0f - appear) * APPEAR_LIFT;
        if (found.entry().id().equals(released)) {
            settle(graphics, found, x, rawY, showSection);
            return;
        }

        boolean hovered = mouseX >= x && mouseX < x + TILE_SIZE && mouseY >= rawY && mouseY < rawY + TILE_SIZE
                && mouseY >= bandTop() && mouseY < bandBottom();
        float focus = hover.get(index + columns()).to(hovered ? 1.0f : 0.0f, UiFrame.delta());
        float y = rawY - focus * HOVER_LIFT;

        graphics.pose().pushPose();
        squash(graphics, index, x, y);
        paintTile(graphics, found, x, y, appear, focus, showSection);
        graphics.pose().popPose();
    }

    // WHY: отпущенная плитка садится тем же движением, каким поднималась: подъём отдаётся обратно,
    // WHY: пока она едет к своему месту, и только потом она снова становится обычной
    private void settle(GuiGraphics graphics, ShopView.Found found, float x, float y, boolean showSection) {
        float lift = carryLift.to(0.0f, UiFrame.delta());
        paintLifted(graphics, found, x, y, lift, showSection);
        if (lift <= LIFT_DONE) released = null;
    }

    // WHY: место плитки едет сглаживанием, а не прыгает: соседи расступаются перед переносимой,
    // WHY: и без хода это читается как мигание сетки
    private Slide settled(ShopView.Found found, int slot, float offset) {
        Slide slide = slideOf(found.entry().id(), tileX(slot), tileY(slot) + offset);
        float delta = UiFrame.delta();
        slide.x.to(tileX(slot), delta);
        slide.y.to(tileY(slot) + offset, delta);
        return slide;
    }

    private void renderCarried(GuiGraphics graphics, List<ShopView.Found> entries, boolean showSection) {
        if (carried == null) return;

        for (ShopView.Found found : entries) {
            if (!found.entry().id().equals(carried)) continue;

            float x = carryX - TILE_SIZE / 2.0f;
            float y = carryY - TILE_SIZE / 2.0f;
            Slide slide = slideOf(carried, x, y);
            slide.x.snap(x);
            slide.y.snap(y);

            float lift = carryLift.to(1.0f, UiFrame.delta());
            paintLifted(graphics, found, x, y, lift, showSection);
            return;
        }
    }

    private void paintLifted(GuiGraphics graphics, ShopView.Found found, float x, float y,
                             float lift, boolean showSection) {
        float scale = 1.0f + (CARRY_LIFT - 1.0f) * lift;
        graphics.pose().pushPose();
        graphics.pose().translate(x + TILE_SIZE / 2.0f, y + TILE_SIZE / 2.0f, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-x - TILE_SIZE / 2.0f, -y - TILE_SIZE / 2.0f, 0.0f);
        try {
            paintTile(graphics, found, x, y, CARRY_ALPHA, lift, showSection);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void squash(GuiGraphics graphics, int index, float x, float y) {
        if (index != pressedIndex || carried != null || released != null) return;

        float age = (System.currentTimeMillis() - pressedAt) / PRESS_MS;
        if (age >= 1.0f) {
            pressedIndex = -1;
            return;
        }

        float scale = 1.0f - PRESS_SQUASH * (1.0f - UiAnim.easeOut(age));
        graphics.pose().translate(x + TILE_SIZE / 2.0f, y + TILE_SIZE / 2.0f, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-x - TILE_SIZE / 2.0f, -y - TILE_SIZE / 2.0f, 0.0f);
    }

    private void paintTile(GuiGraphics graphics, ShopView.Found found, float x, float y,
                           float appear, float focus, boolean showSection) {
        ShopEntry entry = found.entry();
        float taken = chosenAmount(entry);
        UiGlass.panel(graphics, x, y, TILE_SIZE, TILE_SIZE, TILE_RADIUS, appear,
                0.35f * taken + focus * 0.5f);
        if (taken > 0.01f) {
            UiRender.rim(graphics, x, y, TILE_SIZE, TILE_SIZE, TILE_RADIUS, CHOSEN_RIM,
                    UiTheme.alpha(UiAccent.color(), 0.75f * taken));
        }

        renderTileBody(graphics, found, x, y, showSection);
        if (entry.soldOut()) {
            UiRender.panel(graphics, x, y, TILE_SIZE, TILE_SIZE, TILE_RADIUS, SOLD_OUT_SCRIM);
        }
        renderPrice(graphics, entry, x, y, taken, focus);
    }

    // WHY: renderItem принимает целые координаты, а плитка едет субпиксельно - значок прыгал по
    // WHY: целым пикселям и отставал от карточки рывками. Место задаёт матрица, рисуется в ноль
    private void paintItem(GuiGraphics graphics, ShopEntry entry, float x, float y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        try {
            graphics.renderItem(entry.stack(), 0, 0);
            graphics.renderItemDecorations(font(), entry.stack(), 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderTileBody(GuiGraphics graphics, ShopView.Found found, float x, float y, boolean showSection) {
        ShopEntry entry = found.entry();
        paintItem(graphics, entry, x + TILE_SIZE / 2.0f - 8.0f, y + ITEM_TOP);

        float textWidth = TILE_SIZE - UiMetrics.GAP * 2.0f;
        if (showSection) {
            UiRender.textTrackedFit(graphics, font(), Component.literal(found.sectionTitle()),
                    x + TILE_SIZE / 2.0f, y + SECTION_TOP, ROW_HEIGHT * 0.8f, textWidth,
                    SECTION_SCALE, 0.4f, UiAccent.text(), false);
        }

        renderName(graphics, entry.stack().getHoverName(), x, y);
    }

    // WHY: длинное имя шло одной строкой во всю плитку, упиралось в её кромку с обеих сторон и
    // WHY: читалось выехавшим за карточку; сначала пробуем уложить его в две строки помельче
    private void renderName(GuiGraphics graphics, Component name, float x, float y) {
        float room = TILE_SIZE - NAME_INSET * 2.0f;
        float centerX = x + TILE_SIZE / 2.0f;
        if (UiRender.measure(graphics, font(), name, NAME_SCALE) <= room) {
            UiRender.textTrackedFit(graphics, font(), name, centerX, y + NAME_TOP, ROW_HEIGHT, room,
                    NAME_SCALE, 0.0f, UiAccent.text(), false);
            return;
        }

        List<FormattedCharSequence> lines = UiRender.split(graphics, font(), name, NAME_SCALE_TIGHT,
                (int) (room / NAME_SCALE_TIGHT));
        if (lines.isEmpty() || lines.size() > NAME_LINES) {
            UiRender.textTrackedFit(graphics, font(), name, centerX, y + NAME_TOP, ROW_HEIGHT, room,
                    NAME_SCALE, 0.0f, UiAccent.text(), false);
            return;
        }

        float step = font().lineHeight * NAME_SCALE_TIGHT + NAME_LEADING;
        float top = y + NAME_TOP + ROW_HEIGHT / 2.0f - step * lines.size() / 2.0f;
        for (FormattedCharSequence line : lines) {
            UiRender.textTrackedFit(graphics, font(), Component.literal(UiRender.flatten(line)),
                    centerX, top, step, room, NAME_SCALE_TIGHT, 0.0f, UiAccent.text(), false);
            top += step;
        }
    }

    private void renderPrice(GuiGraphics graphics, ShopEntry entry, float x, float y, float taken, float focus) {
        float plateWidth = TILE_SIZE - UiMetrics.GAP * 2.0f;
        float plateX = x + UiMetrics.GAP;
        float plateY = y + TILE_SIZE - PRICE_HEIGHT - PRICE_BOTTOM;

        UiGlass.sunken(graphics, plateX, plateY, plateWidth, PRICE_HEIGHT,
                UiMetrics.radius(PRICE_HEIGHT), 0.9f);
        UiRender.textTrackedFit(graphics, font(), footer(entry), x + TILE_SIZE / 2.0f, plateY,
                PRICE_HEIGHT, plateWidth - UiMetrics.GAP, PRICE_SCALE, 0.0f,
                priceColor(entry, taken, focus), false);
    }

    private static int priceColor(ShopEntry entry, float taken, float focus) {
        if (entry.soldOut()) return UiPalette.alert();
        return UiTheme.mix(UiAccent.text(), UiAccent.color(), Math.max(taken, focus));
    }

    private static Component footer(ShopEntry entry) {
        if (!entry.soldOut()) return Component.translatable("zones.shop.price", entry.price());

        int waiting = entry.remainingSeconds(System.currentTimeMillis());
        if (waiting <= 0) return Component.translatable("zones.shop.sold_out");
        return Component.translatable("zones.shop.restock.short", clock(waiting));
    }

    private void renderScrollbar(GuiGraphics graphics, int total) {
        if (total <= capacity()) return;

        float trackHeight = trackHeight();
        float barHeight = thumbHeight(total);
        float span = trackHeight - barHeight;
        int lastRow = Math.max(1, rowsOf(total) - rows());
        float progress = thumb.to(UiAnim.clamp01(scroll / (float) columns() / lastRow), UiFrame.delta());
        float centerX = trackX() + SCROLL_WIDTH / 2.0f;

        UiGlass.sunken(graphics, trackX(), trackTop(), SCROLL_WIDTH, trackHeight,
                SCROLL_WIDTH / 2.0f, dragging ? 1.0f : 0.75f);
        UiGlass.inner(graphics, trackX(), trackTop() + span * progress, SCROLL_WIDTH, barHeight,
                SCROLL_WIDTH / 2.0f, 1.0f, dragging ? 0.9f : 0.55f);
        UiRender.panel(graphics, centerX - SCROLL_WIDTH / 2.0f + 1.0f, trackTop() + span * progress + 2.0f,
                SCROLL_WIDTH - 2.0f, barHeight - 4.0f, (SCROLL_WIDTH - 2.0f) / 2.0f,
                UiTheme.alpha(UiAccent.dim(), dragging ? 0.95f : 0.8f));
    }

    private float trackX() {
        return left + width - SCROLL_LANE / 2.0f - SCROLL_WIDTH / 2.0f;
    }

    private float trackHeight() {
        return rows() * (TILE_SIZE + TILE_GAP) - TILE_GAP;
    }

    private float thumbHeight(int total) {
        float visible = rows() / (float) Math.max(1, rowsOf(total));
        return Math.max(MIN_THUMB, trackHeight() * visible);
    }

    private int rowsOf(int total) {
        return (total + columns() - 1) / columns();
    }

    private float rowInset() {
        float used = columns() * (TILE_SIZE + TILE_GAP) - TILE_GAP;
        float free = width - used - (scrolling ? SCROLL_LANE : 0.0f);
        return Math.max(UiMetrics.GAP, free / 2.0f);
    }

    private float tileX(int index) {
        return left + rowInset() + Math.floorMod(index, columns()) * (TILE_SIZE + TILE_GAP);
    }

    // WHY: верхний ряд отступает от кромки отсечения: подъём на наведении и кант выбранной плитки
    // WHY: выходят за её границу, и прижатый к кромке ряд оставался без верхнего канта
    private float tileY(int index) {
        return trackTop() + Math.floorDiv(index, columns()) * (TILE_SIZE + TILE_GAP);
    }

    private float trackTop() {
        return top + TILE_LEAD;
    }

    private int clamp(int value, int total) {
        int limit = Math.max(0, rowsOf(total) - rows()) * columns();
        return Math.max(0, Math.min(value / columns() * columns(), limit));
    }

    private static String clock(int seconds) {
        int rest = seconds % SECONDS_PER_MINUTE;
        return seconds / SECONDS_PER_MINUTE + ":" + (rest < 10 ? "0" : "") + rest;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
