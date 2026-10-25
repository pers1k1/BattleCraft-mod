package com.persiki84.battlecraft.client.menu;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.client.ClientGameData;
import com.persiki84.battlecraft.client.ClientLobbyData;
import com.persiki84.battlecraft.client.ClientMatchMode;
import com.persiki84.battlecraft.mode.ModeId;
import com.persiki84.battlecraft.client.menu.lobby.LobbyColumn;
import com.persiki84.battlecraft.client.menu.lobby.LobbyFigures;
import com.persiki84.battlecraft.client.menu.lobby.LobbyGate;
import com.persiki84.battlecraft.client.menu.lobby.LobbyLayout;
import com.persiki84.battlecraft.client.menu.lobby.LobbyModeBand;
import com.persiki84.battlecraft.client.menu.lobby.LobbyStatus;
import com.persiki84.battlecraft.client.menu.lobby.LobbyText;
import com.persiki84.battlecraft.client.menu.lobby.LobbyTiles;
import com.persiki84.battlecraft.network.S2CLobbyRosterPacket;
import com.persiki84.shared.client.menu.GlassScreen;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTitle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class TeamSelectScreen extends GlassScreen {
    private static final float TITLE_TOP = 12.0f;
    private static final float TITLE_SCALE = 0.9f;
    private static final float TITLE_TRACKING = 2.0f;
    private static final float STATUS_Y = 48.0f;
    private static final float COLUMNS_TOP = STATUS_Y + LobbyStatus.HEIGHT + 8.0f;
    private static final int READY_WIDTH = 168;
    private static final int BUTTON_HEIGHT = 20;
    private static final float UNDECIDED_LIFT = 16.0f;
    private static final float BUTTON_LIFT = 52.0f;
    private static final float SUMMARY_GAP = 13.0f;
    private static final float FEEDBACK_ROOM = 30.0f;
    private static final float FEEDBACK_GAP = 6.0f;
    private static final float SUMMARY_SCALE = 0.85f;
    private static final float UNDECIDED_SCALE = 0.85f;
    private static final float LINE_HEIGHT = 10.0f;

    private final List<LobbyColumn> columns = new ArrayList<>();
    private final LobbyLayout layout = new LobbyLayout();
    private final LobbyTiles tiles = new LobbyTiles();
    private final LobbyStatus status = new LobbyStatus();
    private final LobbyText summary = new LobbyText();
    private final LobbyText undecided = new LobbyText();
    private final LobbyModeBand modes = new LobbyModeBand();
    private int hoveredMode = -1;
    private UiButton readyButton;
    private boolean readyShown;
    private int readyCooldownShown = -1;
    private int undecidedVersion = -1;
    private int hovered = -1;

    public TeamSelectScreen() {
        super(Component.translatable("battlecraft.lobby.title"));
    }

    @Override
    protected void init() {
        readyShown = ClientLobbyData.ownReady();
        readyCooldownShown = ClientGameData.getReadyCooldownSeconds();
        readyButton = new UiButton((this.width - READY_WIDTH) / 2, (int) buttonTop(), READY_WIDTH, BUTTON_HEIGHT,
                readyLabel(readyCooldownShown), pressed -> toggleReady());
        addRenderableWidget(readyButton);
        syncColumns();
        refreshState();
    }

    private float buttonTop() {
        return this.height - BUTTON_LIFT;
    }

    private float summaryY() {
        return buttonTop() - SUMMARY_GAP;
    }

    @Override
    public void tick() {
        if (leaving()) return;
        if (closesOnMatchStart()) {
            onClose();
            return;
        }
        syncColumns();
        refreshState();
    }

    private boolean closesOnMatchStart() {
        if (ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.LOBBY) return false;
        return ClientLobbyData.ownTeam() != null || ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.ENDED;
    }

    private void syncColumns() {
        List<String> teams = ClientLobbyData.teams();
        if (sameTeams(teams)) return;

        columns.clear();
        for (int index = 0; index < teams.size(); index++) columns.add(new LobbyColumn(teams.get(index)));
        hovered = -1;
    }

    private boolean sameTeams(List<String> teams) {
        if (teams.size() != columns.size()) return false;
        for (int index = 0; index < teams.size(); index++) {
            if (!teams.get(index).equals(columns.get(index).team())) return false;
        }
        return true;
    }

    private void refreshState() {
        for (int index = 0; index < columns.size(); index++) columns.get(index).refresh();
        summary.set("battlecraft.lobby.ready_count", ClientLobbyData.readyCount(), ClientLobbyData.seatedCount());
        refreshReady();
    }

    private void refreshReady() {
        if (readyButton == null) return;

        int readyCooldown = ClientGameData.getReadyCooldownSeconds();
        boolean seated = ClientLobbyData.ownTeam() != null;
        boolean lobby = ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.LOBBY;
        readyButton.active = seated && lobby && readyCooldown <= 0;

        boolean ready = ClientLobbyData.ownReady();
        if (ready == readyShown && readyCooldown == readyCooldownShown) return;
        readyShown = ready;
        readyCooldownShown = readyCooldown;
        readyButton.setMessage(readyLabel(readyCooldown));
    }

    private Component readyLabel(int cooldown) {
        if (cooldown > 0) return Component.translatable("battlecraft.lobby.ready_in", cooldown);
        return ClientLobbyData.ownReady()
                ? Component.translatable("battlecraft.lobby.not_ready")
                : Component.translatable("battlecraft.lobby.ready");
    }

    private void toggleReady() {
        if (minecraft == null || minecraft.getConnection() == null) return;
        minecraft.getConnection().sendCommand("battlecraft ready");
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float delta = UiFrame.delta();
        float centerX = this.width / 2.0f;
        UiTitle.title(graphics, this.font, getTitle(), centerX, TITLE_TOP, TITLE_SCALE, TITLE_TRACKING,
                UiAccent.text());
        status.render(graphics, this.font, centerX, STATUS_Y, delta);
        if (modes.shown()) {
            pointMode(modes.at(mouseX, mouseY));
            modes.render(graphics, this.font, centerX, buttonTop(), READY_WIDTH, hoveredMode, delta);
        }

        measure();
        tiles.sync(layout);
        point(layout.columnAt(mouseX, mouseY));
        for (int index = 0; index < columns.size(); index++) {
            columns.get(index).render(graphics, this.font, layout, index, index == hovered, delta);
        }
        tiles.render(graphics, this.font, layout.tileWidth(), delta);

        renderSummary(graphics, centerX);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        MenuFeedback.render(graphics, centerX, columns.isEmpty() ? COLUMNS_TOP : layout.bottom() + FEEDBACK_GAP);
        renderUndecided(graphics, centerX);
    }

    private void measure() {
        float room = summaryY() - FEEDBACK_ROOM - COLUMNS_TOP;
        int wanted = Math.max(ClientLobbyData.slotsPerTeam(), fullestTeam());
        layout.measure(this.width, COLUMNS_TOP, room, columns.size(), Math.max(1, wanted));
    }

    private int fullestTeam() {
        int most = 0;
        for (int index = 0; index < columns.size(); index++) {
            most = Math.max(most, ClientLobbyData.sizeOf(columns.get(index).team()));
        }
        return most;
    }

    private void point(int index) {
        if (index == hovered) return;
        hovered = index;
        if (index >= 0 && index < columns.size() && columns.get(index).gate().admits()) UiSound.hover();
    }

    private void pointMode(int index) {
        if (index == hoveredMode) return;
        hoveredMode = index;
        if (modes.votable(index)) UiSound.hover();
    }

    private void renderSummary(GuiGraphics graphics, float centerX) {
        if (ClientLobbyData.seatedCount() <= 0) return;
        LobbyFigures.line(graphics, this.font, summary, centerX, summaryY(), SUMMARY_SCALE,
                UiAccent.textDim(), LobbyFigures.CENTER);
    }

    private void renderUndecided(GuiGraphics graphics, float centerX) {
        List<S2CLobbyRosterPacket.Member> loose = ClientLobbyData.undecided();
        if (loose.isEmpty() || ClientLobbyData.ownTeam() == null) return;

        if (undecidedVersion != ClientLobbyData.version()) {
            undecidedVersion = ClientLobbyData.version();
            undecided.set("battlecraft.lobby.undecided", Component.translatable("battlecraft.lobby.undecided",
                    names(loose)));
        }
        float width = this.width - UiMetrics.MARGIN_WIDE * 2.0f;
        UiRender.textTrackedBox(graphics, this.font, undecided.current(), centerX - width / 2.0f,
                this.height - UNDECIDED_LIFT - LINE_HEIGHT / 2.0f, LINE_HEIGHT, width, UNDECIDED_SCALE, 0.0f,
                UiAccent.textDim(), false, 0.5f);
    }

    private static String names(List<S2CLobbyRosterPacket.Member> members) {
        StringBuilder names = new StringBuilder();
        for (S2CLobbyRosterPacket.Member member : members) {
            if (names.length() > 0) names.append(", ");
            names.append(member.name());
        }
        return names.toString();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (leaving() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        double localX = localX(mouseX, mouseY);
        double localY = localY(mouseX, mouseY);
        int mode = modes.shown() ? modes.at(localX, localY) : -1;
        if (mode >= 0) {
            vote(mode);
            return true;
        }
        int index = layout.columnAt(localX, localY);
        if (index < 0 || index >= columns.size()) return false;
        choose(columns.get(index));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;

        int index = keyCode - GLFW.GLFW_KEY_1;
        if (leaving() || index < 0 || index >= Math.min(9, columns.size())) return false;
        choose(columns.get(index));
        return true;
    }

    private void choose(LobbyColumn column) {
        column.refresh();
        LobbyGate gate = column.gate();
        if (gate == LobbyGate.MINE) return;
        if (!gate.admits()) {
            UiSound.deny();
            column.refused();
            MenuFeedback.show(column.reason(), true);
            return;
        }
        if (minecraft == null || minecraft.getConnection() == null) return;
        UiSound.press();
        column.pressed();
        minecraft.getConnection().sendCommand("battlecraft select_team " + column.team());
    }

    private void vote(int index) {
        if (!modes.votable(index)) {
            UiSound.deny();
            MenuFeedback.show(modes.lockedReason(), true);
            return;
        }
        ModeId mode = modes.mode(index);
        if (mode == ClientMatchMode.mine() || minecraft == null || minecraft.getConnection() == null) return;
        UiSound.press();
        minecraft.getConnection().sendCommand("battlecraft mode vote " + mode.id());
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new TeamSelectScreen());
    }
}
