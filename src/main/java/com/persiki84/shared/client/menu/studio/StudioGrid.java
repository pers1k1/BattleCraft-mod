package com.persiki84.shared.client.menu.studio;

import com.persiki84.shared.client.menu.MenuLayer;
import com.persiki84.shared.client.menu.TickerLines;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

public final class StudioGrid {
    public static final int COLUMNS = 4;

    private static final float BASE_TILE = 58.0f;
    private static final float BASE_GAP = 6.0f;
    private static final float RADIUS = 7.0f;
    private static final float LEAD = 3.0f;
    private static final float ICON_SCALE = 1.5f;
    private static final float ICON_TOP = 7.0f;
    private static final float NAME_TOP = 33.0f;
    private static final float NAME_SCALE = 0.6f;
    private static final float TEXT_INSET = 4.0f;
    private static final float PLATE_HEIGHT = 11.0f;
    private static final float PLATE_BOTTOM = 4.0f;
    private static final float PLATE_INSET = UiMetrics.GAP;
    private static final float PLATE_SCALE = 0.62f;
    private static final float BADGE_TOP = 3.0f;
    private static final float BADGE_HEIGHT = 8.0f;
    private static final float BADGE_SCALE = 0.55f;
    private static final float BAR_HEIGHT = 1.5f;
    private static final float BAR_INSET = 2.0f;
    private static final float HOVER_RISE = 1.0f;
    private static final float HOVER_SPEED = 16.0f;
    private static final float SLIDE_SPEED = 17.0f;
    private static final float CHOICE_SPEED = 13.0f;
    private static final float APPEAR_MS = 200.0f;
    private static final float STAGGER_MS = 14.0f;
    private static final float APPEAR_LIFT = 5.0f;
    private static final float CARRY_LIFT = 1.12f;
    private static final float CARRY_ALPHA = 0.92f;
    private static final float LIFT_SPEED = 13.0f;
    private static final float BORN_MS = 900.0f;
    private static final float BORN_SPREAD = 4.0f;
    private static final float GAP_SPEED = 14.0f;
    private static final int FADED_SCRIM = 0x9A0C0C12;
    private static final float MARK_SPEED = 15.0f;
    private static final float BAND_SPEED = 18.0f;
    private static final float CHECK_RADIUS = 5.0f;
    private static final float CHECK_INSET = 7.0f;
    private static final float GROUP_DIM = 0.6f;
    private static final float STACK_STEP = 3.5f;
    private static final float BADGE_RADIUS = 7.0f;
    private static final float COUNT_INSET = 2.0f;
    private static final float COUNT_SCALE = 0.75f;
    private static final float COUNT_RISE = 3.5f;
    private static final float LAND_SPEED = 20.0f;
    private static final float DEPART_MS = 260.0f;
    private static final float DEPART_SCALE = 0.35f;
    private static final float HUSH_ALPHA = 0.55f;
    private static final float HUSH_DROP = 3.0f;
    private static final float LAND_REST = 0.5f;
    private static final float GRAB_SPEED = 16.0f;
    private static final float LIFT_DEPTH = 50.0f;
    private static final float OVER_ICON = 200.0f;

    private record Departure(StudioTile tile, float fromX, float fromY, float toX, float toY, int group, long at) {}

    private final List<Smooth> hover = new ArrayList<>();
    private final List<Smooth> slideX = new ArrayList<>();
    private final List<Smooth> slideY = new ArrayList<>();
    private final Smooth choice = new Smooth(1.0f, CHOICE_SPEED);
    private final Smooth carryLift = new Smooth(0.0f, LIFT_SPEED);
    private final Smooth gapGlow = new Smooth(0.0f, GAP_SPEED);
    private final List<Smooth> marks = new ArrayList<>();
    private final Smooth bandShown = new Smooth(0.0f, BAND_SPEED);
    private final Smooth land = new Smooth(0.0f, LAND_SPEED);
    private final BitSet vanished = new BitSet();
    private final BitSet crossing = new BitSet();
    private final BitSet painted = new BitSet();
    private float[] shownX = new float[0];
    private float[] shownY = new float[0];
    private boolean drifting;
    private int landing = -1;
    private Departure departure;
    private BitSet marked = new BitSet();
    private int groupCount;
    private String countText = "0";
    private boolean banding;
    private float bandX0;
    private float bandY0;
    private float bandX1;
    private float bandY1;
    private long shownAt = System.currentTimeMillis();
    private long bornAt;
    private int born = -1;
    private float left;
    private float top;
    private float width;
    private float height;
    private float tileSize = BASE_TILE;
    private float gapSize = BASE_GAP;
    private float unit = 1.0f;
    private int scroll;
    private int chosen = -1;
    private int carriedFrom = -1;
    private int carriedTo = -1;
    private int gap = -1;
    private boolean holding;
    private float carryX;
    private float carryY;
    private float grabX;
    private float grabY;
    private final TickerLines cornerLines = new TickerLines(0.0f);
    private final TickerLines alertLines = new TickerLines(1.0f);
    private final TickerLines plateLines = new TickerLines(0.5f);
    private IntFunction<String> keyOf = index -> null;
    private Object[] keyedTiles = new Object[0];
    private Object[] tileKeys = new Object[0];

    // WHY: число на плитке (цена, шанс, остаток) перекатывается по цифрам и держится ключом вещи,
    // WHY: который отдаёт экран: снимок пересобирает плитки, и по номеру ход уезжал бы к соседке
    public void keys(IntFunction<String> source) {
        keyOf = source;
        keyedTiles = new Object[0];
    }

    private void refreshKeys(List<? extends StudioTile> tiles) {
        if (sameTiles(tiles)) return;

        int total = tiles.size();
        keyedTiles = new Object[total];
        tileKeys = new Object[total];
        Map<String, Integer> seen = new HashMap<>();
        for (int index = 0; index < total; index++) {
            keyedTiles[index] = tiles.get(index);
            String key = keyOf.apply(index);
            if (key == null) {
                String name = tiles.get(index).name().getString();
                int occurrence = seen.merge(name, 1, Integer::sum);
                key = "#" + name + "#" + occurrence;
            }
            tileKeys[index] = key;
        }
    }

    private boolean sameTiles(List<? extends StudioTile> tiles) {
        if (tiles.size() != keyedTiles.length) return false;
        for (int index = 0; index < keyedTiles.length; index++) {
            if (keyedTiles[index] != tiles.get(index)) return false;
        }
        return true;
    }

    private Object keyAt(int index) {
        return index >= 0 && index < tileKeys.length ? tileKeys[index] : null;
    }

    public void place(float gridLeft, float gridTop, float gridWidth, float gridHeight) {
        left = gridLeft;
        top = gridTop;
        width = gridWidth;
        height = gridHeight;
        fit(gridWidth - UiMetrics.PAD * 2.0f);
    }

    // WHY: ряд всегда занимает холст от поля до поля: плитка считается от ширины, а не стоит
    // WHY: константой, иначе справа оставалась полоса пустоты. Зазор и всё нутро плитки растут той же долей
    private void fit(float room) {
        if (room <= 0.0f) return;

        tileSize = UiMetrics.snap(room / (COLUMNS + (COLUMNS - 1) * BASE_GAP / BASE_TILE));
        gapSize = Math.max(0.0f, (room - tileSize * COLUMNS) / (COLUMNS - 1));
        unit = tileSize / BASE_TILE;
    }

    public void reset() {
        scroll = 0;
        shownAt = System.currentTimeMillis();
        settle();
        landing = -1;
        slideX.clear();
        slideY.clear();
    }

    // WHY: перенос закончен или отменён: соседи и сама плитка доезжают до своих мест тем же
    // WHY: сглаживанием, что и шли. Раньше места защёлкивались, и отпущенная плитка прыгала в слот
    public void settle() {
        carriedFrom = -1;
        carriedTo = -1;
        holding = false;
        gap = -1;
        vanished.clear();
    }

    // WHY: сервер принял перенос, и номера плиток теперь в новом порядке. Сглаживание переезжает за
    // WHY: своими плитками, а не остаётся на номерах: иначе в момент ответа плитка, ещё не доехавшая
    // WHY: до места, и её соседи щёлкали бы в новые места
    public void commit() {
        if (carriedFrom >= 0 && carriedTo >= 0 && carriedTo != carriedFrom && carriedFrom < slideX.size()) {
            follow(slideX);
            follow(slideY);
            follow(hover);
            follow(marks);
            if (landing == carriedFrom) landing = carriedTo;
        }
        settle();
    }

    private <T> void follow(List<T> places) {
        if (carriedFrom >= places.size()) return;
        T moved = places.remove(carriedFrom);
        places.add(Math.min(carriedTo, places.size()), moved);
    }

    public void flash(int index) {
        born = index;
        bornAt = System.currentTimeMillis();
    }

    private float step() {
        return tileSize + gapSize;
    }

    private float lead() {
        return LEAD * unit;
    }

    private int rows() {
        return Math.max(1, (int) ((height - lead() + gapSize) / step()));
    }

    // WHY: ряды режутся по своей полосе, а не по окну: ряд, видный наполовину, рисовался, но не
    // WHY: ловил щелчок, потому что ёмкость сетки его не знала. Запас сверху и снизу держит кант и подъём
    private float bandBottom() {
        return Math.min(top + height, top + lead() * 2.0f + rows() * step() - gapSize);
    }

    private boolean inBand(double mouseX, double mouseY) {
        return over(mouseX, mouseY) && mouseY < bandBottom();
    }

    public void scrollBy(int step, int total) {
        int rowsTotal = (total + COLUMNS - 1) / COLUMNS;
        int lastRow = Math.max(0, rowsTotal - rows());
        scroll = Math.max(0, Math.min(lastRow, scroll / COLUMNS + step)) * COLUMNS;
    }

    public void reveal(int index, int total) {
        if (index < 0) return;
        int row = index / COLUMNS;
        int first = scroll / COLUMNS;
        if (row < first) scrollBy(row - first, total);
        if (row >= first + rows()) scrollBy(row - first - rows() + 1, total);
    }

    public int pick(double mouseX, double mouseY, int total) {
        if (!inBand(mouseX, mouseY)) return -1;
        for (int index = scroll; index < Math.min(total, scroll + COLUMNS * rows()); index++) {
            if (inside(mouseX, mouseY, tileX(index), tileY(index))) return index;
        }
        return -1;
    }

    private boolean inside(double mouseX, double mouseY, float x, float y) {
        return mouseX >= x && mouseX < x + tileSize && mouseY >= y && mouseY < y + tileSize;
    }

    public boolean over(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    // WHY: взятая плитка не прыгает центром под курсор, а подъезжает к нему от своего места:
    // WHY: разница сходит на нет за десятые доли секунды, пока плитка уже идёт за рукой
    public void carry(int index, double mouseX, double mouseY) {
        carriedFrom = index;
        carriedTo = index;
        holding = true;
        carryX = (float) mouseX;
        carryY = (float) mouseY;
        boolean placed = index >= 0 && index < slideX.size();
        grabX = placed ? slideX.get(index).get() - (carryX - tileSize / 2.0f) : 0.0f;
        grabY = placed ? slideY.get(index).get() - (carryY - tileSize / 2.0f) : 0.0f;
        carryLift.snap(0.0f);
    }

    public boolean carrying() {
        return holding;
    }

    public void carryTo(double mouseX, double mouseY, int total, boolean reorder) {
        carryX = (float) mouseX;
        carryY = (float) mouseY;
        boolean placing = reorder && over(mouseX, mouseY);
        carriedTo = placing ? Math.min(total - 1, slotAt(mouseX, mouseY, total)) : carriedFrom;
    }

    // WHY: отпущенная плитка едет в своё место от курсора и садится: подъём сходит на нет по пути, и всё
    // WHY: это время она рисуется поверх окон, иначе брошенная за окном плитка пропадала бы до слота.
    // WHY: Порядок соседей держится до ответа сервера, иначе сетка на полсекунды вернулась бы назад
    public int[] drop() {
        int[] move = {carriedFrom, carriedTo};
        holding = false;
        boolean seen = carriedFrom >= 0 && carriedFrom < slideX.size() && visible(slotOf(carriedFrom));
        landing = seen ? carriedFrom : -1;
        land.snap(carryLift.get());
        carryLift.snap(0.0f);
        if (carriedFrom >= 0 && carriedFrom < slideX.size()) {
            slideX.get(carriedFrom).snap(carryX - tileSize / 2.0f + grabX);
            slideY.get(carriedFrom).snap(carryY - tileSize / 2.0f + grabY);
        }
        return move;
    }

    // WHY: брошенная в раздел плитка улетает в его строку и тает там, а её место пустеет сразу: до ответа
    // WHY: сервера она стояла бы в старой витрине. Сборка сетки по ответу возвращает всё как есть
    public void depart(StudioTile tile, List<Integer> indices, float toX, float toY) {
        departure = new Departure(tile, carryX + grabX, carryY + grabY, toX, toY, indices.size(),
                System.currentTimeMillis());
        landing = -1;
        for (int index : indices) {
            if (index >= 0) vanished.set(index);
        }
    }

    public boolean lifting() {
        return carrying() || landing >= 0 || departure != null;
    }

    private boolean visible(int slot) {
        float y = tileY(slot);
        return y >= top && y + tileSize <= bandBottom();
    }

    // WHY: предмет, который несут с полки, раздвигает соседей там, куда ляжет: без просвета игрок
    // WHY: не видит, встанет ли он перед плиткой под курсором или после неё
    public void openGap(double mouseX, double mouseY, int total) {
        gap = over(mouseX, mouseY) ? slotAt(mouseX, mouseY, total) : -1;
    }

    public void closeGap() {
        gap = -1;
    }

    public int slotAt(double mouseX, double mouseY, int total) {
        int column = (int) Math.floor((mouseX - tileX(0) + gapSize / 2.0f) / step());
        int row = (int) Math.floor((mouseY - top - lead() + gapSize / 2.0f) / step());
        int slot = Math.max(0, Math.min(COLUMNS - 1, column)) + Math.max(0, row) * COLUMNS + scroll;
        return Math.max(0, Math.min(total, slot));
    }

    private int slotOf(int index) {
        int slot = carriedSlot(index);
        return gap >= 0 && slot >= gap ? slot + 1 : slot;
    }

    private int carriedSlot(int index) {
        if (carriedFrom < 0 || carriedTo == carriedFrom) return index;
        if (index == carriedFrom) return carriedTo;
        if (carriedFrom < carriedTo) return index > carriedFrom && index <= carriedTo ? index - 1 : index;
        return index >= carriedTo && index < carriedFrom ? index + 1 : index;
    }

    public void mark(BitSet flags) {
        marked = flags;
    }

    public void group(int count) {
        if (count != groupCount) countText = String.valueOf(count);
        groupCount = count;
    }

    public void band(double x0, double y0, double x1, double y1) {
        banding = true;
        bandX0 = (float) Math.min(x0, x1);
        bandY0 = (float) Math.min(y0, y1);
        bandX1 = (float) Math.max(x0, x1);
        bandY1 = (float) Math.max(y0, y1);
    }

    public void endBand() {
        banding = false;
    }

    // WHY: рамка отбирает плитки по их месту на экране, поэтому считаются только видимые ряды:
    // WHY: невидимую плитку рамкой не накрыть, как бы далеко её ни растянули
    public void collect(int total, BitSet out) {
        for (int index = scroll; index < Math.min(total, scroll + COLUMNS * rows()); index++) {
            float x = tileX(index);
            float y = tileY(index);
            if (x < bandX1 && x + tileSize > bandX0 && y < bandY1 && y + tileSize > bandY0) out.set(index);
        }
    }

    public void render(GuiGraphics graphics, List<? extends StudioTile> tiles, int selected, int mouseX, int mouseY) {
        int total = tiles.size();
        refreshKeys(tiles);
        grow(total);
        follow(selected);
        travel(total);
        sortCrossing(total);
        clipBand(graphics);
        try {
            paintGap(graphics);
            for (int index = 0; index < total; index++) {
                if (!skipped(index) && !crossing.get(index)) paintSlot(graphics, tiles.get(index), index, mouseX, mouseY);
            }
            graphics.flush();
            if (!crossing.isEmpty()) MenuLayer.flatten(graphics);
        } finally {
            graphics.disableScissor();
        }
        if (!crossing.isEmpty()) paintCrossing(graphics, tiles, total, mouseX, mouseY);
        paintBand(graphics);
    }

    private void clipBand(GuiGraphics graphics) {
        UiRender.clip(graphics, left, top, width, bandBottom() - top);
    }

    private boolean skipped(int index) {
        return raised(index) || vanished.get(index);
    }

    private boolean raised(int index) {
        return carrying() && index == carriedFrom || index == landing;
    }

    // WHY: место плитки в кадре считается один раз до отрисовки: сглаживание шагает при каждом вызове,
    // WHY: а по этим местам ещё решается, какие плитки перекрывают соседей
    private void travel(int total) {
        if (shownX.length < total) {
            shownX = new float[Math.max(total, shownX.length * 2)];
            shownY = new float[shownX.length];
        }
        float delta = UiFrame.delta();
        drifting = false;
        for (int index = 0; index < total; index++) {
            if (skipped(index)) continue;
            int slot = slotOf(index);
            shownX[index] = slideX.get(index).to(tileX(slot), delta);
            shownY[index] = slideY.get(index).to(tileY(slot), delta);
            drifting |= drift(index) > gapSize * 0.5f;
        }
    }

    private float drift(int index) {
        int slot = slotOf(index);
        return Math.abs(shownX[index] - tileX(slot)) + Math.abs(shownY[index] - tileY(slot));
    }

    // WHY: плитка, которая на переезде заходит на соседку (перенос через край ряда), рисуется вторым
    // WHY: проходом поверх снимка с уже нарисованными значками: иначе значок соседки торчал бы сквозь
    // WHY: её стекло по глубине и не преломлялся. Из пары наверх идёт та, что дальше от своего места
    private void sortCrossing(int total) {
        crossing.clear();
        if (!drifting) return;
        for (int index = 0; index < total; index++) {
            if (!skipped(index) && shownInBand(index) && coversCalmer(index, total)) crossing.set(index);
        }
    }

    private boolean coversCalmer(int index, int total) {
        float own = drift(index);
        for (int other = 0; other < total; other++) {
            if (other == index || skipped(other) || !shownInBand(other) || !touching(index, other)) continue;
            float theirs = drift(other);
            if (theirs < own || theirs == own && other < index) return true;
        }
        return false;
    }

    private boolean touching(int first, int second) {
        return Math.abs(shownX[first] - shownX[second]) < tileSize && Math.abs(shownY[first] - shownY[second]) < tileSize;
    }

    private boolean shownInBand(int index) {
        return shownY[index] + tileSize >= top && shownY[index] <= bandBottom();
    }

    // WHY: подложка переснимается один раз на весь второй проход, а не на каждую плитку: пересъёмок за
    // WHY: кадр не больше шести, и их ждут ещё меню, подсказка и окна палитры. Ножницы снимаются на
    // WHY: время пересъёмки, иначе пирамида снималась бы по полосе сетки
    private void paintCrossing(GuiGraphics graphics, List<? extends StudioTile> tiles, int total, int mouseX, int mouseY) {
        UiGlass.layer(graphics);
        clipBand(graphics);
        try {
            painted.clear();
            for (int next = calmestCrossing(total); next >= 0; next = calmestCrossing(total)) {
                painted.set(next);
                paintSlot(graphics, tiles.get(next), next, mouseX, mouseY);
            }
            graphics.flush();
        } finally {
            graphics.disableScissor();
        }
    }

    private int calmestCrossing(int total) {
        int found = -1;
        for (int index = crossing.nextSetBit(0); index >= 0 && index < total; index = crossing.nextSetBit(index + 1)) {
            if (!painted.get(index) && (found < 0 || drift(index) < drift(found))) found = index;
        }
        return found;
    }

    // WHY: поднятая плитка рисуется вне ножниц сетки, поверх всех окон: её можно унести хоть за край
    // WHY: окна, и она не пропадает из виду. Экран перед этим переснимает интерфейс и сбрасывает глубину,
    // WHY: поэтому её стекло преломляет плитки и значки под собой, а не один мир
    public void renderLifted(GuiGraphics graphics, List<? extends StudioTile> tiles) {
        refreshKeys(tiles);
        grow(tiles.size());
        paintDeparture(graphics);
        if (landing >= 0 && landing < tiles.size()) paintLanding(graphics, tiles.get(landing));
        if (carrying() && carriedFrom < tiles.size()) paintCarried(graphics, tiles.get(carriedFrom));
    }

    private void paintLanding(GuiGraphics graphics, StudioTile tile) {
        float delta = UiFrame.delta();
        int index = landing;
        int slot = slotOf(index);
        float x = slideX.get(index).to(tileX(slot), delta);
        float y = slideY.get(index).to(tileY(slot), delta);
        float lift = land.to(0.0f, delta);
        float mark = marks.get(index).to(marked.get(index) ? 1.0f : 0.0f, delta);
        if (lift < 0.01f && Math.abs(x - tileX(slot)) < LAND_REST && Math.abs(y - tileY(slot)) < LAND_REST) landing = -1;
        paintRaised(graphics, tile, index, x, y, lift, 1.0f, mark, false);
    }

    private void paintDeparture(GuiGraphics graphics) {
        if (departure == null) return;
        float age = (System.currentTimeMillis() - departure.at()) / DEPART_MS;
        if (age >= 1.0f) {
            departure = null;
            return;
        }
        float travel = UiAnim.easeOut(age);
        float half = tileSize / 2.0f;
        float x = departure.fromX() + (departure.toX() - departure.fromX()) * travel - half;
        float y = departure.fromY() + (departure.toY() - departure.fromY()) * travel - half;
        float shrink = CARRY_LIFT + (DEPART_SCALE - CARRY_LIFT) * travel;
        float fade = 1.0f - UiAnim.smoothstep(0.45f, 1.0f, age);
        UiRestFrame.push(graphics, x + half, y + half, shrink, shrink, 0.0f, 0.0f);
        graphics.pose().translate(0.0f, 0.0f, LIFT_DEPTH);
        try {
            if (departure.group() > 1) paintStack(graphics, x, y, departure.group(), fade);
            paintTile(graphics, departure.tile(), -1, x, y, CARRY_ALPHA * fade, 1.0f - travel, 1.0f);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private void follow(int selected) {
        if (selected != chosen) {
            chosen = selected;
            choice.snap(0.0f);
        }
        choice.to(1.0f, UiFrame.delta());
    }

    private void grow(int total) {
        while (hover.size() < total) {
            hover.add(new Smooth(0.0f, HOVER_SPEED));
        }
        while (marks.size() < total) {
            marks.add(new Smooth(0.0f, MARK_SPEED));
        }
        while (slideX.size() < total) {
            int index = slideX.size();
            slideX.add(new Smooth(tileX(slotOf(index)), SLIDE_SPEED));
            slideY.add(new Smooth(tileY(slotOf(index)), SLIDE_SPEED));
        }
    }

    private void paintGap(GuiGraphics graphics) {
        float glow = UiAnim.easeOut(gapGlow.to(gap >= 0 ? 1.0f : 0.0f, UiFrame.delta()));
        if (gap < 0 || glow <= 0.01f) return;

        float x = tileX(gap);
        float y = tileY(gap);
        UiGlass.sunken(graphics, x, y, tileSize, tileSize, RADIUS * unit, 0.8f * glow);
        UiRender.rim(graphics, x, y, tileSize, tileSize, RADIUS * unit, 1.2f, UiTheme.alpha(UiAccent.color(), 0.6f * glow));
    }

    // WHY: плитка рисуется в своём месте покоя, а скольжение, вход и подъём под курсором заявлены ходом
    // WHY: от него: имя и подписи стоят на пиксельной сетке покоя и не идут ступенями по пикселю
    private void paintSlot(GuiGraphics graphics, StudioTile tile, int index, int mouseX, int mouseY) {
        int slot = slotOf(index);
        float delta = UiFrame.delta();
        float restX = tileX(slot);
        float restY = tileY(slot);
        float x = shownX[index];
        float y = shownY[index];
        if (!shownInBand(index)) return;

        float appear = UiAnim.easeOut((System.currentTimeMillis() - shownAt - slot * STAGGER_MS) / APPEAR_MS);
        boolean hovered = !carrying() && inside(mouseX, mouseY, x, y) && inBand(mouseX, mouseY);
        float focus = hover.get(index).to(hovered ? 1.0f : 0.0f, delta);
        float mark = marks.get(index).to(marked.get(index) ? 1.0f : 0.0f, delta);
        float gathered = groupCount > 1 && carrying() && marked.get(index) ? carryLift.get() * GROUP_DIM : 0.0f;
        float lift = (1.0f - appear) * APPEAR_LIFT * unit - focus * wholeRise(graphics);
        UiRestFrame.shift(graphics, x - restX, y - restY + lift);
        try {
            paintTile(graphics, tile, index, restX, restY, appear * (1.0f - gathered), focus, mark);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    // WHY: подъём держится, пока плитка наведена, поэтому он встаёт на целый пиксель: на дробном
    // WHY: подъёме имя всё время наведения стояло бы на полупикселе и читалось мягче соседних
    private float wholeRise(GuiGraphics graphics) {
        float rise = HOVER_RISE * unit;
        float pixels = UiRender.pixels(graphics) / UiRestFrame.stretchX();
        return pixels <= 0.01f ? rise : Math.round(rise * pixels) / pixels;
    }

    private void paintCarried(GuiGraphics graphics, StudioTile tile) {
        float delta = UiFrame.delta();
        float lift = carryLift.to(1.0f, delta);
        float keep = (float) Math.exp(-GRAB_SPEED * delta);
        grabX *= keep;
        grabY *= keep;
        float half = tileSize / 2.0f;
        paintRaised(graphics, tile, carriedFrom, carryX - half + grabX, carryY - half + grabY, lift,
                CARRY_ALPHA, 1.0f, groupCount > 1);
    }

    // WHY: подъём читается тенью, которая растёт и уходит вниз вместе с ростом плитки: без неё плитка
    // WHY: просто увеличивалась и не отрывалась от сетки
    private void paintRaised(GuiGraphics graphics, StudioTile tile, int index, float x, float y, float lift,
                             float alpha, float mark, boolean stacked) {
        float scale = 1.0f + (CARRY_LIFT - 1.0f) * lift;
        float half = tileSize / 2.0f;
        UiRestFrame.push(graphics, x + half, y + half, scale, scale, 0.0f, 0.0f);
        graphics.pose().translate(0.0f, 0.0f, LIFT_DEPTH);
        try {
            UiGlass.hush(graphics, x, y + HUSH_DROP * unit * lift, tileSize, tileSize, RADIUS * unit, HUSH_ALPHA * lift);
            if (stacked) paintStack(graphics, x, y, groupCount, lift);
            paintTile(graphics, tile, index, x, y, alpha, lift, mark);
            if (stacked) paintCount(graphics, x, y, lift);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    // WHY: пачка при переносе видна стопкой карточек под курсором и числом: иначе группа выглядит
    // WHY: как одна плитка, и непонятно, уедет ли в раздел всё выделенное или только она. Стекло
    // WHY: верхней плитки переснимает подложку, иначе нижние карточки стопки в нём не преломлялись бы
    private void paintStack(GuiGraphics graphics, float x, float y, int count, float lift) {
        int layers = Math.min(2, count - 1);
        for (int layer = layers; layer >= 1; layer--) {
            float shift = STACK_STEP * unit * layer * lift;
            UiGlass.panel(graphics, x + shift, y + shift, tileSize, tileSize, RADIUS * unit,
                    CARRY_ALPHA * (1.0f - 0.25f * layer), 0.2f);
        }
        UiGlass.above(graphics, x, y, tileSize, tileSize);
    }

    private void paintCount(GuiGraphics graphics, float x, float y, float lift) {
        float pop = UiAnim.easeOutBack(lift);
        float cx = x + tileSize - COUNT_INSET * unit;
        float cy = y + COUNT_INSET * unit;
        overIcon(graphics);
        try {
            UiRender.dot(graphics, cx, cy, BADGE_RADIUS * unit * pop, UiAccent.color());
            paintCountText(graphics, cx, cy, pop);
        } finally {
            graphics.pose().popPose();
        }
    }

    // WHY: число растёт позой покоя, а не кеглем: кегль, меняющийся каждый кадр, вёл буквы ступенями
    private void paintCountText(GuiGraphics graphics, float cx, float cy, float pop) {
        UiRestFrame.push(graphics, cx, cy, pop, pop, 0.0f, -COUNT_RISE * unit * pop);
        try {
            UiRender.textCentered(graphics, font(), countText, cx, cy, COUNT_SCALE * unit, UiTheme.WHITE, false);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    // WHY: рамка тонирует и значки под собой: значок пишет глубину выше плитки, и рамка на уровне
    // WHY: плитки ложилась под него. Рамку видно и за полосой рядов, поэтому режется по окну целиком
    private void paintBand(GuiGraphics graphics) {
        float shownBand = UiAnim.easeOut(bandShown.to(banding ? 1.0f : 0.0f, UiFrame.delta()));
        if (shownBand <= 0.01f) return;
        float w = bandX1 - bandX0;
        float h = bandY1 - bandY0;
        UiRender.clip(graphics, left, top, width, height);
        overIcon(graphics);
        try {
            UiRender.panel(graphics, bandX0, bandY0, w, h, 3.0f, UiTheme.alpha(UiAccent.color(), 0.12f * shownBand));
            UiRender.rim(graphics, bandX0, bandY0, w, h, 3.0f, 1.0f, UiTheme.alpha(UiAccent.color(), 0.7f * shownBand));
            graphics.flush();
        } finally {
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }

    // WHY: значок предмета пишет глубину на высоте ванильного renderItem, и всё, что обязано лечь
    // WHY: поверх него (затемнение, отметка, счётчик, рамка), поднимается выше, иначе тест глубины
    // WHY: оставлял значок ярким поверх собственного затемнения
    private static void overIcon(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, OVER_ICON);
    }

    private void paintCover(GuiGraphics graphics, StudioTile tile, float x, float y, float mark) {
        if (!tile.faded() && mark <= 0.01f) return;
        overIcon(graphics);
        try {
            if (tile.faded()) UiRender.panel(graphics, x, y, tileSize, tileSize, RADIUS * unit, FADED_SCRIM);
            paintMark(graphics, x, y, mark);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void paintMark(GuiGraphics graphics, float x, float y, float mark) {
        if (mark <= 0.01f) return;
        float radius = RADIUS * unit;
        UiRender.panel(graphics, x, y, tileSize, tileSize, radius, UiTheme.alpha(UiAccent.color(), 0.1f * mark));
        UiRender.rim(graphics, x, y, tileSize, tileSize, radius, 1.5f, UiTheme.alpha(UiAccent.color(), 0.9f * mark));
        float pop = UiAnim.easeOutBack(mark);
        float cx = x + tileSize - CHECK_INSET * unit;
        float cy = y + CHECK_INSET * unit;
        UiRender.dot(graphics, cx, cy, CHECK_RADIUS * unit * pop, UiAccent.color());
        UiRender.check(graphics, cx, cy, CHECK_RADIUS * unit * pop, UiTheme.WHITE);
    }

    private void paintTile(GuiGraphics graphics, StudioTile tile, int index, float x, float y, float appear, float focus,
                           float mark) {
        float taken = index >= 0 && index == chosen ? choice.get() : 0.0f;
        float radius = RADIUS * unit;
        UiGlass.panel(graphics, x, y, tileSize, tileSize, radius, appear, 0.35f * taken + focus * 0.5f);
        if (taken > 0.01f) {
            UiRender.rim(graphics, x, y, tileSize, tileSize, radius, 1.2f, UiTheme.alpha(UiAccent.color(), 0.75f * taken));
        }
        paintBorn(graphics, index, x, y);
        paintIcon(graphics, tile.icon(), x + tileSize / 2.0f - 8.0f * ICON_SCALE * unit, y + ICON_TOP * unit);
        paintCorners(graphics, tile, keyAt(index), x, y);
        paintName(graphics, tile, x, y);
        paintPlate(graphics, tile, keyAt(index), x, y, Math.max(taken, focus));
        paintCover(graphics, tile, x, y, mark);
    }

    private void paintBorn(GuiGraphics graphics, int index, float x, float y) {
        if (index < 0 || index != born) return;
        float age = (System.currentTimeMillis() - bornAt) / BORN_MS;
        if (age >= 1.0f) return;

        float glow = 1.0f - UiAnim.easeOut(age);
        float spread = UiAnim.easeOut(age) * BORN_SPREAD * unit;
        UiRender.rim(graphics, x - spread, y - spread, tileSize + spread * 2.0f, tileSize + spread * 2.0f,
                RADIUS * unit + spread, 1.4f, UiTheme.alpha(UiAccent.color(), glow));
    }

    private void paintIcon(GuiGraphics graphics, ItemStack stack, float x, float y) {
        if (stack.isEmpty()) return;

        float scale = ICON_SCALE * unit;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        try {
            graphics.renderItem(stack, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }

    // WHY: имя стоит на карточке всегда одно и то же: наведение его не подменяет и не добавляет
    // WHY: второй подписи, а длинное имя идёт бегущей строкой внутри своей коробки
    private void paintName(GuiGraphics graphics, StudioTile tile, float x, float y) {
        UiRender.textTrackedFit(graphics, font(), tile.name(), x + tileSize / 2.0f, y + NAME_TOP * unit,
                PLATE_HEIGHT * unit, tileSize - TEXT_INSET * 2.0f * unit, NAME_SCALE * unit, 0.0f,
                UiAccent.text(), false);
    }

    private void paintCorners(GuiGraphics graphics, StudioTile tile, Object key, float x, float y) {
        float inset = TEXT_INSET * unit;
        float boxY = y + BADGE_TOP * unit;
        float boxHeight = BADGE_HEIGHT * unit;
        float boxWidth = tileSize - inset * 2.0f;
        float scale = BADGE_SCALE * unit;
        boxed(graphics, cornerLines, key, tile.corner(), x + inset, boxY, boxHeight, boxWidth, scale,
                UiAccent.textDim(), 0.0f);
        boxed(graphics, alertLines, key, tile.mark(), x + inset, boxY, boxHeight, boxWidth, scale,
                UiPalette.alert(), 1.0f);
    }

    // WHY: плитка без ключа (уезжающая в раздел копия) рисуется прежним пером: ходу не к чему прилипнуть
    private void boxed(GuiGraphics graphics, TickerLines lines, Object key, Component value, float boxX, float boxY,
                       float boxHeight, float boxWidth, float scale, int color, float align) {
        if (key == null) {
            UiRender.textTrackedBox(graphics, font(), value, boxX, boxY, boxHeight, boxWidth, scale, 0.0f, color,
                    false, align);
            return;
        }
        lines.of(key).box(graphics, font(), value, boxX, boxY, boxHeight, boxWidth, scale, color);
    }

    private void paintPlate(GuiGraphics graphics, StudioTile tile, Object key, float x, float y, float lit) {
        float plateX = x + PLATE_INSET * unit;
        float plateWidth = tileSize - PLATE_INSET * unit * 2.0f;
        float plateHeight = PLATE_HEIGHT * unit;
        float plateY = y + tileSize - plateHeight - PLATE_BOTTOM * unit;
        UiGlass.sunken(graphics, plateX, plateY, plateWidth, plateHeight, UiMetrics.radius(plateHeight), 0.9f);
        float meter = tile.meter();
        if (meter >= 0.0f) {
            float barInset = BAR_INSET * unit;
            float barHeight = BAR_HEIGHT * unit;
            UiRender.rect(graphics, plateX + barInset, plateY + plateHeight - barHeight - unit,
                    (plateWidth - barInset * 2.0f) * Math.min(1.0f, meter), barHeight,
                    UiTheme.alpha(tile.meterColor(), 0.8f));
        }
        float boxWidth = plateWidth - 2.0f * unit;
        boxed(graphics, plateLines, key, tile.plate(), x + (tileSize - boxWidth) / 2.0f, plateY - 0.5f * unit,
                plateHeight, boxWidth, PLATE_SCALE * unit, UiTheme.mix(UiAccent.text(), UiAccent.color(), lit), 0.5f);
    }

    private float tileX(int slot) {
        return left + UiMetrics.PAD + Math.floorMod(slot, COLUMNS) * step();
    }

    private float tileY(int slot) {
        return top + lead() + (Math.floorDiv(slot, COLUMNS) - scroll / COLUMNS) * step();
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
