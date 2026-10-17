package com.persiki84.battlecraft.client.menu.lobby;

import com.persiki84.battlecraft.client.ClientLobbyData;
import com.persiki84.battlecraft.network.S2CLobbyRosterPacket;
import com.persiki84.minimap.client.MapRenderUtil;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LobbyTiles {
    public static final int HEAD_SIZE = 14;
    private static final float GLIDE_SPEED = 9.0f;
    private static final float FADE_SPEED = 12.0f;
    private static final float TINT_SPEED = 6.0f;
    private static final float RISE = 6.0f;
    private static final float PADDING = 4.0f;
    private static final float NAME_ROW = 11.0f;
    private static final float STATUS_ROW = 8.0f;
    private static final float NAME_SCALE = 0.95f;
    private static final float STATUS_SCALE = 0.75f;
    private static final float NAME_MIN_WIDTH = 10.0f;
    private static final float DOT_RADIUS = 3.0f;
    private static final float WELL_ALPHA = 0.72f;
    private static final float OWN_FILL = 0.30f;
    private static final float GONE = 0.01f;

    private static final Component READY = Component.translatable("battlecraft.lobby.member_ready");
    private static final Component WAITING = Component.translatable("battlecraft.lobby.member_waiting");

    private final List<Tile> tiles = new ArrayList<>();
    private final Map<UUID, Tile> index = new HashMap<>();
    private int syncedRoster = -1;
    private int syncedLayout = -1;
    private int syncedFrame = -1;
    private boolean seeded;

    public void sync(LobbyLayout layout) {
        if (syncedRoster == ClientLobbyData.version() && syncedLayout == layout.stamp()) return;

        boolean reframed = syncedFrame != layout.frame();
        syncedRoster = ClientLobbyData.version();
        syncedLayout = layout.stamp();
        syncedFrame = layout.frame();
        for (int at = 0; at < tiles.size(); at++) tiles.get(at).listed = false;

        List<String> teams = ClientLobbyData.teams();
        UUID self = selfId();
        for (int column = 0; column < teams.size(); column++) {
            place(layout, teams.get(column), column, self);
        }
        if (reframed) settle();
        seeded = true;
    }

    private void place(LobbyLayout layout, String team, int column, UUID self) {
        List<S2CLobbyRosterPacket.Member> members = ClientLobbyData.membersOf(team);
        int shown = Math.min(members.size(), layout.rows());
        int own = rowOf(members, self);
        for (int row = 0; row < shown; row++) {
            int picked = row == shown - 1 && own >= shown ? own : row;
            aim(members.get(picked), team, layout.tileX(column), layout.rowY(row));
        }
    }

    private void aim(S2CLobbyRosterPacket.Member member, String team, float x, float y) {
        Tile tile = index.get(member.uuid());
        if (tile == null) {
            tile = new Tile(member.uuid(), x, y, seeded);
            index.put(member.uuid(), tile);
            tiles.add(tile);
        }
        tile.aim(member, team, x, y);
    }

    // WHY: в переполненной колонке своя плашка встаёт на последнее видимое место: иначе голова,
    // WHY: перелетевшая в команду, где игроков больше, чем рядов, исчезала бы под строкой «ещё N»
    private static int rowOf(List<S2CLobbyRosterPacket.Member> members, UUID self) {
        if (self == null) return -1;
        for (int row = 0; row < members.size(); row++) {
            if (members.get(row).uuid().equals(self)) return row;
        }
        return -1;
    }

    // WHY: смена размера окна переставляет колонки, и плашки, уехавшие бы к новому месту через весь
    // WHY: экран, читались бы как переход игроков между командами. Новое место ставится сразу.
    // WHY: Рост числа рядов колонки не двигает, поэтому полёт своей головы он не обрывает
    private void settle() {
        for (int at = 0; at < tiles.size(); at++) tiles.get(at).settle();
    }

    public void render(GuiGraphics graphics, Font font, float width, float delta) {
        Tile own = null;
        UUID self = selfId();
        for (int at = tiles.size() - 1; at >= 0; at--) {
            Tile tile = tiles.get(at);
            if (!tile.advance(delta)) {
                index.remove(tile.uuid);
                tiles.remove(at);
                continue;
            }
            if (tile.uuid.equals(self)) {
                own = tile;
                continue;
            }
            paint(graphics, font, tile, width, false);
        }
        if (own != null) paint(graphics, font, own, width, true);
    }

    private static UUID selfId() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? null : minecraft.player.getUUID();
    }

    // WHY: плашка рисуется на своём месте покоя, а полёт и подъём появления заявлены ходом:
    // WHY: имя, переданное в живых координатах, шло бы в полёте ступенями по пикселю
    private void paint(GuiGraphics graphics, Font font, Tile tile, float width, boolean self) {
        float alpha = tile.presence.get();
        if (alpha <= GONE) return;

        UiRestFrame.shift(graphics, tile.x.get() - tile.targetX,
                tile.y.get() + (1.0f - alpha) * RISE - tile.targetY);
        try {
            paintAt(graphics, font, tile, tile.targetX, tile.targetY, width, self, alpha);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private void paintAt(GuiGraphics graphics, Font font, Tile tile, float x, float y, float width, boolean self,
                         float alpha) {
        int accent = UiTheme.mix(tile.fromTint, tile.toTint, tile.tint.get());
        float radius = UiMetrics.radius(LobbyLayout.TILE_HEIGHT);
        if (self) {
            UiRender.panel(graphics, x, y, width, LobbyLayout.TILE_HEIGHT, radius,
                    UiTheme.withAlpha(accent, OWN_FILL * alpha));
        } else {
            UiGlass.sunken(graphics, x, y, width, LobbyLayout.TILE_HEIGHT, radius, WELL_ALPHA * alpha);
        }

        LobbyHead.draw(graphics, tile.uuid, x + PADDING, y + (LobbyLayout.TILE_HEIGHT - HEAD_SIZE) / 2.0f,
                HEAD_SIZE, alpha);
        float ready = tile.ready.get();
        paintDot(graphics, accent, x + width - PADDING - DOT_RADIUS, y + LobbyLayout.TILE_HEIGHT / 2.0f, ready, alpha);
        paintIdentity(graphics, font, tile, accent, x, y, width, ready, alpha);
    }

    private static void paintIdentity(GuiGraphics graphics, Font font, Tile tile, int accent, float x, float y,
                                      float width, float ready, float alpha) {
        float textLeft = x + PADDING + HEAD_SIZE + UiMetrics.GAP;
        float textWidth = x + width - PADDING - DOT_RADIUS * 2.0f - UiMetrics.GAP - textLeft;
        if (textWidth < NAME_MIN_WIDTH) return;

        float top = y + (LobbyLayout.TILE_HEIGHT - NAME_ROW - STATUS_ROW) / 2.0f;
        UiRender.textTrackedBox(graphics, font, tile.name, textLeft, top, NAME_ROW, textWidth, NAME_SCALE, 0.0f,
                UiTheme.alpha(UiAccent.text(), alpha), false, 0.0f);

        int lit = UiTheme.mix(accent, UiTheme.WHITE, 0.45f);
        paintStatus(graphics, font, WAITING, UiAccent.textFaint(), (1.0f - ready) * alpha, textLeft, top + NAME_ROW,
                textWidth);
        paintStatus(graphics, font, READY, lit, ready * alpha, textLeft, top + NAME_ROW, textWidth);
    }

    private static void paintStatus(GuiGraphics graphics, Font font, Component line, int tone, float alpha,
                                    float left, float top, float width) {
        if (alpha <= GONE) return;
        UiRender.textTrackedBox(graphics, font, line, left, top, STATUS_ROW, width, STATUS_SCALE, 0.0f,
                UiTheme.alpha(tone, alpha), false, 0.0f);
    }

    private static void paintDot(GuiGraphics graphics, int accent, float centerX, float centerY, float ready,
                                 float alpha) {
        if (ready < 1.0f) {
            UiRender.ring(graphics, centerX, centerY, DOT_RADIUS, 1.1f, 1.0f,
                    UiTheme.withAlpha(UiTheme.WHITE, 0.24f * (1.0f - ready) * alpha));
        }
        if (ready <= GONE) return;

        float grown = DOT_RADIUS * (0.6f + 0.4f * ready);
        UiRender.dot(graphics, centerX, centerY, grown * 2.0f, UiTheme.withAlpha(accent, 0.18f * ready * alpha));
        UiRender.dot(graphics, centerX, centerY, grown,
                UiTheme.alpha(UiTheme.mix(accent, UiTheme.WHITE, 0.25f), ready * alpha));
    }

    private static final class Tile {
        private final UUID uuid;
        private final Smooth x;
        private final Smooth y;
        private final Smooth presence;
        private final Smooth ready = new Smooth(FADE_SPEED);
        private final Smooth tint = new Smooth(1.0f, TINT_SPEED);
        private String team;
        private String nameText;
        private Component name = Component.empty();
        private float targetX;
        private float targetY;
        private int fromTint;
        private int toTint;
        private boolean wantsReady;
        private boolean listed;

        private Tile(UUID uuid, float x, float y, boolean fresh) {
            this.uuid = uuid;
            this.x = new Smooth(x, GLIDE_SPEED);
            this.y = new Smooth(y, GLIDE_SPEED);
            this.presence = new Smooth(fresh ? 0.0f : 1.0f, FADE_SPEED);
        }

        private void aim(S2CLobbyRosterPacket.Member member, String nextTeam, float nextX, float nextY) {
            if (!member.name().equals(nameText)) {
                nameText = member.name();
                name = Component.literal(nameText);
            }
            if (!nextTeam.equals(team)) recolor(nextTeam);
            if (presence.get() <= GONE) {
                x.snap(nextX);
                y.snap(nextY);
            }
            targetX = nextX;
            targetY = nextY;
            wantsReady = member.ready();
            listed = true;
        }

        // WHY: плашка в полёте меняет цвет команды за время пути, а не кадром на вылете
        private void recolor(String nextTeam) {
            int color = MapRenderUtil.getTeamColor(nextTeam);
            fromTint = team == null ? color : UiTheme.mix(fromTint, toTint, tint.get());
            toTint = color;
            tint.snap(team == null ? 1.0f : 0.0f);
            team = nextTeam;
        }

        private void settle() {
            x.snap(targetX);
            y.snap(targetY);
        }

        private boolean advance(float delta) {
            x.to(targetX, delta);
            y.to(targetY, delta);
            tint.to(1.0f, delta);
            ready.to(wantsReady ? 1.0f : 0.0f, delta);
            float shown = presence.to(listed ? 1.0f : 0.0f, delta);
            return listed || shown > GONE;
        }
    }
}
