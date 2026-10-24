package com.persiki84.battlecraft.client.roster;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Arrays;
import java.util.List;

// WHY: группы кладутся карточками в ряд, ряд переносится, когда не влезает в экран; внутри карточки
// WHY: игроки идут колонками сверху вниз, как в ванильном списке, не больше 20 в колонке
final class RosterLayout {
    static final float ROW_HEIGHT = 12.0f;
    static final float ROW_STEP = 13.0f;
    static final int HEAD_SIZE = 8;
    static final float ROW_INSET = 2.5f;
    static final float NAME_SCALE = 0.8f;
    static final float CELL_GAP = 4.0f;
    static final float SCORE_WIDTH = 22.0f;
    static final float PING_WIDTH = 34.0f;
    static final float CARD_PAD = 4.0f;
    static final float CARD_HEAD = 14.0f;
    static final float CARD_RADIUS = 7.0f;
    static final float PANEL_RADIUS = 12.0f;
    static final float STATUS_HEIGHT = 16.0f;
    static final float PAD = 9.0f;
    static final float SECTION_GAP = 6.0f;

    private static final float NAME_MIN = 56.0f;
    private static final float NAME_MAX = 108.0f;
    private static final float NAME_FLOOR = 20.0f;
    // WHY: запас под имя на случай, когда окно ужато под высоту экрана и пиксели покоя при отрисовке
    // WHY: чуть другие, чем при замере: без него длинное имя на долю единицы уходило в бегущую строку
    private static final float NAME_SLACK = 1.0f;
    private static final float CARD_GAP = 6.0f;
    private static final float COLUMN_GAP = 4.0f;
    private static final float TOP_SHARE = 0.06f;
    private static final float EDGE = 6.0f;
    private static final float WIDTH_SHARE = 0.94f;
    private static final float HEIGHT_SHARE = 0.88f;
    private static final float MIN_WIDTH = 232.0f;
    private static final int MAX_ROWS = 20;
    private static final int MIN_ROWS = 4;

    private float[] cardWidth = new float[8];
    private float[] cardHeight = new float[8];
    private float[] cardOffset = new float[8];
    private int[] cardShelf = new int[8];
    private float[] shelfWidth = new float[8];
    private float[] shelfHeight = new float[8];
    private int shelves;

    float panelX;
    float panelY;
    float panelWidth;
    float panelHeight;
    float nameWidth;
    float columnWidth;

    public void compute(GuiGraphics graphics, Font font, float screenWidth, float screenHeight, float headerHeight,
                        float footerHeight, float chromeWidth) {
        List<RosterGroup> groups = RosterBook.groups();
        reserve(groups.size());
        int cap = rowsCap(screenHeight, headerHeight, footerHeight);
        float limit = screenWidth * WIDTH_SHARE - PAD * 2.0f;
        nameWidth = fittedName(widestName(graphics, font), widestColumns(groups, cap), limit);
        columnWidth = columnWidthFor(nameWidth);
        shelve(groups, cap, limit);

        float bodyWidth = 0.0f;
        float bodyHeight = 0.0f;
        for (int shelf = 0; shelf < shelves; shelf++) {
            bodyWidth = Math.max(bodyWidth, shelfWidth[shelf]);
            bodyHeight += shelfHeight[shelf] + (shelf > 0 ? CARD_GAP : 0.0f);
        }
        float contentWidth = Math.max(MIN_WIDTH, Math.max(bodyWidth, chromeWidth) + PAD * 2.0f);
        panelWidth = Math.min(screenWidth - EDGE * 2.0f, contentWidth);
        panelX = (screenWidth - panelWidth) / 2.0f;
        panelY = Math.max(EDGE, screenHeight * TOP_SHARE);
        float bodyY = panelY + PAD + gapped(headerHeight) + STATUS_HEIGHT + SECTION_GAP;
        panelHeight = bodyY + bodyHeight + gapped(footerHeight) + PAD - panelY;
        aimGroups(groups, cap, bodyY);
    }

    private static float gapped(float height) {
        return height > 0.0f ? height + SECTION_GAP : 0.0f;
    }

    private void reserve(int count) {
        if (cardWidth.length >= count) return;
        int size = Math.max(count, cardWidth.length * 2);
        cardWidth = Arrays.copyOf(cardWidth, size);
        cardHeight = Arrays.copyOf(cardHeight, size);
        cardOffset = Arrays.copyOf(cardOffset, size);
        cardShelf = Arrays.copyOf(cardShelf, size);
        shelfWidth = Arrays.copyOf(shelfWidth, size);
        shelfHeight = Arrays.copyOf(shelfHeight, size);
    }

    // WHY: доля экрана, в которую окно влезает по высоте; меньше единицы - окно ужимается целиком,
    // WHY: иначе полки карточек при многих командах уходили бы за нижний край
    public float fit(float screenHeight) {
        float room = screenHeight * HEIGHT_SHARE - panelY;
        return panelHeight <= room ? 1.0f : room / panelHeight;
    }

    private static float widestName(GuiGraphics graphics, Font font) {
        float widest = 0.0f;
        for (RosterRow row : RosterBook.rows()) {
            if (row.listed()) widest = Math.max(widest, row.nameWidth(graphics, font) + NAME_SLACK);
        }
        return Math.max(NAME_MIN, Math.min(NAME_MAX, widest));
    }

    private static int widestColumns(List<RosterGroup> groups, int cap) {
        int widest = 1;
        for (RosterGroup group : groups) {
            if (group.listed()) widest = Math.max(widest, columnsOf(group.members.size(), cap));
        }
        return widest;
    }

    // WHY: как ванильный список, который ужимает колонки до ширины экрана: самая широкая карточка
    // WHY: обязана влезть в полку целиком, лишнее срезает имя, и оно идёт бегущей строкой
    private static float fittedName(float wanted, int columns, float limit) {
        float column = (limit - CARD_PAD * 2.0f - (columns - 1) * COLUMN_GAP) / columns;
        float room = column - columnWidthFor(0.0f);
        return Math.max(NAME_FLOOR, Math.min(wanted, room));
    }

    private static float columnWidthFor(float names) {
        float score = RosterBook.scored() ? SCORE_WIDTH + CELL_GAP : 0.0f;
        return ROW_INSET * 2.0f + HEAD_SIZE + CELL_GAP + names + CELL_GAP + score + PING_WIDTH;
    }

    private static int rowsCap(float screenHeight, float headerHeight, float footerHeight) {
        float chrome = PAD * 2.0f + gapped(headerHeight) + gapped(footerHeight) + STATUS_HEIGHT + SECTION_GAP
                + CARD_HEAD + CARD_PAD;
        float room = screenHeight * HEIGHT_SHARE - Math.max(EDGE, screenHeight * TOP_SHARE) - chrome;
        int fits = (int) ((room + ROW_STEP - ROW_HEIGHT) / ROW_STEP);
        return Math.max(MIN_ROWS, Math.min(MAX_ROWS, fits));
    }

    private void shelve(List<RosterGroup> groups, int cap, float limit) {
        shelves = 0;
        shelfWidth[0] = 0.0f;
        shelfHeight[0] = 0.0f;
        for (int at = 0; at < groups.size(); at++) {
            RosterGroup group = groups.get(at);
            if (!group.listed()) continue;
            measureCard(at, group.members.size(), cap);
            int shelf = Math.max(0, shelves - 1);
            boolean opens = shelves == 0 || shelfWidth[shelf] + CARD_GAP + cardWidth[at] > limit;
            if (opens) shelf = openShelf();
            cardOffset[at] = shelfWidth[shelf] > 0.0f ? shelfWidth[shelf] + CARD_GAP : 0.0f;
            cardShelf[at] = shelf;
            shelfWidth[shelf] = cardOffset[at] + cardWidth[at];
            shelfHeight[shelf] = Math.max(shelfHeight[shelf], cardHeight[at]);
        }
    }

    private int openShelf() {
        int shelf = shelves++;
        shelfWidth[shelf] = 0.0f;
        shelfHeight[shelf] = 0.0f;
        return shelf;
    }

    private void measureCard(int at, int members, int cap) {
        int columns = columnsOf(members, cap);
        int rows = rowsOf(members, columns);
        cardWidth[at] = columns * columnWidth + (columns - 1) * COLUMN_GAP + CARD_PAD * 2.0f;
        cardHeight[at] = CARD_HEAD + rows * ROW_STEP - (ROW_STEP - ROW_HEIGHT) + CARD_PAD;
    }

    private static int columnsOf(int members, int cap) {
        return Math.max(1, (members + cap - 1) / cap);
    }

    private static int rowsOf(int members, int columns) {
        return Math.max(1, (members + columns - 1) / columns);
    }

    private void aimGroups(List<RosterGroup> groups, int cap, float bodyY) {
        for (int at = 0; at < groups.size(); at++) {
            RosterGroup group = groups.get(at);
            if (!group.listed()) continue;
            int shelf = cardShelf[at];
            float x = panelX + (panelWidth - shelfWidth[shelf]) / 2.0f + cardOffset[at];
            float y = bodyY + shelfTop(shelf);
            group.aim(x, y, cardWidth[at], cardHeight[at]);
            aimRows(group, cap, x, y);
        }
    }

    private float shelfTop(int shelf) {
        float top = 0.0f;
        for (int above = 0; above < shelf; above++) top += shelfHeight[above] + CARD_GAP;
        return top;
    }

    private void aimRows(RosterGroup group, int cap, float cardX, float cardY) {
        int members = group.members.size();
        int rows = rowsOf(members, columnsOf(members, cap));
        for (int at = 0; at < members; at++) {
            int column = at / rows;
            float x = cardX + CARD_PAD + column * (columnWidth + COLUMN_GAP);
            float y = cardY + CARD_HEAD + (at % rows) * ROW_STEP;
            group.members.get(at).aim(x, y);
        }
    }
}
