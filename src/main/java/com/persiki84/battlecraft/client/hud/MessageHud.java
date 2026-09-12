package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public final class MessageHud {
    private static final float TITLE_ANCHOR = 0.36f;
    private static final float TITLE_SCALE = 2.25f;
    private static final float TITLE_TRACKING = 1.1f;
    private static final float SUBTITLE_SCALE = 1.1f;
    private static final float CARD_PADDING = UiMetrics.PAD_WIDE;
    private static final float ROW_GAP = UiMetrics.GAP;
    private static final float ACTION_SCALE = 1.0f;
    private static final float ACTION_PADDING = UiMetrics.PAD_WIDE;
    private static final float LIFT_TRAVEL = 6.0f;

    private MessageHud() {}

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !GuiBridge.available() || !HudLayout.visible(HudSlot.MESSAGES)) return;

        float scale = UiScale.push(graphics);
        try {
            renderTitle(graphics, mc, partialTick, screenWidth / scale, screenHeight / scale);
            renderAction(graphics, mc, partialTick, screenWidth / scale, screenHeight / scale);
        } finally {
            UiScale.pop(graphics);
        }
    };

    private static void renderTitle(GuiGraphics graphics, Minecraft mc, float partialTick,
                                    float screenWidth, float screenHeight) {
        GuiBridge.Notice notice = GuiBridge.title(mc.gui, partialTick);
        if (notice == null) return;

        float titleScale = UiRender.crisp(graphics, TITLE_SCALE);
        float detailScale = UiRender.crisp(graphics, SUBTITLE_SCALE);
        float titleRow = mc.font.lineHeight * titleScale;
        float detailRow = notice.detail() == null ? 0.0f : mc.font.lineHeight * detailScale + ROW_GAP;

        float width = titleSpan(mc, notice, titleScale, detailScale) + CARD_PADDING * 2.0f;
        float height = UiMetrics.snap(titleRow + detailRow + CARD_PADDING * 2.0f);
        float alpha = notice.alpha();
        float x = (screenWidth - width) / 2.0f;
        float y = screenHeight * TITLE_ANCHOR - height / 2.0f + (1.0f - UiAnim.easeOut(alpha)) * LIFT_TRAVEL;

        UiVital.card(graphics, x, y, width, height, UiMetrics.radius(height), alpha);
        UiRender.textTracked(graphics, mc.font, notice.text(), screenWidth / 2.0f, y + CARD_PADDING, titleScale,
                TITLE_TRACKING, UiTheme.alpha(HudInk.text(), alpha));

        if (notice.detail() == null) return;
        UiRender.textCentered(graphics, mc.font, notice.detail(), screenWidth / 2.0f,
                y + CARD_PADDING + titleRow + ROW_GAP, detailScale,
                UiTheme.alpha(HudInk.textDim(), alpha), false);
    }

    private static float titleSpan(Minecraft mc, GuiBridge.Notice notice, float titleScale, float detailScale) {
        float titleWidth = UiRender.width(mc.font, notice.text()) * titleScale
                + TITLE_TRACKING * notice.text().getString().length();
        if (notice.detail() == null) return titleWidth;
        return Math.max(titleWidth, UiRender.width(mc.font, notice.detail()) * detailScale);
    }

    private static void renderAction(GuiGraphics graphics, Minecraft mc, float partialTick,
                                     float screenWidth, float screenHeight) {
        GuiBridge.Notice notice = GuiBridge.action(mc.gui, partialTick);
        if (notice == null) return;

        float scale = UiRender.crisp(graphics, ACTION_SCALE);
        float width = UiRender.measure(graphics, mc.font, notice.text(), scale) + ACTION_PADDING * 2.0f;
        float height = UiMetrics.snap(mc.font.lineHeight * scale + UiMetrics.GAP_WIDE);
        float alpha = notice.alpha();
        HudBox box = HudLayout.placeAbove(HudSlot.MESSAGES, width, height, screenWidth, screenHeight,
                BottomHud.noticeTop());
        float y = box.y() + (1.0f - UiAnim.easeOut(alpha)) * LIFT_TRAVEL;
        float x = box.x();

        HudLayout.push(graphics, box);
        try {
            paintAction(graphics, mc, notice, x, y, width, height, scale, alpha);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    public static void preview(GuiGraphics graphics, float x, float y, float width, float height, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        float scale = UiRender.crisp(graphics, ACTION_SCALE);
        Component text = Component.translatable("battlecraft.custom.sample.message");
        float span = UiRender.measure(graphics, mc.font, text, scale) + ACTION_PADDING * 2.0f;
        HudLayout.sample(HudSlot.MESSAGES, span, height);
        UiVital.card(graphics, x, y, span, height, UiMetrics.radius(height), alpha);
        UiRender.textCentered(graphics, mc.font, text, x + span / 2.0f,
                UiRender.centerY(y, height, scale), scale,
                UiTheme.alpha(HudLayout.tint(HudSlot.MESSAGES, HudInk.text()), alpha), false);
    }

    private static void paintAction(GuiGraphics graphics, Minecraft mc, GuiBridge.Notice notice,
                                    float x, float y, float width, float height, float scale, float alpha) {
        UiVital.card(graphics, x, y, width, height, UiMetrics.radius(height), alpha);
        UiRender.textCentered(graphics, mc.font, notice.text(), x + width / 2.0f,
                UiRender.centerY(y, height, scale), scale,
                UiTheme.alpha(HudLayout.tint(HudSlot.MESSAGES, HudInk.text()), alpha), false);
    }
}
