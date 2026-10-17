package com.persiki84.capturepoints.client;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.client.ClientGameData;
import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.minimap.client.MapRenderUtil;
import com.persiki84.shared.client.ui.Toggle;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiTicker;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Map;

public final class ObjectiveHud {
    private static final float BAR_HEIGHT = 15.0f;
    private static final float SCORE_WIDTH = 20.0f;
    private static final float CLOCK_WIDTH = 40.0f;
    private static final float DOT_RADIUS = 2.2f;
    private static final float SCORE_SCALE = 1.05f;
    private static final float CLOCK_SCALE = 0.85f;
    private static final long BANNER_MS = 4200L;
    private static final long VICTORY_MS = 7000L;
    private static final float BANNER_HEIGHT = 26.0f;
    private static final float BANNER_PADDING = 22.0f;
    private static final float BANNER_SCALE = 1.35f;
    private static final float BANNER_TRACKING = 1.2f;

    private static final Toggle barToggle = new Toggle(9.0f, 150L);
    private static final Board board = new Board();
    private static final Board sampleBoard = new Board();

    private static boolean finalOpen;
    private static long bannerAt;
    private static long bannerLife;
    private static Component bannerText;
    private static int bannerTint = UiPalette.alert();

    private static int heldMine;
    private static int heldTheirs;
    private static final int SAMPLE_POINTS = 3;
    private static final long SAMPLE_SECONDS = 275L;
    private static final int SAMPLE_MINE = 2;
    private static final int SAMPLE_THEIRS = 1;

    private static int heldTotal;
    private static int heldMineColor;
    private static int heldTheirsColor;
    private static long heldSeconds;

    private ObjectiveHud() {}

    private static int mineColor() {
        return heldMineColor == 0 ? UiAccent.color() : heldMineColor;
    }

    private static int theirsColor() {
        return heldTheirsColor == 0 ? UiAccent.faint() : heldTheirsColor;
    }

    public static void reset() {
        finalOpen = false;
    }

    public static void showVictory(String team) {
        bannerText = Component.translatable("capturepoints.hud.victory", team);
        bannerTint = UiTheme.muted(MapRenderUtil.getTeamColor(team), 0.15f);
        bannerLife = VICTORY_MS;
        bannerAt = System.currentTimeMillis();
        UiSound.alert();
    }

    public static float render(GuiGraphics graphics, Minecraft mc, float centerX, float top, float delta,
                               boolean wanted) {
        boolean active = !ClientGameData.isSoftDisabled()
                && ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.ACTIVE
                && mc.player != null && mc.player.getTeam() != null;

        float alpha = barToggle.update(active && wanted, delta);
        if (barToggle.live()) {
            measure(mc);
            trackFinal();
        }
        if (!active) {
            reset();
        }
        if (barToggle.cleared() && !active) {
            heldTotal = 0;
            heldSeconds = 0L;
        }
        if (alpha <= 0.01f || heldTotal <= 0) return 0.0f;

        float width = SCORE_WIDTH * 2.0f + CLOCK_WIDTH;
        float x = centerX - width / 2.0f;
        float y = top - (1.0f - UiAnim.easeOut(alpha)) * 6.0f;

        UiVital.card(graphics, x, y, width, BAR_HEIGHT, UiMetrics.radius(BAR_HEIGHT), alpha);
        renderScoreboard(graphics, mc, x, y, width, alpha, board);

        return y + BAR_HEIGHT;
    }

    public static void preview(GuiGraphics graphics, float centerX, float top, float alpha, float delta) {
        int total = heldTotal;
        long seconds = heldSeconds;
        int mine = heldMine;
        int theirs = heldTheirs;
        if (total <= 0) {
            heldTotal = SAMPLE_POINTS;
            heldSeconds = SAMPLE_SECONDS;
            heldMine = SAMPLE_MINE;
            heldTheirs = SAMPLE_THEIRS;
        }
        try {
            float width = SCORE_WIDTH * 2.0f + CLOCK_WIDTH;
            float x = centerX - width / 2.0f;
            HudLayout.sample(HudSlot.OBJECTIVE, width, BAR_HEIGHT);
            UiVital.card(graphics, x, top, width, BAR_HEIGHT, UiMetrics.radius(BAR_HEIGHT), alpha);
            renderScoreboard(graphics, mc(), x, top, width, alpha, sampleBoard);
        } finally {
            heldTotal = total;
            heldSeconds = seconds;
            heldMine = mine;
            heldTheirs = theirs;
        }
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static void renderScoreboard(GuiGraphics graphics, Minecraft mc, float x, float y, float width,
                                         float alpha, Board shown) {
        float scoreY = UiRender.centerY(y, BAR_HEIGHT, SCORE_SCALE);
        shown.mine.set(heldMine);
        shown.clock.set(heldSeconds, UiTicker.CLOCK);
        shown.theirs.set(heldTheirs);

        UiRender.dot(graphics, x + 6.5f, y + BAR_HEIGHT / 2.0f, DOT_RADIUS, UiTheme.alpha(mineColor(), alpha));
        shown.mine.draw(graphics, mc.font, x + SCORE_WIDTH - 2.5f, scoreY, SCORE_SCALE, HudInk.text(), alpha);
        shown.clock.draw(graphics, mc.font, x + width / 2.0f, UiRender.centerY(y, BAR_HEIGHT, CLOCK_SCALE),
                CLOCK_SCALE, HudInk.text(), alpha);
        shown.theirs.draw(graphics, mc.font, x + width - SCORE_WIDTH + 2.5f, scoreY, SCORE_SCALE, HudInk.text(),
                alpha);
        UiRender.dot(graphics, x + width - 6.5f, y + BAR_HEIGHT / 2.0f, DOT_RADIUS,
                UiTheme.alpha(theirsColor(), alpha));
    }

    public static void renderBanner(GuiGraphics graphics, Minecraft mc, float screenWidth, float screenHeight,
                                    float presence) {
        if (bannerText == null) return;

        long elapsed = System.currentTimeMillis() - bannerAt;
        if (elapsed > bannerLife) {
            bannerText = null;
            return;
        }

        float fade = Math.min(UiAnim.fadeIn(bannerAt, 280.0f), UiAnim.clamp01((bannerLife - elapsed) / 700.0f));
        float alpha = presence * fade;
        if (alpha <= 0.01f) return;

        float scale = UiScale.push(graphics);
        try {
            float logicalWidth = screenWidth / scale;
            float logicalHeight = screenHeight / scale;
            float width = UiRender.width(mc.font, bannerText) * BANNER_SCALE
                    + BANNER_TRACKING * bannerText.getString().length() + BANNER_PADDING * 2.0f;
            float x = (logicalWidth - width) / 2.0f;
            float y = logicalHeight * 0.3f - (1.0f - UiAnim.easeOut(alpha)) * 8.0f;
            float pulse = UiAnim.pulse(1400.0f, 0.55f, 1.0f);

            UiVital.cardTinted(graphics, x, y, width, BANNER_HEIGHT, UiMetrics.radius(BANNER_HEIGHT), alpha,
                    0.22f * pulse, UiTheme.withAlpha(bannerTint, 0.24f));
            UiRender.textTracked(graphics, mc.font, bannerText, logicalWidth / 2.0f,
                    UiRender.centerY(y, BANNER_HEIGHT, BANNER_SCALE), BANNER_SCALE, BANNER_TRACKING,
                    UiTheme.alpha(HudInk.text(), alpha));
        } finally {
            UiScale.pop(graphics);
        }
    }

    private static void measure(Minecraft mc) {
        String team = mc.player.getTeam().getName();
        Map<String, String> owners = ClientCaptureData.getAllPointOwners();

        int mine = 0;
        int theirs = 0;
        int counted = 0;
        String enemy = null;
        for (Map.Entry<String, String> point : owners.entrySet()) {
            if (!ClientCaptureData.isPointShownInHud(point.getKey())) continue;

            counted++;
            String owner = point.getValue();
            if (owner == null || owner.isEmpty()) continue;
            if (owner.equals(team)) {
                mine++;
            } else {
                theirs++;
                if (enemy == null) enemy = owner;
            }
        }

        heldMine = mine;
        heldTheirs = theirs;
        heldTotal = counted;
        heldMineColor = MapRenderUtil.getTeamColor(team);
        heldTheirsColor = enemy == null ? UiAccent.color() : MapRenderUtil.getTeamColor(enemy);

        heldSeconds = ClientGameData.getMatchElapsedMillis() / 1000L;
    }

    private static void trackFinal() {
        boolean open = ClientCaptureData.areAllPointsCapturedBySameTeam();
        if (open == finalOpen) return;

        finalOpen = open;
        if (open) {
            bannerText = Component.translatable("capturepoints.hud.final_unlocked");
            bannerTint = UiPalette.alert();
            bannerLife = BANNER_MS;
            bannerAt = System.currentTimeMillis();
            UiSound.alert();
        }
    }

    // WHY: счёт точек и часы матча меняют только сменившиеся цифры, как таймер острова: часы идут
    // WHY: только вперёд, поэтому новая цифра у них всегда снизу. У образца в редакторе свой набор
    private static final class Board {
        private final UiTicker mine = new UiTicker().align(UiTicker.CENTER);
        private final UiTicker clock = new UiTicker().align(UiTicker.CENTER).rising();
        private final UiTicker theirs = new UiTicker().align(UiTicker.CENTER);
    }
}
