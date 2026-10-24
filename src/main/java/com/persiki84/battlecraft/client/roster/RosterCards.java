package com.persiki84.battlecraft.client.roster;

import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.battlecraft.client.menu.lobby.LobbyHead;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.UUID;

// WHY: карточка команды и строка игрока устроены как колонка и плашка выбора команды: стеклянный
// WHY: колодец в цвет команды, своя строка залита цветом, место строки едет, появление поднимает её
final class RosterCards {
    private static final float CARD_WELL = 0.55f;
    private static final float CARD_TINT = 0.12f;
    private static final float TITLE_SCALE = 0.75f;
    private static final float TITLE_LIGHT = 0.35f;
    private static final float TITLE_INSET = 2.5f;
    private static final float COUNT_SCALE = 0.7f;
    private static final float SCORE_SCALE = 0.75f;
    private static final float LATENCY_SCALE = 0.62f;
    private static final float HEART_SIZE = 5.0f;
    private static final float OWN_FILL = 0.30f;
    private static final float WATCHER_DIM = 0.55f;
    private static final float RISE = 4.0f;

    private RosterCards() {}

    public static void drawGroups(GuiGraphics graphics, Font font, float alpha) {
        for (RosterGroup group : RosterBook.groups()) {
            float shown = group.presence.get() * alpha;
            if (shown > RosterRow.GONE) drawGroup(graphics, font, group, shown);
        }
    }

    private static void drawGroup(GuiGraphics graphics, Font font, RosterGroup group, float alpha) {
        float x = group.x.get();
        float y = group.y.get();
        float width = group.width.get();
        int color = group.color();
        UiGlass.sunken(graphics, x, y, width, group.height.get(), RosterLayout.CARD_RADIUS, CARD_WELL * alpha);
        UiRender.panel(graphics, x, y, width, group.height.get(), RosterLayout.CARD_RADIUS,
                UiTheme.withAlpha(color, CARD_TINT * alpha));

        float right = x + width - RosterLayout.CARD_PAD - TITLE_INSET;
        group.count.draw(graphics, font, right, UiRender.centerY(y, RosterLayout.CARD_HEAD, COUNT_SCALE),
                COUNT_SCALE, HudInk.textDim(), alpha);
        float left = x + RosterLayout.CARD_PAD + TITLE_INSET;
        float room = right - group.count.steadyWidth(graphics, font, COUNT_SCALE) - UiMetrics.GAP - left;
        if (room <= UiMetrics.GAP) return;
        UiRender.textTrackedBox(graphics, font, RosterBook.titleOf(group), left, y, RosterLayout.CARD_HEAD, room,
                TITLE_SCALE, 0.0f, UiTheme.alpha(UiTheme.mix(color, UiTheme.WHITE, TITLE_LIGHT), alpha), true, 0.0f);
    }

    public static void drawRows(GuiGraphics graphics, Font font, RosterLayout layout, float alpha) {
        UUID self = selfId();
        RosterRow own = null;
        List<RosterRow> rows = RosterBook.rows();
        for (int at = 0; at < rows.size(); at++) {
            RosterRow row = rows.get(at);
            if (row.uuid.equals(self)) {
                own = row;
                continue;
            }
            drawRow(graphics, font, layout, row, false, alpha);
        }
        if (own != null) drawRow(graphics, font, layout, own, true, alpha);
    }

    private static UUID selfId() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? null : minecraft.player.getUUID();
    }

    // WHY: строка рисуется на месте покоя, а полёт и подъём появления заявлены ходом: имя в живых
    // WHY: координатах шло бы в полёте ступенями по пикселю
    private static void drawRow(GuiGraphics graphics, Font font, RosterLayout layout, RosterRow row,
                                boolean self, float alpha) {
        float shown = row.presence.get();
        if (shown * alpha <= RosterRow.GONE) return;

        UiRestFrame.shift(graphics, row.x.get() - row.targetX(),
                row.y.get() + (1.0f - shown) * RISE - row.targetY());
        try {
            paintRow(graphics, font, layout, row, self, shown * alpha * (row.spectator() ? WATCHER_DIM : 1.0f));
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private static void paintRow(GuiGraphics graphics, Font font, RosterLayout layout, RosterRow row,
                                 boolean self, float alpha) {
        float x = row.targetX();
        float y = row.targetY();
        float height = RosterLayout.ROW_HEIGHT;
        if (self) {
            UiRender.panel(graphics, x, y, layout.columnWidth, height, UiMetrics.radius(height),
                    UiTheme.withAlpha(row.tintNow(), OWN_FILL * alpha));
        }
        float head = RosterLayout.HEAD_SIZE;
        LobbyHead.draw(graphics, row.uuid, x + RosterLayout.ROW_INSET, y + (height - head) / 2.0f,
                RosterLayout.HEAD_SIZE, alpha);
        float nameLeft = x + RosterLayout.ROW_INSET + head + RosterLayout.CELL_GAP;
        UiRender.textTrackedBox(graphics, font, row.name(), nameLeft, y, height, layout.nameWidth,
                RosterLayout.NAME_SCALE, 0.0f, UiTheme.alpha(HudInk.text(), alpha), false, 0.0f);

        float pingRight = x + layout.columnWidth - RosterLayout.ROW_INSET;
        paintLatency(graphics, font, row, pingRight, y, alpha);
        if (!row.scored()) return;
        paintScore(graphics, font, row, pingRight - RosterLayout.PING_WIDTH - RosterLayout.CELL_GAP, y, alpha);
    }

    private static void paintLatency(GuiGraphics graphics, Font font, RosterRow row, float right, float y,
                                     float alpha) {
        float height = RosterLayout.ROW_HEIGHT;
        float barsLeft = right - RosterPing.WIDTH;
        RosterPing.draw(graphics, barsLeft, y + height / 2.0f, row.latency(), alpha);
        int tone = row.latency() < 0 ? HudInk.textFaint() : RosterTone.latency(row.latency());
        row.latencyTicker.draw(graphics, font, barsLeft - UiMetrics.GAP_TIGHT,
                UiRender.centerY(y, height, LATENCY_SCALE), LATENCY_SCALE, UiTheme.mix(tone, HudInk.textDim(), 0.35f),
                alpha);
    }

    private static void paintScore(GuiGraphics graphics, Font font, RosterRow row, float right, float y,
                                   float alpha) {
        float height = RosterLayout.ROW_HEIGHT;
        row.scoreTicker.draw(graphics, font, right, UiRender.centerY(y, height, SCORE_SCALE), SCORE_SCALE,
                HudInk.text(), alpha);
        if (!row.hearts()) return;
        float numberWidth = row.scoreTicker.steadyWidth(graphics, font, SCORE_SCALE);
        UiRender.iconHeart(graphics, right - numberWidth - UiMetrics.GAP_TIGHT - HEART_SIZE / 2.0f,
                y + height / 2.0f, HEART_SIZE, UiTheme.alpha(RosterTone.BAD, alpha));
    }
}
