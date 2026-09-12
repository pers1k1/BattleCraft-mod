package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.shared.client.ui.KeyLabel;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Toggle;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class ClientOverlayRenderer {
    private static final float VOTE_TRAVEL = 40.0f;
    private static final float LOBBY_TRAVEL = 7.0f;
    private static final float LOBBY_BAR_WIDTH = 150.0f;
    private static final float LOBBY_BAR_HEIGHT = 2.5f;
    private static final float LOBBY_PADDING = 8.0f;
    private static final float LOBBY_TEXT_BLOCK = 10.0f;
    private static final float LOBBY_BAR_BLOCK = 4.0f;
    private static final float LOBBY_BEAT_LIFT = 0.35f;
    private static final int LOBBY_URGENT_FROM = 5;
    private static final float MISSING_TRAVEL = 4.0f;

    private static final Toggle warningToggle = new Toggle(4.0f, 200L);
    private static final Toggle voteToggle = new Toggle(5.0f, 150L);
    private static final Toggle lobbyToggle = new Toggle(9.0f, 120L);
    private static final Toggle missingToggle = new Toggle(7.0f, 150L);
    private static final Smooth lobbyFill = new Smooth(12.0f);
    private static final Smooth lobbyWidth = new Smooth(14.0f);
    private static final Smooth lobbyHeight = new Smooth(14.0f);
    private static final Smooth lobbyBar = new Smooth(9.0f);
    private static final Smooth lobbyBeat = new Smooth(0.0f, 5.5f);

    private static int heldVoteRemaining;
    private static int heldVoteYes;
    private static int heldVoteRequired;
    private static String heldVoteTeam = "";
    private static float heldVoteProgress;

    private static final int SAMPLE_SECONDS = 15;
    private static final int SAMPLE_YES = 3;
    private static final int SAMPLE_REQUIRED = 5;
    private static final String SAMPLE_TEAM = "RED";

    private static int heldSeconds;
    private static float heldProgress;
    private static String heldMissing = "";
    private static boolean missingLive;
    private static boolean heldGrace;

    public static final IGuiOverlay HUD_OVERLAY = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        float scale = UiScale.push(guiGraphics);
        try {
            render(guiGraphics, mc, screenWidth / scale, screenHeight / scale);
        } finally {
            UiScale.pop(guiGraphics);
        }
    };

    private static void render(GuiGraphics guiGraphics, Minecraft mc, float screenWidth, float screenHeight) {
        float delta = UiFrame.delta();

        boolean lobby = !ClientGameData.isSoftDisabled() && ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.LOBBY;
        boolean needsTeam = lobby && mc.player.getTeam() == null;
        boolean needsReady = lobby && mc.player.getTeam() != null && !ClientGameData.isReady();

        float alpha = warningToggle.update(needsTeam || needsReady, delta);
        if (alpha > 0.01f && HudLayout.visible(HudSlot.WARNING)) {
            renderWarning(guiGraphics, mc, screenWidth, screenHeight, alpha, needsTeam);
        }

        float slide = voteToggle.update(ClientGameData.hasActiveVote(), delta);
        if (slide > 0.01f && HudLayout.visible(HudSlot.VOTE)) {
            renderVote(guiGraphics, mc, screenWidth, screenHeight, slide);
        }

        float countdown = lobbyToggle.update(lobbyWanted(), delta);
        trackLobby();
        if (countdown > 0.01f) {
            renderLobby(guiGraphics, mc, screenWidth, screenHeight, countdown, delta);
        }
    }

    private static boolean lobbyWanted() {
        if (ClientGameData.getCurrentPhase() != BattleCraftManager.GamePhase.LOBBY) return false;
        if (!HudLayout.visible(HudSlot.LOBBY)) return false;
        return ClientGameData.getInterpolatedLobbyTimer() > 0 || ClientGameData.getGraceSeconds() > 0;
    }

    private static void trackLobby() {
        if (lobbyToggle.cleared()) {
            heldSeconds = 0;
            heldProgress = 0.0f;
            heldMissing = "";
            missingLive = false;
            heldGrace = false;
            lobbyFill.snap(0.0f);
            return;
        }
        if (!lobbyToggle.live()) return;

        float timer = ClientGameData.getInterpolatedLobbyTimer();
        int maxTimer = ClientGameData.getLobbyMaxTimer();
        heldGrace = timer <= 0.0f;
        heldProgress = !heldGrace && maxTimer > 0 ? UiAnim.clamp01(timer / maxTimer) : heldProgress;
        trackMissing(heldGrace ? "" : ClientGameData.getMissingPlayers());
        trackSeconds(heldGrace ? ClientGameData.getGraceSeconds() : (int) Math.ceil(timer / 20.0f));
    }

    private static void trackMissing(String missing) {
        missingLive = missing != null && !missing.isEmpty();
        if (missingLive) heldMissing = missing;
    }

    private static void trackSeconds(int seconds) {
        if (seconds != heldSeconds) lobbyBeat.snap(1.0f);
        heldSeconds = seconds;
    }

    private static Component warningText(boolean needsTeam) {
        if (!needsTeam) return Component.translatable("battlecraft.warning.not_ready");

        int autoAssign = ClientGameData.getAutoAssignSeconds();
        return autoAssign > 0
                ? Component.translatable("battlecraft.warning.select_team_timed", autoAssign)
                : Component.translatable("battlecraft.warning.select_team");
    }

    private static void renderWarning(GuiGraphics graphics, Minecraft mc, float screenWidth, float screenHeight,
                                      float alpha, boolean needsTeam) {
        Component text = warningText(needsTeam);
        float width = UiRender.measure(graphics, mc.font, text, 1.0f) + 20.0f;
        float height = mc.font.lineHeight + 11.0f;
        HudBox box = HudLayout.place(HudSlot.WARNING, width, height, screenWidth, screenHeight);
        float x = box.x();
        float y = box.y() - (1.0f - UiAnim.easeOut(alpha)) * 6.0f;
        float shown = alpha * box.alpha();

        float pulse = UiAnim.pulse(1600.0f, 0.55f, 1.0f);
        HudLayout.push(graphics, box);
        try {
            UiVital.card(graphics, x, y, width, height, UiMetrics.radius(height), shown, pulse * 0.5f);
            UiRender.textCentered(graphics, mc.font, text, x + width / 2.0f, UiRender.centerY(y, height, 1.0f), 1.0f,
                    UiTheme.alpha(HudLayout.tint(HudSlot.WARNING, HudInk.text()), shown), false);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    private static void renderLobby(GuiGraphics graphics, Minecraft mc, float screenWidth, float screenHeight,
                                    float alpha, float delta) {
        Component text = lobbyText();
        float bar = lobbyBar.to(heldGrace ? 0.0f : 1.0f, delta);
        float width = lobbyWidth.to(lobbyWidth(graphics, mc, text, bar), delta);
        float height = lobbyHeight.to(mc.font.lineHeight + LOBBY_TEXT_BLOCK + 1.0f + LOBBY_BAR_BLOCK * bar, delta);

        HudBox box = HudLayout.place(HudSlot.LOBBY, width, height, screenWidth, screenHeight);
        float y = box.y() - (1.0f - UiAnim.easeOut(alpha)) * LOBBY_TRAVEL;
        float shown = alpha * box.alpha();
        float beat = lobbyBeat.to(0.0f, delta);

        HudLayout.push(graphics, box);
        try {
            UiVital.card(graphics, box.x(), y, width, height, UiMetrics.radius(height), shown, lobbyLift(beat));
            UiRender.textCentered(graphics, mc.font, text, box.centerX(),
                    UiRender.centerY(y, mc.font.lineHeight + LOBBY_TEXT_BLOCK, 1.0f), 1.0f,
                    UiTheme.alpha(HudLayout.tint(HudSlot.LOBBY, lobbyTextColor(bar)), shown), false);
            renderLobbyBar(graphics, mc, box.centerX(), y, shown * bar, delta);
            renderMissing(graphics, mc, box.centerX(), y + height + 2.0f, shown, delta);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    private static Component lobbyText() {
        return heldGrace
                ? Component.translatable("battlecraft.lobby.grace", heldSeconds)
                : Component.translatable("battlecraft.lobby.countdown", heldSeconds);
    }

    private static int lobbyTextColor(float bar) {
        return UiTheme.mix(HudInk.textDim(), HudInk.text(), bar);
    }

    private static float lobbyWidth(GuiGraphics graphics, Minecraft mc, Component text, float bar) {
        float content = Math.max(LOBBY_BAR_WIDTH * bar, UiRender.measure(graphics, mc.font, text, 1.0f));
        return content + LOBBY_PADDING * 2.0f;
    }

    private static float lobbyLift(float beat) {
        float urgent = !heldGrace && heldSeconds <= LOBBY_URGENT_FROM
                ? UiAnim.pulse(900.0f, 0.0f, 0.45f)
                : 0.0f;
        return Math.max(beat * LOBBY_BEAT_LIFT, urgent);
    }

    private static void renderLobbyBar(GuiGraphics graphics, Minecraft mc, float centerX, float y, float alpha,
                                       float delta) {
        float animated = lobbyFill.to(heldProgress, delta);
        if (alpha <= 0.01f) return;

        float barX = centerX - LOBBY_BAR_WIDTH / 2.0f;
        float barY = y + mc.font.lineHeight + LOBBY_TEXT_BLOCK - 3.0f;
        UiGlass.sunken(graphics, barX, barY, LOBBY_BAR_WIDTH, LOBBY_BAR_HEIGHT, LOBBY_BAR_HEIGHT / 2.0f, alpha);
        UiGlass.progress(graphics, barX, barY, LOBBY_BAR_WIDTH, LOBBY_BAR_HEIGHT, animated, UiAccent.color(), alpha);
    }

    private static void renderMissing(GuiGraphics graphics, Minecraft mc, float centerX, float top, float alpha,
                                      float delta) {
        float shown = missingToggle.update(missingLive, delta) * alpha;
        if (shown <= 0.01f || heldMissing.isEmpty()) return;

        Component text = Component.translatable("battlecraft.overlay.waiting", heldMissing);
        float width = UiRender.measure(graphics, mc.font, text, 0.85f) + 16.0f;
        float height = mc.font.lineHeight * 0.85f + 8.0f;
        float y = top + (1.0f - UiAnim.easeOut(shown)) * MISSING_TRAVEL;

        UiVital.card(graphics, centerX - width / 2.0f, y, width, height, shown);
        UiRender.textCentered(graphics, mc.font, text, centerX, UiRender.centerY(y, height, 0.85f), 0.85f,
                UiTheme.alpha(HudInk.textDim(), shown), false);
    }

    private static void trackVote() {
        if (voteToggle.cleared()) {
            heldVoteRemaining = 0;
            heldVoteYes = 0;
            heldVoteRequired = 0;
            heldVoteTeam = "";
            heldVoteProgress = 0.0f;
        }
        if (!voteToggle.live()) return;

        heldVoteRemaining = ClientGameData.getVoteRemainingSeconds();
        heldVoteYes = ClientGameData.getYesCount();
        heldVoteRequired = ClientGameData.getTotalRequired();
        heldVoteTeam = ClientGameData.getVoteTeam();
        heldVoteProgress = heldVoteRequired > 0 ? UiAnim.clamp01(heldVoteYes / (float) heldVoteRequired) : 0.0f;
    }

    public static void previewWarning(GuiGraphics graphics, HudBox box, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        Component text = warningText(true);
        float width = UiRender.measure(graphics, mc.font, text, 1.0f) + 20.0f;
        float height = mc.font.lineHeight + 11.0f;

        HudLayout.sample(HudSlot.WARNING, width, height);
        UiVital.card(graphics, box.x(), box.y(), width, height, UiMetrics.radius(height), alpha,
                UiAnim.pulse(1600.0f, 0.55f, 1.0f) * 0.5f);
        UiRender.textCentered(graphics, mc.font, text, box.x() + width / 2.0f,
                UiRender.centerY(box.y(), height, 1.0f), 1.0f,
                UiTheme.alpha(HudLayout.tint(HudSlot.WARNING, HudInk.text()), alpha), false);
    }

    public static void previewLobby(GuiGraphics graphics, HudBox box, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        int seconds = heldSeconds;
        boolean grace = heldGrace;
        if (seconds <= 0) {
            heldSeconds = SAMPLE_SECONDS;
            heldGrace = false;
        }
        try {
            paintLobbySample(graphics, mc, box, alpha);
        } finally {
            heldSeconds = seconds;
            heldGrace = grace;
        }
    }

    private static void paintLobbySample(GuiGraphics graphics, Minecraft mc, HudBox box, float alpha) {
        Component text = lobbyText();
        float width = lobbyWidth(graphics, mc, text, 1.0f);
        float height = mc.font.lineHeight + LOBBY_TEXT_BLOCK + 1.0f + LOBBY_BAR_BLOCK;

        HudLayout.sample(HudSlot.LOBBY, width, height);
        UiVital.card(graphics, box.x(), box.y(), width, height, UiMetrics.radius(height), alpha);
        UiRender.textCentered(graphics, mc.font, text, box.x() + width / 2.0f,
                UiRender.centerY(box.y(), mc.font.lineHeight + LOBBY_TEXT_BLOCK, 1.0f), 1.0f,
                UiTheme.alpha(HudLayout.tint(HudSlot.LOBBY, lobbyTextColor(1.0f)), alpha), false);
        renderLobbyBar(graphics, mc, box.x() + width / 2.0f, box.y(), alpha, UiFrame.delta());
    }

    public static void previewVote(GuiGraphics graphics, HudBox box, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        Component header = Component.translatable("battlecraft.vote.overlay.header", SAMPLE_TEAM);
        Component details = Component.translatable("battlecraft.vote.overlay.details",
                SAMPLE_YES, SAMPLE_REQUIRED, SAMPLE_SECONDS);
        Component keys = Component.translatable("battlecraft.vote.overlay.keys",
                KeyLabel.of(KeyInputHandler.VOTE_YES), KeyLabel.of(KeyInputHandler.VOTE_NO));

        float width = Math.max(UiRender.measure(graphics, mc.font, header, 1.0f),
                Math.max(UiRender.measure(graphics, mc.font, details, 1.0f),
                        UiRender.measure(graphics, mc.font, keys, 1.0f))) + 24.0f;
        float height = mc.font.lineHeight * 3 + 24.0f;
        HudLayout.sample(HudSlot.VOTE, width, height);
        drawVote(graphics, mc, box.x(), box.y(), width, height, alpha, header, details, keys);
    }

    private static void renderVote(GuiGraphics graphics, Minecraft mc, float screenWidth, float screenHeight, float slide) {
        trackVote();

        Component header = Component.translatable("battlecraft.vote.overlay.header", heldVoteTeam);
        Component details = Component.translatable("battlecraft.vote.overlay.details",
                heldVoteYes, heldVoteRequired, heldVoteRemaining);
        Component keys = Component.translatable("battlecraft.vote.overlay.keys",
                KeyLabel.of(KeyInputHandler.VOTE_YES), KeyLabel.of(KeyInputHandler.VOTE_NO));

        float width = Math.max(UiRender.measure(graphics, mc.font, header, 1.0f),
                Math.max(UiRender.measure(graphics, mc.font, details, 1.0f),
                        UiRender.measure(graphics, mc.font, keys, 1.0f))) + 24.0f;
        float height = mc.font.lineHeight * 3 + 24.0f;

        HudBox box = HudLayout.place(HudSlot.VOTE, width, height, screenWidth, screenHeight);
        float travel = (1.0f - UiAnim.easeOut(slide)) * HudLayout.slideX(HudSlot.VOTE, VOTE_TRAVEL);

        HudLayout.push(graphics, box);
        try {
            drawVote(graphics, mc, box.x() + travel, box.y(), width, height, slide * box.alpha(),
                    header, details, keys);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    private static void drawVote(GuiGraphics graphics, Minecraft mc, float x, float y, float width, float height,
                                 float shown, Component header, Component details, Component keys) {
        UiVital.card(graphics, x, y, width, height, UiMetrics.radius(height), shown);

        UiRender.textScaled(graphics, mc.font, header, x + 12.0f, y + 8.0f, 1.0f,
                UiTheme.alpha(HudLayout.tint(HudSlot.VOTE, HudInk.text()), shown), false);
        UiRender.textScaled(graphics, mc.font, details, x + 12.0f, y + 10.0f + mc.font.lineHeight, 1.0f,
                UiTheme.alpha(HudInk.textDim(), shown), false);
        UiRender.textScaled(graphics, mc.font, keys, x + 12.0f, y + 12.0f + mc.font.lineHeight * 2, 1.0f,
                UiTheme.alpha(HudInk.textFaint(), shown), false);

        UiGlass.sunken(graphics, x + 12.0f, y + height - 7.0f, width - 24.0f, 2.5f, 1.25f, shown);
        UiGlass.progress(graphics, x + 12.0f, y + height - 7.0f, width - 24.0f, 2.5f, heldVoteProgress,
                UiAccent.color(), shown);
    }
}
