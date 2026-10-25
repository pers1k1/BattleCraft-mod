package com.persiki84.battlecraft.client.menu.lobby;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.client.ClientGameData;
import com.persiki84.battlecraft.client.ClientMatchMode;
import com.persiki84.battlecraft.mode.ModeId;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// WHY: плитки режимов рисуются вручную, как колонки команд, а не кнопками: GlassScreen узнаёт кнопку
// WHY: по тексту, а текст плитки меняется вместе со счётом голосов. Плитки стоят в ряду кнопки «Готов»
// WHY: по бокам от неё: полоса над колонками отнимала у них строку игроков на 1080p
public final class LobbyModeBand {
    private static final float TILE_HEIGHT = 20.0f;
    private static final float MAX_TILE_WIDTH = 132.0f;
    private static final float GAP = 8.0f;
    private static final float SIDE_MARGIN = 16.0f;
    private static final float PAD = 7.0f;
    private static final float NAME_SCALE = 0.85f;
    private static final float COUNT_SCALE = 0.8f;
    private static final float LOCK_SIZE = 7.0f;
    private static final float SPEED = 12.0f;
    private static final float MINE_TINT = 0.24f;
    private static final float HOVER_TINT = 0.08f;
    private static final float CLOSED_DIM = 0.45f;

    private final ModeId[] modes = ModeId.values();
    private final Smooth[] hover = new Smooth[modes.length];
    private final Smooth[] mine = new Smooth[modes.length];
    private final LobbyText[] counts = new LobbyText[modes.length];
    private final Component[] names = new Component[modes.length];
    private final int perSide = (modes.length + 1) / 2;
    private float centerX;
    private float readyHalf;
    private float tileWidth;
    private float top;

    public LobbyModeBand() {
        for (int index = 0; index < modes.length; index++) {
            hover[index] = new Smooth(0.0f, SPEED);
            mine[index] = new Smooth(0.0f, SPEED);
            counts[index] = new LobbyText();
            names[index] = Component.translatable(modes[index].translationKey());
        }
    }

    public boolean shown() {
        return !ClientGameData.isSoftDisabled()
                && ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.LOBBY;
    }

    public void render(GuiGraphics graphics, Font font, float rowCenterX, float rowTop, float readyWidth,
                       int hovered, float delta) {
        centerX = rowCenterX;
        readyHalf = readyWidth / 2.0f;
        top = rowTop;
        float side = centerX - readyHalf - GAP - SIDE_MARGIN;
        tileWidth = Math.max(0.0f, Math.min(MAX_TILE_WIDTH, (side - (perSide - 1) * GAP) / perSide));
        for (int index = 0; index < modes.length; index++) {
            counts[index].set("battlecraft.mode.votes", ClientMatchMode.votes(modes[index]));
            float lit = hover[index].to(index == hovered && votable(index) ? 1.0f : 0.0f, delta);
            float own = mine[index].to(modes[index] == ClientMatchMode.mine() ? 1.0f : 0.0f, delta);
            paintTile(graphics, font, index, tileX(index), lit, own);
        }
    }

    private void paintTile(GuiGraphics graphics, Font font, int index, float x, float lit, float own) {
        int accent = UiAccent.color();
        UiGlass.tinted(graphics, x, top, tileWidth, TILE_HEIGHT, UiMetrics.radius(TILE_HEIGHT), 1.0f,
                0.3f * own + 0.16f * lit, UiTheme.withAlpha(accent, MINE_TINT * own + HOVER_TINT * lit));
        float content = votable(index) || isChosen(index) ? 1.0f : 1.0f - CLOSED_DIM;
        int ink = UiTheme.alpha(UiTheme.mix(UiAccent.text(), UiTheme.mix(accent, UiTheme.WHITE, 0.45f), own), content);
        float right = x + tileWidth - PAD;
        LobbyFigures.line(graphics, font, counts[index], right, UiRender.centerY(top, TILE_HEIGHT, COUNT_SCALE),
                COUNT_SCALE, UiTheme.alpha(UiAccent.textDim(), content), LobbyFigures.RIGHT);
        float nameLeft = x + PAD;
        if (ClientMatchMode.locked() && isChosen(index)) {
            UiRender.iconLock(graphics, nameLeft + LOCK_SIZE / 2.0f, top + TILE_HEIGHT / 2.0f, LOCK_SIZE, true, ink);
            nameLeft += LOCK_SIZE + UiMetrics.GAP_TIGHT;
        }
        float room = right - counts[index].width(font) * COUNT_SCALE - UiMetrics.GAP - nameLeft;
        if (room <= UiMetrics.GAP) return;
        UiRender.textTrackedBox(graphics, font, names[index], nameLeft, top, TILE_HEIGHT, room, NAME_SCALE, 0.0f,
                ink, true, 0.0f);
    }

    // WHY: первая половина режимов стоит слева от кнопки, остальные справа, у кнопки ближайшие
    private float tileX(int index) {
        if (index < perSide) {
            return centerX - readyHalf - GAP - (perSide - index) * tileWidth - (perSide - 1 - index) * GAP;
        }
        return centerX + readyHalf + GAP + (index - perSide) * (tileWidth + GAP);
    }

    private boolean isChosen(int index) {
        return modes[index] == ClientMatchMode.active();
    }

    // WHY: правило сервера повторено здесь только ради отклика: закреплённый режим не голосуется
    public boolean votable(int index) {
        return !ClientMatchMode.locked() && index >= 0 && index < modes.length;
    }

    public int at(double x, double y) {
        if (y < top || y > top + TILE_HEIGHT) return -1;
        for (int index = 0; index < modes.length; index++) {
            if (x >= tileX(index) && x <= tileX(index) + tileWidth) return index;
        }
        return -1;
    }

    public ModeId mode(int index) {
        return modes[index];
    }

    public Component lockedReason() {
        return Component.translatable("battlecraft.mode.error.locked");
    }
}
