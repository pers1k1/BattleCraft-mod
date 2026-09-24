package com.persiki84.battlecraft.client.menu;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.menu.MenuFace;
import com.persiki84.shared.menu.MenuKind;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class MenuWindowLayout {
    public static final float WIDTH = 320.0f;
    public static final float HEIGHT = 208.0f;
    public static final float RADIUS = 13.0f;
    public static final float BAND = 16.0f;

    private static final float PAD = 12.0f;
    private static final float TITLE_TOP = 11.0f;
    private static final float TITLE_SCALE = 1.15f;
    private static final float TAB_TOP = 34.0f;
    private static final float TAB_HEIGHT = 16.0f;
    private static final float TAB_GAP = 4.0f;
    private static final float TAB_WIDEST = 72.0f;
    private static final float TAB_SCALE = 0.72f;
    private static final float TAB_INSET = 6.0f;
    private static final float TOP_WITH_TABS = 62.0f;
    private static final float TOP_BARE = 40.0f;
    private static final float DIVIDER_LIFT = 7.0f;
    private static final float BOTTOM = HEIGHT - 12.0f;

    private static final float ROW_HEIGHT = 18.0f;
    private static final float ROW_GAP = 5.0f;
    private static final float ROW_RADIUS = 5.0f;
    private static final float LINE_HEIGHT = 4.0f;
    private static final float LINE_INSET = 8.0f;
    private static final float PILL_WIDTH = 26.0f;
    private static final float PILL_HEIGHT = 8.0f;
    private static final float[] LINE_SHARES = {0.46f, 0.62f, 0.38f, 0.55f, 0.30f, 0.50f, 0.42f};

    private static final int TILE_COLUMNS = 5;
    private static final int TILE_ROWS = 2;
    private static final float TILE_GAP = 6.0f;
    private static final float ICON_SHARE = 0.46f;
    private static final float PRICE_HEIGHT = 5.0f;

    private static final int MAP_CELLS = 14;
    private static final float MAP_CELL_GAP = 1.0f;
    private static final float MARKER = 5.0f;
    private static final float[][] MARKERS = {{0.28f, 0.34f}, {0.66f, 0.58f}, {0.47f, 0.78f}};

    private static final float DIVIDER_ALPHA = 0.55f;
    private static final float LINE_ALPHA = 0.45f;
    private static final float PILL_ALPHA = 0.70f;
    private static final float ICON_ALPHA = 0.16f;
    private static final float CELL_LOW = 0.10f;
    private static final float CELL_HIGH = 0.24f;

    private MenuWindowLayout() {}

    public static void paint(GuiGraphics graphics, Font font, MenuFace face) {
        float top = face.tabs().isEmpty() ? TOP_BARE : TOP_WITH_TABS;

        UiRender.textTitle(graphics, font, title(face), WIDTH / 2.0f, TITLE_TOP, TITLE_SCALE, 0.0f, UiAccent.text());
        tabs(graphics, font, face.tabs(), face.active());
        UiRender.panel(graphics, PAD, top - DIVIDER_LIFT, WIDTH - PAD * 2.0f, 1.0f, 0.0f,
                UiTheme.withAlpha(UiAccent.color(), DIVIDER_ALPHA));
        body(graphics, face.kind(), top);
    }

    private static Component title(MenuFace face) {
        Component translated = translated(face.title());
        return translated != null ? translated : Component.translatable(face.kind().key());
    }

    // WHY: ключ пришёл от чужого клиента: несуществующий показал бы сырой ключ, поэтому
    // WHY: берутся только те, что есть в загруженном переводе
    private static Component translated(String key) {
        return !key.isEmpty() && Language.getInstance().has(key) ? Component.translatable(key) : null;
    }

    private static void tabs(GuiGraphics graphics, Font font, List<String> keys, int active) {
        int count = keys.size();
        if (count == 0) return;

        float width = Math.min(TAB_WIDEST, (WIDTH - PAD * 2.0f - TAB_GAP * (count - 1)) / count);
        float x = (WIDTH - (width * count + TAB_GAP * (count - 1))) / 2.0f;
        for (int index = 0; index < count; index++) {
            tab(graphics, font, translated(keys.get(index)), x, width, index == active);
            x += width + TAB_GAP;
        }
    }

    private static void tab(GuiGraphics graphics, Font font, Component label, float x, float width, boolean lit) {
        if (lit) {
            UiRender.panel(graphics, x, TAB_TOP, width, TAB_HEIGHT, TAB_HEIGHT / 2.0f, UiAccent.color());
        } else {
            well(graphics, x, TAB_TOP, width, TAB_HEIGHT, TAB_HEIGHT / 2.0f);
        }
        if (label == null) return;

        UiRender.textTrackedFit(graphics, font, label, x + width / 2.0f, TAB_TOP, TAB_HEIGHT,
                width - TAB_INSET, TAB_SCALE, 0.0f, lit ? UiTheme.TEXT : UiAccent.textDim(), true);
    }

    private static void body(GuiGraphics graphics, MenuKind kind, float top) {
        switch (kind) {
            case SHOP -> tiles(graphics, top);
            case MAP -> map(graphics, top);
            default -> rows(graphics, top);
        }
    }

    private static void rows(GuiGraphics graphics, float top) {
        float width = WIDTH - PAD * 2.0f;
        int count = (int) ((BOTTOM - top + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
        for (int row = 0; row < count; row++) {
            float y = top + row * (ROW_HEIGHT + ROW_GAP);
            well(graphics, PAD, y, width, ROW_HEIGHT, ROW_RADIUS);
            UiRender.panel(graphics, PAD + LINE_INSET, y + (ROW_HEIGHT - LINE_HEIGHT) / 2.0f,
                    width * LINE_SHARES[row % LINE_SHARES.length], LINE_HEIGHT, LINE_HEIGHT / 2.0f,
                    UiTheme.withAlpha(UiTheme.FILL, LINE_ALPHA));
            UiRender.panel(graphics, PAD + width - LINE_INSET - PILL_WIDTH, y + (ROW_HEIGHT - PILL_HEIGHT) / 2.0f,
                    PILL_WIDTH, PILL_HEIGHT, PILL_HEIGHT / 2.0f, UiTheme.withAlpha(UiAccent.color(), PILL_ALPHA));
        }
    }

    private static void tiles(GuiGraphics graphics, float top) {
        float size = Math.min((WIDTH - PAD * 2.0f - TILE_GAP * (TILE_COLUMNS - 1)) / TILE_COLUMNS,
                (BOTTOM - top - TILE_GAP * (TILE_ROWS - 1)) / TILE_ROWS);
        float left = (WIDTH - (size * TILE_COLUMNS + TILE_GAP * (TILE_COLUMNS - 1))) / 2.0f;
        for (int slot = 0; slot < TILE_COLUMNS * TILE_ROWS; slot++) {
            float x = left + (slot % TILE_COLUMNS) * (size + TILE_GAP);
            float y = top + (slot / TILE_COLUMNS) * (size + TILE_GAP);
            tile(graphics, x, y, size);
        }
    }

    private static void tile(GuiGraphics graphics, float x, float y, float size) {
        float icon = size * ICON_SHARE;
        well(graphics, x, y, size, size, ROW_RADIUS);
        UiRender.panel(graphics, x + (size - icon) / 2.0f, y + size * 0.18f, icon, icon, ROW_RADIUS,
                UiTheme.withAlpha(UiTheme.FILL, ICON_ALPHA));
        UiRender.panel(graphics, x + size * 0.2f, y + size - PRICE_HEIGHT * 2.2f, size * 0.6f, PRICE_HEIGHT,
                PRICE_HEIGHT / 2.0f, UiTheme.withAlpha(UiAccent.color(), PILL_ALPHA));
    }

    private static void map(GuiGraphics graphics, float top) {
        float span = BOTTOM - top;
        float left = (WIDTH - span * 1.6f) / 2.0f;
        float width = span * 1.6f;
        well(graphics, left, top, width, span, ROW_RADIUS);
        cells(graphics, left, top, width, span);
        for (float[] marker : MARKERS) {
            UiRender.panel(graphics, left + width * marker[0] - MARKER / 2.0f, top + span * marker[1] - MARKER / 2.0f,
                    MARKER, MARKER, MARKER / 2.0f, UiAccent.color());
        }
    }

    // WHY: мозаика клеток детерминирована по номеру клетки: карточка печётся один раз, и случайность
    // WHY: при перепекании на смене акцента меняла бы рисунок карты у всех окон разом
    private static void cells(GuiGraphics graphics, float left, float top, float width, float height) {
        float cellWidth = width / MAP_CELLS;
        int rows = Math.max(1, (int) (height / cellWidth));
        float cellHeight = height / rows;
        for (int cell = 0; cell < MAP_CELLS * rows; cell++) {
            float shade = ((cell * 7919 + (cell / MAP_CELLS) * 104729) % 97) / 96.0f;
            UiRender.panel(graphics, left + (cell % MAP_CELLS) * cellWidth + MAP_CELL_GAP,
                    top + (cell / MAP_CELLS) * cellHeight + MAP_CELL_GAP,
                    cellWidth - MAP_CELL_GAP * 2.0f, cellHeight - MAP_CELL_GAP * 2.0f, 0.0f,
                    UiTheme.withAlpha(UiTheme.FILL_DIM, CELL_LOW + (CELL_HIGH - CELL_LOW) * shade));
        }
    }

    private static void well(GuiGraphics graphics, float x, float y, float width, float height, float radius) {
        UiRender.panelShaded(graphics, x, y, width, height, radius, UiTheme.GLASS_WELL_TOP, UiTheme.GLASS_WELL_BOTTOM);
    }
}
