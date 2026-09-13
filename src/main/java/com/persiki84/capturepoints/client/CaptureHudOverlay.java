package com.persiki84.capturepoints.client;

import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.shared.client.ui.TopStack;
import com.persiki84.battlecraft.client.hud.BossBarHud;
import com.persiki84.minimap.client.MapRenderUtil;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Toggle;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiHud;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiTagStack;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiWorldTag;
import com.persiki84.shared.client.ui.UiVital;
import com.persiki84.battlecraft.client.ClientModules;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.battlecraft.modules.ModuleId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

public class CaptureHudOverlay {
    private static final float PILL_HEIGHT = 13.0f;
    private static final float PILL_GAP = 5.0f;
    private static final float PILL_RADIUS = 4.0f;
    private static final float PILL_PADDING = 7.0f;
    private static final float LABEL_SCALE = 0.8f;
    private static final long OWNER_FLASH_MS = 3500L;
    private static final long CAPTURE_TIMEOUT_MS = 1200L;
    private static final float OBJECTIVE_WIDTH = 240.0f;
    private static final float OBJECTIVE_HEIGHT = 46.0f;
    private static final float CENTRAL_WIDTH = 110.0f;
    private static final float CENTRAL_LABEL_SCALE = 0.8f;
    private static final float CENTRAL_PADDING = UiMetrics.PAD;
    private static final float CENTRAL_BAR_HEIGHT = UiMetrics.BAR_HEIGHT;
    private static final float HOVER_NEAR = 14.0f;
    private static final float HOVER_FAR = 46.0f;
    private static final float ROW_GAP = 5.0f;
    private static final float DOT_COLUMN = 7.0f;
    private static final float MARKER_DOT = 1.6f;
    private static final float MARKER_TAG_GAP = 4.5f;
    private static final float RANGE_FADE_SPEED = 6.0f;
    private static final double RANGE_SHOWN_FROM = 24.0;
    private static final Map<String, MarkerState> markerStates = new HashMap<>();
    private static final float MARKER_LABEL_SCALE = 0.75f;
    private static final float PRESENCE_SPEED = 8.0f;
    private static final float MARKER_GONE = 0.01f;
    private static final float STACK_GAP = 5.0f;
    private static final float SAMPLE_PROGRESS = 0.62f;
    private static final String STATE_ARMING = "capturepoints.hud.state.arming";
    private static final String STATE_HELD = "capturepoints.hud.state.held";
    private static final String STATE_STALLED = "capturepoints.hud.state.stalled";
    private static final String STATE_CONTEST = "capturepoints.hud.state.contest";
    private static final String STATE_ROLLBACK = "capturepoints.hud.state.rollback";
    private static final String STATE_FINAL = "capturepoints.hud.state.final";

    private static final Smooth dotPresence = new Smooth(0.0f, PRESENCE_SPEED);
    private static final Map<String, Pill> pills = new LinkedHashMap<>();
    private static final Toggle centralToggle = new Toggle(11.0f, 120L);
    private static final Smooth centralProgress = new Smooth(14.0f);
    private static String centralPoint;
    private static boolean centralFinal;
    private static float centralValue;
    private static boolean centralLive;
    private static Component centralCached;
    private static Component centralCachedTag = Component.empty();
    private static int centralCachedTint;
    private static final CaptureState centralState = new CaptureState();
    private static final CaptureState sampleState = new CaptureState();

    // WHY: под открытым списком игроков метки точек уходят присутствием, как метки зон и баз:
    // WHY: ранний выход снимал их кадром, и на фоне уезжающих чужих меток это читалось как дефект
    public static final IGuiOverlay HUD_CAPTURE = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        if (com.persiki84.battlecraft.client.ClientGameData.isSoftDisabled()) return;
        if (!ClientModules.allows(ModuleId.CAPTURE_POINTS)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null || mc.options.hideGui) return;

        boolean wanted = !UiHud.rosterOpen();
        renderProjectedMarkers(guiGraphics, mc, screenWidth / 2.0f, screenHeight / 2.0f, wanted);
        ObjectiveHud.renderBanner(guiGraphics, mc, screenWidth, screenHeight, UiAnim.easeOut(dotPresence.get()));

        boolean matchRunning = com.persiki84.battlecraft.client.ClientGameData.getCurrentPhase()
                == com.persiki84.battlecraft.BattleCraftManager.GamePhase.ACTIVE && mc.player.getTeam() != null;

        float scale = UiScale.push(guiGraphics);
        try {
            float delta = UiFrame.delta();
            float logicalWidth = screenWidth / scale;
            if (matchRunning) {
                renderTopStack(guiGraphics, mc, logicalWidth, screenHeight / scale, scale, delta, wanted);
            }
            renderCentral(guiGraphics, mc, logicalWidth, screenHeight / scale, delta, wanted);
        } finally {
            UiScale.pop(guiGraphics);
        }
    };

    private static void renderTopStack(GuiGraphics graphics, Minecraft mc, float screenWidth,
                                       float screenHeight, float scale, float delta, boolean wanted) {
        if (!HudLayout.visible(HudSlot.OBJECTIVE)) return;

        HudBox box = HudLayout.place(HudSlot.OBJECTIVE, OBJECTIVE_WIDTH, OBJECTIVE_HEIGHT, screenWidth, screenHeight);
        HudLayout.push(graphics, box);
        try {
            paintTopStack(graphics, mc, box, scale, delta, wanted);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    private static void paintTopStack(GuiGraphics graphics, Minecraft mc, HudBox box, float scale, float delta,
                                      boolean wanted) {
        float centerX = box.centerX();
        float top = TopStack.at("capture_top", objectiveBase(box, scale), delta);
        float returnBottom = CaptureReturnHud.render(graphics, mc, centerX, top, delta, wanted);

        float objectiveTop = TopStack.at("capture_objective",
                returnBottom > 0.0f ? returnBottom + STACK_GAP : top, delta);
        float barBottom = ObjectiveHud.render(graphics, mc, centerX, objectiveTop, delta, wanted);

        float pillsTop = TopStack.at("capture_pills",
                barBottom > 0.0f ? barBottom + STACK_GAP : objectiveTop, delta);
        renderPills(graphics, mc, centerX, pillsTop, delta, wanted);
    }

    private static float objectiveBase(HudBox box, float scale) {
        if (HudLayout.pinned(HudSlot.OBJECTIVE)) return box.y();
        if (!HudLayout.of(HudSlot.OBJECTIVE).anchor().top()) return box.y();
        return Math.max(box.y(), BossBarHud.bottom() / scale + 6.0f);
    }

    private static void renderPills(GuiGraphics graphics, Minecraft mc, float centerX, float top, float delta,
                                    boolean wanted) {
        long now = System.currentTimeMillis();
        boolean finalsOpen = ClientCaptureData.areAllPointsCapturedBySameTeam();

        claimRow(ClientCaptureData.getAllPointOwners(), false, now);
        if (finalsOpen) {
            claimRow(ClientCaptureData.getAllFinalPointOwners(), true, now);
        }
        retire(mc, finalsOpen, now, delta, wanted);

        float rowTop = renderRow(graphics, mc, centerX, top, false);
        renderRow(graphics, mc, centerX, rowTop > 0.0f ? rowTop + ROW_GAP : top, true);
    }

    private static void claimRow(Map<String, String> owners, boolean isFinal, long now) {
        for (Map.Entry<String, String> entry : owners.entrySet()) {
            claimPill(entry.getKey(), entry.getValue(), isFinal, now);
        }
    }

    // WHY: под открытым списком игроков пилюли гаснут присутствием, но из набора не выбывают:
    // WHY: снятая пилюля вернулась бы новой и без своей истории смены владельца
    private static void retire(Minecraft mc, boolean finalsOpen, long now, float delta, boolean wanted) {
        boolean anyGone = false;
        for (Map.Entry<String, Pill> entry : pills.entrySet()) {
            Pill pill = entry.getValue();
            pill.listed = listed(entry.getKey(), pill.isFinal, finalsOpen);
            pill.alive = pill.listed && wanted;
            animatePill(mc, entry.getKey(), pill, now, delta);
            anyGone |= !pill.listed && pill.toggle.hidden();
        }
        if (anyGone && wanted) {
            pills.values().removeIf(pill -> !pill.listed && pill.toggle.hidden());
        }
    }

    private static boolean listed(String name, boolean isFinal, boolean finalsOpen) {
        if (isFinal) return finalsOpen && ClientCaptureData.getAllFinalPointOwners().containsKey(name);
        return ClientCaptureData.getAllPointOwners().containsKey(name);
    }

    private static void claimPill(String name, String owner, boolean isFinal, long now) {
        Pill pill = pills.get(name);
        if (pill == null) {
            pill = new Pill();
            pill.owner = owner;
            pills.put(name, pill);
        } else if (!Objects.equals(pill.owner, owner)) {
            pill.owner = owner;
            pill.changedAt = now;
        }
        pill.isFinal = isFinal;
    }

    private static void animatePill(Minecraft mc, String name, Pill pill, long now, float delta) {
        float progress = pill.listed ? ClientCaptureData.getProgress(name) : 0.0f;

        pill.capturing = pill.alive && progress > 0.001f
                && now - ClientCaptureData.getLastUpdateTime(name) < CAPTURE_TIMEOUT_MS;
        pill.alphaValue = pill.toggle.update(pill.alive, delta);
        pill.flash = UiAnim.clamp01((OWNER_FLASH_MS - (now - pill.changedAt)) / (float) OWNER_FLASH_MS);
        if (pill.toggle.hidden()) {
            pill.progress.snap(progress);
        }
        pill.progressValue = pill.progress.to(progress, delta);
        float span = UiRender.width(mc.font, name) * LABEL_SCALE + PILL_PADDING * 2.0f + DOT_COLUMN;
        pill.drawWidth = span * UiAnim.easeOut(pill.alphaValue);
    }

    private static float renderRow(GuiGraphics graphics, Minecraft mc, float centerX, float top, boolean finalRow) {
        float totalWidth = 0.0f;
        int visible = 0;

        for (Pill pill : pills.values()) {
            if (pill.isFinal != finalRow || pill.alphaValue <= 0.01f) continue;
            totalWidth += pill.drawWidth;
            visible++;
        }
        if (visible == 0) return 0.0f;

        totalWidth += PILL_GAP * (visible - 1);
        float x = centerX - totalWidth / 2.0f;

        for (Map.Entry<String, Pill> entry : pills.entrySet()) {
            Pill pill = entry.getValue();
            if (pill.isFinal != finalRow || pill.alphaValue <= 0.01f) continue;

            float y = top - (1.0f - UiAnim.easeOut(pill.alphaValue)) * 6.0f;
            renderPill(graphics, mc, entry.getKey(), pill, x, y);
            x += pill.drawWidth + PILL_GAP;
        }
        return top + PILL_HEIGHT;
    }

    private static void renderPill(GuiGraphics graphics, Minecraft mc, String name, Pill pill, float x, float y) {
        float alpha = pill.alphaValue;
        float width = pill.drawWidth;

        float lift = pill.capturing
                ? UiAnim.pulse(1100.0f, 0.25f, 0.85f)
                : Math.max(pill.isFinal ? 0.5f : 0.0f, pill.flash * 0.7f);

        UiVital.card(graphics, x, y, width, PILL_HEIGHT, Math.min(PILL_RADIUS, PILL_HEIGHT / 2.0f), alpha, lift);

        if (width < PILL_PADDING * 2.0f) return;

        UiRender.dot(graphics, x + PILL_PADDING - 1.0f, y + PILL_HEIGHT / 2.0f, 1.5f,
                UiTheme.alpha(dotTint(pill), alpha));

        int labelColor = CaptureColors.label();
        UiRender.labelScaled(graphics, mc.font, name, x + PILL_PADDING + 5.0f,
                UiRender.centerY(y, PILL_HEIGHT, LABEL_SCALE), LABEL_SCALE, UiTheme.alpha(labelColor, alpha));

        if (pill.progressValue > 0.002f) {
            int bar = ClientCaptureData.isDecaying(name) ? UiPalette.alert() : attackerTint(name);
            UiRender.panel(graphics, x + 3.0f, y + PILL_HEIGHT - 3.0f, (width - 6.0f) * pill.progressValue, 1.5f, 0.75f,
                    UiTheme.alpha(bar, alpha));
        }
    }

    private static int dotTint(Pill pill) {
        if (pill.isFinal) return UiAccent.color();
        if (pill.owner == null || pill.owner.isEmpty()) return CaptureColors.neutralDot();
        return MapRenderUtil.getTeamColor(pill.owner);
    }

    private static int attackerTint(String pointName) {
        String team = ClientCaptureData.getAttackerTeam(pointName);
        if (team == null || team.isEmpty()) return CaptureColors.progress();
        return MapRenderUtil.getTeamColor(team);
    }

    private static void renderCentral(GuiGraphics graphics, Minecraft mc, float screenWidth, float screenHeight,
                                     float delta, boolean wanted) {
        boolean capturing = ClientCaptureData.isLocalCaptureShown();
        centralLive = capturing;
        if (capturing) {
            centralPoint = ClientCaptureData.getLocalCapturingPoint();
            centralFinal = ClientCaptureData.isLocalCapturingFinal();
            centralValue = ClientCaptureData.getBarValue(centralPoint);
        }

        float alpha = centralToggle.update(capturing && wanted, delta);
        if (centralToggle.cleared() && !capturing) {
            centralPoint = null;
            centralValue = 0.0f;
            centralProgress.snap(0.0f);
            centralCachedTag = Component.empty();
            centralState.settle();
        }
        if (alpha <= 0.01f || centralPoint == null) return;

        wantCentralTag();
        renderCentralCard(graphics, mc, screenWidth, screenHeight, alpha,
                centralProgress.to(centralValue, delta), delta);
    }

    private static Component centralLabel(float progress) {
        if (!centralLive) return centralCached == null ? Component.empty() : centralCached;

        centralCached = buildCentralLabel(progress);
        return centralCached;
    }

    private static Component buildCentralLabel(float progress) {
        String point = centralPoint;
        if (!ClientCaptureData.isRunning(point)) return Component.translatable("capturepoints.hud.point", point);

        return Component.translatable("capturepoints.hud.capture_progress", point, (int) (progress * 100));
    }

    // WHY: тег состояния отделён от строки прогресса, потому что проценты меняются каждый кадр,
    // WHY: и перекрашивать по ним всю надпись значит мигать вместо смены состояния
    private static void wantCentralTag() {
        if (!centralLive) return;

        String key = resolveTag();
        centralState.want(key, centralCachedTag, HudInk.text());
    }

    private static String resolveTag() {
        String point = centralPoint;
        if (!ClientCaptureData.isRunning(point)) {
            centralCachedTag = Component.translatable(STATE_ARMING,
                    ClientCaptureData.getArmingSeconds(point));
            return STATE_ARMING;
        }
        if (ClientCaptureData.isHeld(point)) return tag(STATE_HELD);

        int attackers = ClientCaptureData.getAttackerCount(point);
        int rivals = ClientCaptureData.getRivalCount(point);
        if (rivals > 0 && attackers == rivals) return tag(STATE_STALLED);
        if (rivals > 0) {
            centralCachedTag = Component.translatable(STATE_CONTEST, attackers, rivals);
            return STATE_CONTEST;
        }
        if (ClientCaptureData.isDecaying(point)) return tag(STATE_ROLLBACK);
        if (centralFinal) return tag(STATE_FINAL);

        centralCachedTag = Component.empty();
        return "";
    }

    private static String tag(String key) {
        centralCachedTag = Component.translatable(key);
        return key;
    }

    private static int centralTint() {
        if (!centralLive || centralPoint == null) {
            return centralCachedTint == 0 ? CaptureColors.progress() : centralCachedTint;
        }

        centralCachedTint = ClientCaptureData.isRunning(centralPoint) && !ClientCaptureData.isHeld(centralPoint)
                ? (ClientCaptureData.isDecaying(centralPoint) ? UiPalette.alert() : CaptureColors.progress())
                : UiAccent.faint();
        return centralCachedTint;
    }

    private static void renderCentralCard(GuiGraphics graphics, Minecraft mc, float screenWidth, float screenHeight,
                                          float alpha, float progress, float delta) {
        if (!HudLayout.visible(HudSlot.CAPTURE)) return;

        Component label = centralLabel(progress);

        float labelScale = UiRender.crisp(graphics, CENTRAL_LABEL_SCALE);
        float tagRoom = centralState.advance(graphics, labelScale, delta);
        float textWidth = UiRender.measure(graphics, mc.font, label, labelScale) + tagRoom;
        float barWidth = Math.max(CENTRAL_WIDTH, textWidth + 12.0f);
        float cardWidth = barWidth + CENTRAL_PADDING * 2.0f;
        float cardHeight = mc.font.lineHeight * labelScale + CENTRAL_BAR_HEIGHT + CENTRAL_PADDING * 2.0f + 4.0f;
        HudBox box = HudLayout.place(HudSlot.CAPTURE, cardWidth, cardHeight, screenWidth, screenHeight);
        float cardX = box.x();
        float cardY = box.y() + (1.0f - UiAnim.easeOut(alpha)) * 5.0f;

        HudLayout.push(graphics, box);
        try {
            paintCentralCard(graphics, mc, label, cardX, cardY, cardWidth, cardHeight, barWidth,
                    labelScale, progress, alpha, centralTint(), centralState);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    public static void preview(GuiGraphics graphics, float x, float y, float width, float height, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        float labelScale = UiRender.crisp(graphics, CENTRAL_LABEL_SCALE);
        Component label = Component.translatable("battlecraft.custom.sample.capture");
        float barWidth = Math.max(CENTRAL_WIDTH, UiRender.width(mc.font, label) * labelScale + 12.0f);
        float cardWidth = barWidth + CENTRAL_PADDING * 2.0f;
        float cardHeight = mc.font.lineHeight * labelScale + CENTRAL_BAR_HEIGHT + CENTRAL_PADDING * 2.0f + 4.0f;
        HudLayout.sample(HudSlot.CAPTURE, cardWidth, cardHeight);
        sampleState.want(STATE_ROLLBACK, Component.translatable(STATE_ROLLBACK), HudInk.text());
        sampleState.advance(graphics, labelScale, UiFrame.delta());
        paintCentralCard(graphics, mc, label, x, y, cardWidth, cardHeight, barWidth, labelScale,
                SAMPLE_PROGRESS, alpha, CaptureColors.progress(), sampleState);
    }

    private static void paintCentralCard(GuiGraphics graphics, Minecraft mc, Component label, float cardX,
                                         float cardY, float cardWidth, float cardHeight, float barWidth,
                                         float labelScale, float progress, float alpha, int tint,
                                         CaptureState state) {
        UiVital.card(graphics, cardX, cardY, cardWidth, cardHeight, UiMetrics.radius(cardHeight), alpha);

        float textY = cardY + CENTRAL_PADDING;
        float labelWidth = UiRender.measure(graphics, mc.font, label, labelScale);
        float textLeft = cardX + (cardWidth - labelWidth - state.room()) / 2.0f;
        UiRender.labelScaled(graphics, mc.font, label, textLeft, textY, labelScale,
                UiTheme.alpha(HudInk.text(), alpha));
        state.paint(graphics, textLeft + labelWidth, textY, labelScale, alpha);

        float barX = cardX + CENTRAL_PADDING;
        float barY = cardY + cardHeight - CENTRAL_PADDING - CENTRAL_BAR_HEIGHT;
        UiGlass.sunken(graphics, barX, barY, barWidth, CENTRAL_BAR_HEIGHT, CENTRAL_BAR_HEIGHT / 2.0f, alpha);
        UiGlass.progress(graphics, barX, barY, barWidth, CENTRAL_BAR_HEIGHT, progress, tint, alpha);
    }

    private static void renderProjectedMarkers(GuiGraphics graphics, Minecraft mc, float aimX, float aimY,
                                               boolean wanted) {
        float scale = UiScale.factor();
        float delta = UiFrame.delta();
        float dots = UiAnim.easeOut(dotPresence.to(wanted ? 1.0f : 0.0f, delta));

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0f);
        try {
            for (ProjectedMarker marker : MarkerRenderer.visible()) {
                float dx = marker.screenX() - aimX;
                float dy = marker.screenY() - aimY;
                float aimDistance = (float) Math.sqrt(dx * dx + dy * dy) / scale;
                float focus = 1.0f - UiAnim.clamp01((aimDistance - HOVER_NEAR) / (HOVER_FAR - HOVER_NEAR));

                renderMarker(graphics, mc, marker, marker.screenX() / scale, marker.screenY() / scale,
                        focus, delta, wanted, dots);
            }
        } finally {
            graphics.pose().popPose();
        }
        forgetUnseen();
    }

    private static void renderMarker(GuiGraphics graphics, Minecraft mc, ProjectedMarker marker,
                                     float x, float y, float focus, float delta, boolean wanted, float dots) {
        if (marker.name() == null) {
            UiRender.dot(graphics, x, y, MARKER_DOT * HudConfig.markerScale(),
                    UiTheme.alpha(markerColor(marker), (0.45f + 0.55f * focus) * dots));
            return;
        }

        MarkerState state = stateOf(marker);
        float presence = state.presence(wanted, delta);
        if (presence <= MARKER_GONE) return;

        float scale = MARKER_LABEL_SCALE * HudConfig.markerScale();
        float alpha = UiWorldTag.IDLE_ALPHA + (UiWorldTag.FOCUS_ALPHA - UiWorldTag.IDLE_ALPHA) * focus;
        float height = UiWorldTag.height(mc.font, scale);
        float ranged = UiAnim.easeOut(state.range.to(marker.distance() >= RANGE_SHOWN_FROM ? 1.0f : 0.0f, delta));
        state.stack.depth((float) marker.distance());

        UiWorldTag.render(graphics, mc.font, Component.literal(marker.name()),
                Component.translatable("capturepoints.hud.marker.range", (int) marker.distance()), ranged,
                x, y - height - MARKER_TAG_GAP, height, scale, markerColor(marker), alpha, focus,
                presence, state.stack);
    }

    private static MarkerState stateOf(ProjectedMarker marker) {
        MarkerState state = markerStates.computeIfAbsent(marker.name(), name -> new MarkerState());
        state.seen = UiFrame.frame();
        return state;
    }

    // WHY: метка, пропавшая с кадра, обязана сбросить присутствие в ноль: вернувшаяся из-за края
    // WHY: экрана иначе вспыхнула бы сразу целиком, а не проявилась заново
    private static void forgetUnseen() {
        long frame = UiFrame.frame();
        for (MarkerState state : markerStates.values()) {
            if (state.seen != frame) state.presence.snap(0.0f);
        }
    }

    private static int markerColor(ProjectedMarker marker) {
        if (marker.explicitColor() != null) return marker.explicitColor();
        if (marker.isFinal()) return UiAccent.color();
        if (marker.owner() != null && !marker.owner().isEmpty()) {
            return MapRenderUtil.getTeamColor(marker.owner());
        }
        return CaptureColors.marker();
    }

    private static final class MarkerState {
        private final Smooth range = new Smooth(0.0f, RANGE_FADE_SPEED);
        private final Smooth presence = new Smooth(0.0f, PRESENCE_SPEED);
        private final UiTagStack.Slot stack = UiTagStack.slot();
        private long seen;

        private float presence(boolean wanted, float delta) {
            return UiAnim.easeOut(presence.to(wanted ? 1.0f : 0.0f, delta));
        }
    }

    private static final class Pill {
        private final Toggle toggle = new Toggle(8.0f, 120L);
        private final Smooth progress = new Smooth(14.0f);
        private String owner;
        private long changedAt;
        private boolean capturing;
        private boolean isFinal;
        private boolean listed = true;
        private boolean alive = true;
        private float alphaValue;
        private float progressValue;
        private float drawWidth;
        private float flash;
    }
}
