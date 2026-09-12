package com.persiki84.battlecraft.client.menu;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.client.ClientGameData;
import com.persiki84.battlecraft.client.ClientLobbyData;
import com.persiki84.battlecraft.network.S2CLobbyRosterPacket;
import com.persiki84.minimap.client.MapRenderUtil;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTitle;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class TeamSelectScreen extends Screen {
    private static final int COLUMN_MAX_WIDTH = 210;
    private static final int COLUMN_MIN_WIDTH = 96;
    private static final int COLUMN_TIGHT_WIDTH = 58;
    private static final float NAME_MIN_WIDTH = 10.0f;
    private static final int COLUMN_GAP = 14;
    private static final int COLUMN_TOP = 54;
    private static final int COLUMN_MIN_HEIGHT = 96;
    private static final int SIDE_MARGIN = 18;
    private static final int READY_WIDTH = 168;
    private static final int HEAD_SIZE = 16;
    private static final int BUTTON_HEIGHT = 20;
    private static final float TILE_HEIGHT = 25.0f;
    private static final float TILE_GAP = 4.0f;
    private static final float TILE_INSET = 5.0f;
    private static final float TILE_PADDING = 4.5f;
    private static final float ROW_HEIGHT = TILE_HEIGHT + TILE_GAP;
    private static final float HEADING_HEIGHT = 13.0f;
    private static final float NAME_ROW = 12.0f;
    private static final float STATUS_ROW = 9.0f;
    private static final float DOT_RADIUS = 3.2f;
    private static final float COLUMN_RADIUS = 20.0f;
    private static final float TITLE_TOP = 20.0f;
    private static final float TITLE_TRACKING = 2.0f;
    private static final float TITLE_SCALE = 1.05f;
    private static final float LABEL_SCALE = 1.0f;
    private static final float NAME_SCALE = 1.0f;
    private static final float STATUS_SCALE = 0.8f;

    private final List<TeamChoice> choices = new ArrayList<>();
    private UiButton readyButton;
    private boolean readyShown;
    private int readyCooldownShown = -1;
    private int columnWidth = COLUMN_MAX_WIDTH;
    private int columnHeight = COLUMN_MIN_HEIGHT;
    private int columnsLeft;

    public TeamSelectScreen() {
        super(Component.translatable("battlecraft.lobby.title"));
    }

    @Override
    protected void init() {
        choices.clear();

        List<String> teams = ClientLobbyData.teams();
        if (teams.isEmpty()) return;

        measureColumns(teams.size());
        int buttonY = COLUMN_TOP + columnHeight + (int) UiMetrics.GAP;

        for (int index = 0; index < teams.size(); index++) {
            String team = teams.get(index);
            UiButton button = new UiButton(columnX(index), buttonY, columnWidth, BUTTON_HEIGHT,
                    Component.translatable("battlecraft.lobby.join", team), pressed -> joinTeam(team));
            choices.add(new TeamChoice(team, button));
            addRenderableWidget(button);
        }

        int readyY = buttonY + BUTTON_HEIGHT + (int) UiMetrics.GAP_WIDE * 2;
        readyButton = new UiButton((this.width - READY_WIDTH) / 2, readyY, READY_WIDTH, BUTTON_HEIGHT,
                readyLabel(ClientGameData.getReadyCooldownSeconds()), pressed -> toggleReady());
        addRenderableWidget(readyButton);
        refreshWidgets();
    }

    private void measureColumns(int count) {
        int available = this.width - SIDE_MARGIN * 2 - COLUMN_GAP * (count - 1);
        int fit = Math.max(1, available / count);
        columnWidth = fit >= COLUMN_MIN_WIDTH ? Math.min(COLUMN_MAX_WIDTH, fit) : Math.max(COLUMN_TIGHT_WIDTH, fit);

        int reserved = COLUMN_TOP + BUTTON_HEIGHT * 2 + (int) UiMetrics.GAP_WIDE * 3 + (int) UiMetrics.MARGIN_WIDE * 2;
        int room = Math.max(COLUMN_MIN_HEIGHT, this.height - reserved);
        columnHeight = Math.min(room, wantedHeight());

        int totalWidth = count * columnWidth + (count - 1) * COLUMN_GAP;
        columnsLeft = Math.max(SIDE_MARGIN, (this.width - totalWidth) / 2);
    }

    private int wantedHeight() {
        int rows = Math.max(ClientLobbyData.slotsPerTeam(), fullestTeam());
        float content = HEADING_HEIGHT + UiMetrics.GAP + Math.max(1, rows) * ROW_HEIGHT - TILE_GAP;
        return Math.max(COLUMN_MIN_HEIGHT, Math.round(content + UiMetrics.PAD * 2.0f));
    }

    private int fullestTeam() {
        int most = 0;
        for (String team : ClientLobbyData.teams()) {
            most = Math.max(most, ClientLobbyData.membersOf(team).size());
        }
        return most;
    }

    private int columnX(int index) {
        return columnsLeft + index * (columnWidth + COLUMN_GAP);
    }

    private int visibleRows() {
        return Math.max(1, (int) ((columnHeight - UiMetrics.PAD * 2.0f - HEADING_HEIGHT - UiMetrics.GAP) / ROW_HEIGHT));
    }

    @Override
    public void tick() {
        if (closesOnMatchStart()) {
            onClose();
            return;
        }
        if (choices.size() != ClientLobbyData.teams().size()) {
            rebuildChoices();
            return;
        }
        refreshWidgets();
    }

    private boolean closesOnMatchStart() {
        if (ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.LOBBY) return false;
        return ClientLobbyData.ownTeam() != null || ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.ENDED;
    }

    private void rebuildChoices() {
        clearWidgets();
        init();
    }

    private void refreshWidgets() {
        String own = ClientLobbyData.ownTeam();
        int cooldown = own == null ? 0 : ClientGameData.getSwitchCooldownSeconds();

        for (TeamChoice choice : choices) {
            choice.update(choice.team.equals(own), cooldown);
        }

        if (readyButton == null) return;

        int readyCooldown = ClientGameData.getReadyCooldownSeconds();
        readyButton.active = own != null && readyCooldown <= 0;

        boolean ready = ClientLobbyData.ownReady();
        if (ready != readyShown || readyCooldown != readyCooldownShown) {
            readyShown = ready;
            readyCooldownShown = readyCooldown;
            readyButton.setMessage(readyLabel(readyCooldown));
        }
    }

    private static Component joinLabel(String team, boolean mine, int cooldown) {
        if (mine) return Component.translatable("battlecraft.lobby.joined", team);
        if (cooldown > 0) return Component.translatable("battlecraft.lobby.switch_in", cooldown);
        return Component.translatable("battlecraft.lobby.join", team);
    }

    private Component readyLabel(int cooldown) {
        if (cooldown > 0) return Component.translatable("battlecraft.lobby.ready_in", cooldown);
        return ClientLobbyData.ownReady()
                ? Component.translatable("battlecraft.lobby.not_ready")
                : Component.translatable("battlecraft.lobby.ready");
    }

    private void joinTeam(String team) {
        if (minecraft == null || minecraft.getConnection() == null) return;
        minecraft.getConnection().sendCommand("battlecraft select_team " + team);
    }

    private void toggleReady() {
        if (minecraft == null || minecraft.getConnection() == null) return;
        minecraft.getConnection().sendCommand("battlecraft ready");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.flush();

        UiTitle.title(graphics, this.font, getTitle(), this.width / 2.0f, TITLE_TOP,
                TITLE_SCALE, TITLE_TRACKING, UiAccent.text());
        renderStatus(graphics);

        List<String> teams = ClientLobbyData.teams();
        for (int index = 0; index < teams.size(); index++) {
            renderColumn(graphics, teams.get(index), columnX(index));
        }

        renderUndecided(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderStatus(GuiGraphics graphics) {
        Component status = statusLine();
        if (status == null) return;

        UiRender.emphasisCentered(graphics, this.font, status, this.width / 2.0f,
                COLUMN_TOP - this.font.lineHeight - UiMetrics.GAP_WIDE, LABEL_SCALE, UiAccent.textDim());
    }

    private Component statusLine() {
        int start = (int) Math.ceil(ClientGameData.getInterpolatedLobbyTimer() / 20.0f);
        if (start > 0) return Component.translatable("battlecraft.lobby.starts_in", start);

        if (ClientLobbyData.ownTeam() == null) {
            int auto = ClientGameData.getAutoAssignSeconds();
            if (auto > 0) return Component.translatable("battlecraft.lobby.auto_in", auto);
            return Component.translatable("battlecraft.lobby.pick_any");
        }

        int grace = ClientGameData.getGraceSeconds();
        if (grace > 0) return Component.translatable("battlecraft.lobby.grace", grace);

        int cooldown = ClientGameData.getSwitchCooldownSeconds();
        if (cooldown > 0) return Component.translatable("battlecraft.lobby.switch_in", cooldown);
        return Component.translatable("battlecraft.lobby.waiting_ready");
    }

    private void renderColumn(GuiGraphics graphics, String team, int columnX) {
        List<S2CLobbyRosterPacket.Member> members = ClientLobbyData.membersOf(team);
        boolean own = team.equals(ClientLobbyData.ownTeam());
        int accent = MapRenderUtil.getTeamColor(team);

        UiGlass.tinted(graphics, columnX, COLUMN_TOP, columnWidth, columnHeight, COLUMN_RADIUS,
                1.0f, own ? 0.34f : 0.0f, UiTheme.withAlpha(accent, own ? 0.38f : 0.10f));
        renderHeading(graphics, team, accent, columnX, members.size());

        float rowY = COLUMN_TOP + UiMetrics.PAD + HEADING_HEIGHT + UiMetrics.GAP;
        int rows = Math.min(members.size(), visibleRows());
        for (int index = 0; index < rows; index++) {
            renderMember(graphics, members.get(index), accent, columnX, rowY);
            rowY += ROW_HEIGHT;
        }

        int hidden = members.size() - rows;
        if (hidden > 0) {
            UiRender.textCentered(graphics, this.font, Component.translatable("battlecraft.lobby.more", hidden),
                    columnX + columnWidth / 2.0f, rowY + UiMetrics.GAP_TIGHT, STATUS_SCALE, UiAccent.textFaint(), false);
        }
    }

    private void renderHeading(GuiGraphics graphics, String team, int accent, int columnX, int taken) {
        float left = columnX + TILE_INSET + TILE_PADDING;
        float right = columnX + columnWidth - TILE_INSET - TILE_PADDING;
        float top = COLUMN_TOP + UiMetrics.PAD;

        Component slots = Component.translatable("battlecraft.lobby.slots", taken, ClientLobbyData.slotsPerTeam());
        float slotsWidth = UiRender.width(this.font, slots) * STATUS_SCALE;
        UiRender.textRight(graphics, this.font, slots, right,
                UiRender.centerY(top, HEADING_HEIGHT, STATUS_SCALE), STATUS_SCALE, UiAccent.textFaint(), false);

        float nameWidth = right - left - slotsWidth - UiMetrics.GAP;
        if (nameWidth < NAME_MIN_WIDTH) return;

        UiRender.textTrackedBox(graphics, this.font, Component.literal(team), left, top,
                HEADING_HEIGHT, nameWidth, LABEL_SCALE, 0.0f, UiTheme.mix(accent, UiTheme.WHITE, 0.35f), true, 0.0f);
    }

    private void renderMember(GuiGraphics graphics, S2CLobbyRosterPacket.Member member, int accent, int columnX, float rowY) {
        float tileX = columnX + TILE_INSET;
        float tileWidth = columnWidth - TILE_INSET * 2.0f;
        float radius = UiMetrics.radius(TILE_HEIGHT);
        boolean self = isSelf(member);

        if (self) {
            UiGlass.tinted(graphics, tileX, rowY, tileWidth, TILE_HEIGHT, radius, 1.0f, 0.38f,
                    UiTheme.withAlpha(accent, 0.34f));
        } else {
            UiGlass.sunken(graphics, tileX, rowY, tileWidth, TILE_HEIGHT, radius, 0.72f);
        }

        renderHead(graphics, member, tileX + TILE_PADDING, rowY + (TILE_HEIGHT - HEAD_SIZE) / 2.0f);
        renderReady(graphics, member, accent, tileX + tileWidth - TILE_PADDING - DOT_RADIUS, rowY + TILE_HEIGHT / 2.0f);
        renderIdentity(graphics, member, accent, tileX, tileWidth, rowY);
    }

    private void renderHead(GuiGraphics graphics, S2CLobbyRosterPacket.Member member, float x, float y) {
        UiRender.panel(graphics, x - 1.5f, y - 1.5f, HEAD_SIZE + 3.0f, HEAD_SIZE + 3.0f, 6.0f,
                UiTheme.withAlpha(0x000000, 0.34f));

        ResourceLocation skin = skinOf(member);
        if (skin == null) return;

        PlayerFaceRenderer.draw(graphics, skin, Math.round(x), Math.round(y), HEAD_SIZE);
    }

    private void renderIdentity(GuiGraphics graphics, S2CLobbyRosterPacket.Member member, int accent,
                                float tileX, float tileWidth, float rowY) {
        float textLeft = tileX + TILE_PADDING + HEAD_SIZE + UiMetrics.GAP;
        float textWidth = tileX + tileWidth - TILE_PADDING - DOT_RADIUS * 2.0f - UiMetrics.GAP - textLeft;
        if (textWidth < NAME_MIN_WIDTH) return;

        float top = rowY + (TILE_HEIGHT - NAME_ROW - STATUS_ROW) / 2.0f;
        UiRender.textTrackedBox(graphics, this.font, Component.literal(member.name()), textLeft, top,
                NAME_ROW, textWidth, NAME_SCALE, 0.0f, UiAccent.text(), false, 0.0f);

        Component status = Component.translatable(member.ready()
                ? "battlecraft.lobby.member_ready" : "battlecraft.lobby.member_waiting");
        int tone = member.ready() ? UiTheme.mix(accent, UiTheme.WHITE, 0.45f) : UiAccent.textFaint();
        UiRender.textTrackedBox(graphics, this.font, status, textLeft, top + NAME_ROW,
                STATUS_ROW, textWidth, STATUS_SCALE, 0.0f, tone, false, 0.0f);
    }

    private void renderReady(GuiGraphics graphics, S2CLobbyRosterPacket.Member member, int accent,
                             float centerX, float centerY) {
        if (!member.ready()) {
            UiRender.ring(graphics, centerX, centerY, DOT_RADIUS, 1.1f, 1.0f,
                    UiTheme.withAlpha(UiTheme.WHITE, 0.24f));
            return;
        }

        UiRender.dot(graphics, centerX, centerY, DOT_RADIUS * 2.0f, UiTheme.withAlpha(accent, 0.18f));
        UiRender.dot(graphics, centerX, centerY, DOT_RADIUS, UiTheme.mix(accent, UiTheme.WHITE, 0.25f));
    }

    private boolean isSelf(S2CLobbyRosterPacket.Member member) {
        return minecraft != null && minecraft.player != null && member.uuid().equals(minecraft.player.getUUID());
    }

    private ResourceLocation skinOf(S2CLobbyRosterPacket.Member member) {
        if (minecraft == null || minecraft.getConnection() == null) return null;

        PlayerInfo info = minecraft.getConnection().getPlayerInfo(member.uuid());
        return info == null ? null : info.getSkinLocation();
    }

    private void renderUndecided(GuiGraphics graphics) {
        List<S2CLobbyRosterPacket.Member> undecided = ClientLobbyData.undecided();
        if (undecided.isEmpty() || ClientLobbyData.ownTeam() == null) return;

        StringBuilder names = new StringBuilder();
        for (S2CLobbyRosterPacket.Member member : undecided) {
            if (names.length() > 0) names.append(", ");
            names.append(member.name());
        }

        Component notice = Component.translatable("battlecraft.lobby.undecided", names.toString());
        UiRender.textCentered(graphics, this.font, notice, this.width / 2.0f,
                this.height - UiMetrics.MARGIN_WIDE * 2.0f, NAME_SCALE, UiAccent.textDim(), false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new TeamSelectScreen());
    }

    private static final class TeamChoice {
        private final String team;
        private final UiButton button;
        private boolean mine;
        private int cooldown = -1;

        private TeamChoice(String team, UiButton button) {
            this.team = team;
            this.button = button;
        }

        private void update(boolean joined, int remaining) {
            if (joined == mine && remaining == cooldown) return;

            mine = joined;
            cooldown = remaining;
            button.active = !joined && remaining <= 0;
            button.setMessage(joinLabel(team, joined, remaining));
        }
    }
}
