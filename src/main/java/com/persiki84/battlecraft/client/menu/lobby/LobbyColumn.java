package com.persiki84.battlecraft.client.menu.lobby;

import com.persiki84.battlecraft.client.ClientGameData;
import com.persiki84.battlecraft.client.ClientLobbyData;
import com.persiki84.minimap.client.MapRenderUtil;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class LobbyColumn {
    private static final float HOVER_SPEED = 12.0f;
    private static final float STATE_SPEED = 10.0f;
    private static final float DENY_SPEED = 5.0f;
    private static final float TEAM_TINT = 0.10f;
    private static final float OWN_TINT = 0.24f;
    private static final float HOVER_TINT = 0.08f;
    private static final float OWN_LIFT = 0.34f;
    private static final float HOVER_LIFT = 0.16f;
    private static final float CLOSED_DIM = 0.45f;
    private static final float SLOT_ALPHA = 0.22f;
    private static final float GHOST_ALPHA = 0.42f;
    private static final float LABEL_SCALE = 1.0f;
    private static final float COUNT_SCALE = 0.8f;
    private static final float FOOTER_SCALE = 0.8f;
    private static final float MORE_ROOM = 20.0f;
    private static final float PAD = 4.5f;
    private static final long PENDING_MS = 1500L;
    private static final float GONE = 0.01f;

    private final String team;
    private final Component name;
    private final Smooth hover = new Smooth(0.0f, HOVER_SPEED);
    private final Smooth own = new Smooth(STATE_SPEED);
    private final Smooth closed = new Smooth(STATE_SPEED);
    private final Smooth ghost = new Smooth(0.0f, HOVER_SPEED);
    private final Smooth denied = new Smooth(0.0f, DENY_SPEED);
    private final LobbyText counter = new LobbyText();
    private final LobbyText footer = new LobbyText();
    private final LobbyText more = new LobbyText();
    private LobbyGate gate = LobbyGate.OPEN;
    private long pendingUntil;

    public LobbyColumn(String team) {
        this.team = team;
        this.name = Component.literal(team);
    }

    public String team() {
        return team;
    }

    public LobbyGate gate() {
        return gate;
    }

    public Component reason() {
        return footer.current();
    }

    public void refresh() {
        gate = LobbyGate.of(team);
        if (gate != LobbyGate.OPEN) pendingUntil = 0L;
        counter.set("battlecraft.lobby.slots", ClientLobbyData.sizeOf(team), ClientLobbyData.slotsPerTeam());
        int seconds = gate == LobbyGate.COOLDOWN ? ClientGameData.getSwitchCooldownSeconds() : 0;
        footer.set(gate.key(), seconds);
    }

    public void pressed() {
        pendingUntil = System.currentTimeMillis() + PENDING_MS;
    }

    public void refused() {
        denied.snap(1.0f);
    }

    public void render(GuiGraphics graphics, Font font, LobbyLayout layout, int index, boolean hovered, float delta) {
        int accent = MapRenderUtil.getTeamColor(team);
        float x = layout.columnX(index);
        float lit = hover.to(hovered && gate.admits() ? 1.0f : 0.0f, delta);
        float mine = own.to(gate == LobbyGate.MINE ? 1.0f : 0.0f, delta);
        float shut = closed.to(gate.admits() || gate == LobbyGate.MINE ? 0.0f : 1.0f, delta);
        denied.to(0.0f, delta);

        UiGlass.tinted(graphics, x, layout.top(), layout.width(), layout.height(), LobbyLayout.RADIUS, 1.0f,
                OWN_LIFT * mine + HOVER_LIFT * lit,
                UiTheme.withAlpha(accent, TEAM_TINT + OWN_TINT * mine + HOVER_TINT * lit));
        float content = 1.0f - CLOSED_DIM * shut;
        renderHeading(graphics, font, layout, x, accent, content);
        renderSlots(graphics, layout, index, content);
        renderGhost(graphics, layout, index, hovered, accent, delta);
        renderFooter(graphics, font, layout, x, accent, lit, mine, content);
    }

    private void renderHeading(GuiGraphics graphics, Font font, LobbyLayout layout, float x, int accent,
                               float content) {
        float left = x + LobbyLayout.TILE_INSET + PAD;
        float right = x + layout.width() - LobbyLayout.TILE_INSET - PAD;
        float top = layout.top() + UiMetrics.PAD;
        float countY = UiRender.centerY(top, LobbyLayout.HEADER, COUNT_SCALE);
        LobbyFigures.line(graphics, font, counter, right, countY, COUNT_SCALE,
                UiTheme.alpha(UiAccent.textDim(), content), LobbyFigures.RIGHT);

        float nameWidth = right - left - counter.width(font) * COUNT_SCALE - UiMetrics.GAP;
        if (nameWidth < PAD * 2.0f) return;
        UiRender.textTrackedBox(graphics, font, name, left, top, LobbyLayout.HEADER, nameWidth, LABEL_SCALE, 0.0f,
                UiTheme.alpha(UiTheme.mix(accent, UiTheme.WHITE, 0.35f), content), true, 0.0f);
    }

    private void renderSlots(GuiGraphics graphics, LobbyLayout layout, int index, float content) {
        int taken = ClientLobbyData.sizeOf(team);
        int free = Math.min(ClientLobbyData.slotsPerTeam(), layout.rows());
        float radius = UiMetrics.radius(LobbyLayout.TILE_HEIGHT);
        for (int row = taken; row < free; row++) {
            UiGlass.sunken(graphics, layout.tileX(index), layout.rowY(row), layout.tileWidth(),
                    LobbyLayout.TILE_HEIGHT, radius, SLOT_ALPHA * content);
        }
    }

    // WHY: наведённая открытая колонка показывает, куда встанет своя голова, а после щелчка держит
    // WHY: это место, пока сервер не пришлёт новый состав: иначе нажатие полсекунды выглядит пропавшим
    private void renderGhost(GuiGraphics graphics, LobbyLayout layout, int index, boolean hovered, int accent,
                             float delta) {
        boolean pending = System.currentTimeMillis() < pendingUntil;
        int row = ClientLobbyData.sizeOf(team);
        boolean room = row < layout.rows() && gate.admits();
        float shown = ghost.to(room && (hovered || pending) ? 1.0f : 0.0f, delta);
        if (shown <= GONE || row >= layout.rows() || Minecraft.getInstance().player == null) return;

        float x = layout.tileX(index);
        float y = layout.rowY(row);
        float alpha = GHOST_ALPHA * shown * (pending ? 1.6f : 1.0f);
        UiRender.panel(graphics, x, y, layout.tileWidth(), LobbyLayout.TILE_HEIGHT,
                UiMetrics.radius(LobbyLayout.TILE_HEIGHT), UiTheme.withAlpha(accent, 0.22f * alpha));
        LobbyHead.draw(graphics, Minecraft.getInstance().player.getUUID(), x + 4.0f,
                y + (LobbyLayout.TILE_HEIGHT - LobbyTiles.HEAD_SIZE) / 2.0f, LobbyTiles.HEAD_SIZE, alpha);
    }

    private void renderFooter(GuiGraphics graphics, Font font, LobbyLayout layout, float x, int accent,
                              float lit, float mine, float content) {
        float left = x + LobbyLayout.TILE_INSET + PAD;
        float width = layout.width() - (LobbyLayout.TILE_INSET + PAD) * 2.0f;
        int hidden = ClientLobbyData.sizeOf(team) - Math.min(ClientLobbyData.sizeOf(team), layout.rows());
        float top = layout.footerTop();
        if (hidden > 0) {
            more.set("battlecraft.lobby.more_short", hidden);
            LobbyFigures.line(graphics, font, more, left + width,
                    UiRender.centerY(top, LobbyLayout.FOOTER, FOOTER_SCALE), FOOTER_SCALE,
                    UiTheme.alpha(UiAccent.textDim(), content), LobbyFigures.RIGHT);
            width -= MORE_ROOM;
        }
        LobbyFigures.boxed(graphics, font, footer, left, top, width, LobbyLayout.FOOTER, FOOTER_SCALE,
                footerTone(accent, lit, mine), LobbyFigures.CENTER);
    }

    private int footerTone(int accent, float lit, float mine) {
        int idle = UiTheme.mix(UiAccent.textFaint(), UiAccent.text(), lit);
        int own = UiTheme.mix(idle, UiTheme.mix(accent, UiTheme.WHITE, 0.45f), mine);
        return UiTheme.mix(own, UiPalette.alert(), denied.get());
    }
}
