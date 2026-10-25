package com.persiki84.capturepoints.client;

import com.persiki84.battlecraft.client.ClientGameData;
import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.capturepoints.network.MatchBannerPacket;
import com.persiki84.minimap.client.MapRenderUtil;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiHud;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

// WHY: баннер середины экрана один на всё (открытие финальной, внезапная смерть, конец матча):
// WHY: новый сменяет прежний, а не ложится поверх. Ширина меряется один раз на показ, а не в кадре
public final class MatchBanner {
    private static final float TITLE_HEIGHT = 26.0f;
    private static final float LINE_GAP = 3.0f;
    private static final float BOTTOM_PAD = 7.0f;
    private static final float PADDING = 22.0f;
    private static final float TITLE_SCALE = 1.35f;
    private static final float DETAIL_SCALE = 0.85f;
    private static final float TITLE_TRACKING = 1.2f;
    private static final float DETAIL_TRACKING = 0.3f;
    private static final float FADE_IN_MS = 280.0f;
    private static final float FADE_OUT_MS = 700.0f;
    private static final float RISE = 8.0f;
    private static final float SCREEN_SHARE = 0.3f;
    private static final long ENDING_MS = 9000L;
    private static final long SUDDEN_MS = 6500L;
    private static final float TEAM_MUTE = 0.15f;
    private static final float PRESENCE_SPEED = 8.0f;
    private static final Smooth presence = new Smooth(0.0f, PRESENCE_SPEED);
    private static final float HIDDEN = 0.01f;
    private static final float MILLIS_PER_SECOND = 1000.0f;

    private static Component title;
    private static Component detail = Component.empty();
    private static Component score = Component.empty();
    private static int tint;
    private static long shownAt;
    private static long life;
    private static float width = -1.0f;
    private static boolean hasDetail;
    private static boolean hasScore;

    private MatchBanner() {}

    // WHY: свой слой, а не часть HUD точек: матч бывает и без модуля точек, на одних убийствах, и
    // WHY: баннер исхода обязан дойти. Под списком игроков баннер уходит, как остальной HUD
    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (ClientGameData.isSoftDisabled() || mc.options == null || mc.options.hideGui) return;
        float shown = UiAnim.easeOut(presence.to(UiHud.rosterOpen() ? 0.0f : 1.0f, UiFrame.delta()));
        if (shown <= HIDDEN) holdWhileHidden();
        render(graphics, mc, screenWidth, screenHeight, shown);
    };

    public static void show(Component nextTitle, Component nextDetail, Component nextScore, int nextTint,
                            long lifeMillis) {
        title = nextTitle;
        detail = nextDetail;
        score = nextScore;
        tint = nextTint;
        life = lifeMillis;
        shownAt = System.currentTimeMillis();
        width = -1.0f;
        hasDetail = !detail.getString().isEmpty();
        hasScore = !score.getString().isEmpty();
        UiSound.alert();
    }

    public static void accept(MatchBannerPacket packet) {
        switch (packet.kind) {
            case VICTORY -> show(Component.translatable("capturepoints.hud.victory", packet.team), packet.detail,
                    packet.score, UiTheme.muted(MapRenderUtil.getTeamColor(packet.team), TEAM_MUTE), ENDING_MS);
            case DRAW -> show(Component.translatable("capturepoints.hud.draw"), packet.detail, packet.score,
                    UiAccent.color(), ENDING_MS);
            case STOPPED -> show(Component.translatable("capturepoints.hud.stopped"), packet.detail, packet.score,
                    UiAccent.faint(), ENDING_MS);
            case SUDDEN -> show(Component.translatable("capturepoints.hud.sudden"), packet.detail, packet.score,
                    UiPalette.alert(), SUDDEN_MS);
        }
    }

    // WHY: под списком игроков баннер скрыт, и время его жизни стоит: кто держал Tab в конце матча,
    // WHY: иначе не увидел бы итог совсем, ведь строка в чате спрятана ради баннера
    private static void holdWhileHidden() {
        if (title != null) shownAt += (long) (UiFrame.delta() * MILLIS_PER_SECOND);
    }

    public static void forget() {
        title = null;
    }

    public static void render(GuiGraphics graphics, Minecraft mc, float screenWidth, float screenHeight,
                              float presence) {
        if (title == null) return;
        long elapsed = System.currentTimeMillis() - shownAt;
        if (elapsed > life) {
            title = null;
            return;
        }
        float fade = Math.min(UiAnim.fadeIn(shownAt, FADE_IN_MS), UiAnim.clamp01((life - elapsed) / FADE_OUT_MS));
        float alpha = presence * fade;
        if (alpha <= 0.01f) return;

        float scale = UiScale.push(graphics);
        try {
            paint(graphics, mc.font, screenWidth / scale, screenHeight / scale, alpha);
        } finally {
            UiScale.pop(graphics);
        }
    }

    private static void paint(GuiGraphics graphics, Font font, float logicalWidth, float logicalHeight, float alpha) {
        if (width < 0.0f) width = measure(font);
        float lineStep = font.lineHeight * DETAIL_SCALE + LINE_GAP;
        int lines = (hasDetail ? 1 : 0) + (hasScore ? 1 : 0);
        float height = TITLE_HEIGHT + (lines > 0 ? lines * lineStep + BOTTOM_PAD - LINE_GAP : 0.0f);
        float x = (logicalWidth - width) / 2.0f;
        float y = logicalHeight * SCREEN_SHARE - (1.0f - UiAnim.easeOut(alpha)) * RISE;
        float pulse = UiAnim.pulse(1400.0f, 0.55f, 1.0f);
        float centerX = logicalWidth / 2.0f;

        UiVital.cardTinted(graphics, x, y, width, height, UiMetrics.radius(TITLE_HEIGHT), alpha,
                0.22f * pulse, UiTheme.withAlpha(tint, 0.24f));
        UiRender.textTracked(graphics, font, title, centerX, UiRender.centerY(y, TITLE_HEIGHT, TITLE_SCALE),
                TITLE_SCALE, TITLE_TRACKING, UiTheme.alpha(HudInk.text(), alpha));
        float lineY = y + TITLE_HEIGHT - LINE_GAP;
        if (hasDetail) {
            UiRender.textTracked(graphics, font, detail, centerX, lineY, DETAIL_SCALE, DETAIL_TRACKING,
                    UiTheme.alpha(HudInk.textDim(), alpha));
            lineY += lineStep;
        }
        if (hasScore) {
            UiRender.textTracked(graphics, font, score, centerX, lineY, DETAIL_SCALE, DETAIL_TRACKING,
                    UiTheme.alpha(HudInk.text(), alpha));
        }
    }

    private static float measure(Font font) {
        float widest = tracked(font, title, TITLE_SCALE, TITLE_TRACKING);
        widest = Math.max(widest, tracked(font, detail, DETAIL_SCALE, DETAIL_TRACKING));
        widest = Math.max(widest, tracked(font, score, DETAIL_SCALE, DETAIL_TRACKING));
        return widest + PADDING * 2.0f;
    }

    private static float tracked(Font font, Component value, float scale, float tracking) {
        return UiRender.width(font, value) * scale + tracking * value.getString().length();
    }
}
