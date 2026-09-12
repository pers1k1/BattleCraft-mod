package com.persiki84.battlecraft.client.hudedit;

import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.battlecraft.client.custom.HudDock;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudSide;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class HudEditLayer {
    private static final int DOCK_COLOR = 0xFF3FDB6A;

    private static final float FRAME_RADIUS = 4.0f;
    private static final float FRAME_THICKNESS = 0.9f;
    private static final float IDLE_ALPHA = 0.26f;
    private static final float HOVER_LIT = 0.66f;
    private static final float HIDDEN_FILL = 0.07f;
    private static final float LIT_FILL = 0.06f;
    private static final float LIT_SWELL = 1.6f;
    private static final float LIT_SPEED = 14.0f;
    private static final float TAG_RISE = 3.0f;
    private static final float SAMPLE_ALPHA = 0.9f;
    private static final float GUIDE_WIDTH = 0.7f;
    private static final float GUIDE_ALPHA = 0.55f;
    private static final float DOCK_WIDTH = 1.1f;
    private static final float DOCK_REACH = 4.0f;
    private static final float HANDLE_SIZE = 5.0f;
    private static final float HANDLE_RADIUS = 1.6f;
    private static final float TAG_SCALE = 0.68f;
    private static final float TAG_HEIGHT = 10.0f;
    private static final float TAG_GAP = 2.5f;
    private static final float TAG_PAD = 4.0f;
    private static final float HINT_SCALE = 0.72f;
    private static final float HINT_TOP = 5.0f;
    private static final float GUIDE_SPEED = 16.0f;
    private static final float HANDLE_SPEED = 13.0f;

    private static final Smooth acrossGlow = new Smooth(0.0f, GUIDE_SPEED);
    private static final Smooth downGlow = new Smooth(0.0f, GUIDE_SPEED);
    private static final Smooth handleGrow = new Smooth(0.0f, HANDLE_SPEED);
    private static final Smooth[] lit = new Smooth[HudSlot.values().length];

    static {
        for (int index = 0; index < lit.length; index++) {
            lit[index] = new Smooth(0.0f, LIT_SPEED);
        }
    }

    private static float acrossLine;
    private static float downLine;

    private HudEditLayer() {}

    public static void render(GuiGraphics graphics) {
        float presence = HudEditSession.presence();
        if (presence <= 0.004f) return;

        float ui = UiScale.factor();
        float width = Minecraft.getInstance().getWindow().getGuiScaledWidth() / ui;
        float height = Minecraft.getInstance().getWindow().getGuiScaledHeight() / ui;
        if (HudEditSession.claimMeasure()) measureHidden(graphics);
        HudLayout.outlineMissing(width, height);
        advance(UiFrame.delta());

        graphics.pose().pushPose();
        graphics.pose().scale(ui, ui, 1.0f);
        try {
            drawGuides(graphics, presence, width, height);
            drawFrames(graphics, presence);
            drawDock(graphics, presence);
            drawHint(graphics, presence, width);
        } finally {
            graphics.pose().popPose();
        }
        HudSlotMenu.render(graphics);
    }

    private static void measureHidden(GuiGraphics graphics) {
        for (HudSlot slot : HudSlot.values()) {
            if (HudLayout.drawn(slot)) continue;
            HudSample.draw(graphics, slot, HudLayout.box(slot), 0.0f);
        }
    }

    private static void advance(float delta) {
        HudGuideAxis across = HudGuides.across();
        HudGuideAxis down = HudGuides.down();
        boolean moving = HudEditSession.dragging();
        if (moving && lining(across)) acrossLine = across.line();
        if (moving && lining(down)) downLine = down.line();

        acrossGlow.to(moving && lining(across) ? 1.0f : 0.0f, delta);
        downGlow.to(moving && lining(down) ? 1.0f : 0.0f, delta);
        handleGrow.to(HudEditSession.selected() == null ? 0.0f : 1.0f, delta);
        for (HudSlot slot : HudSlot.values()) {
            lit[slot.ordinal()].to(warmth(slot), delta);
        }
    }

    private static float warmth(HudSlot slot) {
        if (slot == HudEditSession.selected()) return 1.0f;
        return slot == HudEditSession.hovered() ? HOVER_LIT : 0.0f;
    }

    private static boolean lining(HudGuideAxis axis) {
        return axis.kind() == HudGuideKind.GRID || axis.kind() == HudGuideKind.ALIGN;
    }

    private static void drawGuides(GuiGraphics graphics, float presence, float width, float height) {
        float across = acrossGlow.get() * presence;
        float down = downGlow.get() * presence;
        if (across > 0.004f) {
            UiRender.panel(graphics, acrossLine - GUIDE_WIDTH / 2.0f, 0.0f, GUIDE_WIDTH, height,
                    GUIDE_WIDTH / 2.0f, UiTheme.withAlpha(UiAccent.color(), GUIDE_ALPHA * across));
        }
        if (down <= 0.004f) return;

        UiRender.panel(graphics, 0.0f, downLine - GUIDE_WIDTH / 2.0f, width, GUIDE_WIDTH,
                GUIDE_WIDTH / 2.0f, UiTheme.withAlpha(UiAccent.color(), GUIDE_ALPHA * down));
    }

    private static void drawFrames(GuiGraphics graphics, float presence) {
        for (HudSlot slot : HudSlot.values()) {
            HudBox box = HudLayout.box(slot);
            if (!box.drawn()) continue;

            float shown = presence * UiAnim.easeOut(HudEditSession.appear(slot.ordinal()));
            if (shown <= 0.004f) continue;

            drawFrame(graphics, slot, box, shown);
        }
    }

    private static void drawFrame(GuiGraphics graphics, HudSlot slot, HudBox box, float shown) {
        float warm = UiAnim.easeOut(lit[slot.ordinal()].get());
        float swell = LIT_SWELL * warm;
        float width = box.width() * box.scale() + swell * 2.0f;
        float height = box.height() * box.scale() + swell * 2.0f;
        float x = box.x() - swell;
        float y = box.y() - swell;
        float weight = IDLE_ALPHA + (1.0f - IDLE_ALPHA) * warm;
        float fill = HudLayout.visible(slot) ? LIT_FILL * warm : HIDDEN_FILL + LIT_FILL * warm;

        if (fill > 0.002f) {
            UiRender.panel(graphics, x, y, width, height, FRAME_RADIUS + swell,
                    UiTheme.withAlpha(UiAccent.color(), fill * shown));
        }
        UiRender.rim(graphics, x, y, width, height, FRAME_RADIUS + swell,
                FRAME_THICKNESS + 0.4f * warm, UiTheme.withAlpha(UiAccent.color(), weight * shown));
        boolean sampled = warm > 0.004f && !HudLayout.drawn(slot);
        if (sampled) drawSample(graphics, slot, box, shown * warm);
        if (warm > 0.004f) drawTag(graphics, slot, box, shown * warm, warm);
        if (slot == HudEditSession.selected()) {
            drawHandles(graphics, box, shown * UiAnim.easeOutBack(handleGrow.get()));
        }
    }

    private static void drawSample(GuiGraphics graphics, HudSlot slot, HudBox box, float shown) {
        HudSample.draw(graphics, slot, box, shown * SAMPLE_ALPHA);
    }

    private static void drawTag(GuiGraphics graphics, HudSlot slot, HudBox box, float shown, float warm) {
        Component title = HudLayout.visible(slot)
                ? Component.translatable(slot.translationKey())
                : Component.translatable("battlecraft.custom.hud.tag_hidden",
                        Component.translatable(slot.translationKey()));
        float y = box.y() - TAG_HEIGHT - TAG_GAP + (1.0f - warm) * TAG_RISE;
        if (y < 0.0f) y = box.y() + box.height() * box.scale() + TAG_GAP - (1.0f - warm) * TAG_RISE;

        float width = UiRender.measure(graphics, font(), title, TAG_SCALE) + TAG_PAD * 2.0f;
        UiRender.panel(graphics, box.x(), y, width, TAG_HEIGHT, TAG_HEIGHT / 2.0f,
                UiTheme.withAlpha(0x101014, 0.82f * shown));
        UiRender.textCentered(graphics, font(), title, box.x() + width / 2.0f,
                UiRender.centerY(y, TAG_HEIGHT, TAG_SCALE), TAG_SCALE,
                UiTheme.withAlpha(HudInk.text(), shown), false);
    }

    private static void drawHandles(GuiGraphics graphics, HudBox box, float shown) {
        if (shown <= 0.004f) return;

        float width = box.width() * box.scale();
        float height = box.height() * box.scale();
        drawHandle(graphics, box.x(), box.y(), shown);
        drawHandle(graphics, box.x() + width, box.y() + height, shown);
    }

    private static void drawHandle(GuiGraphics graphics, float cornerX, float cornerY, float shown) {
        float span = HANDLE_SIZE * (0.7f + 0.3f * Math.min(1.0f, shown));
        UiRender.panel(graphics, cornerX - span / 2.0f, cornerY - span / 2.0f, span, span,
                HANDLE_RADIUS, UiTheme.withAlpha(UiAccent.color(), Math.min(1.0f, shown)));
        UiRender.panel(graphics, cornerX - span / 4.0f, cornerY - span / 4.0f, span / 2.0f, span / 2.0f,
                HANDLE_RADIUS / 2.0f, UiTheme.withAlpha(HudInk.text(), Math.min(1.0f, shown) * 0.9f));
    }

    private static void drawDock(GuiGraphics graphics, float presence) {
        HudSlot slot = HudEditSession.dragging() ? HudEditSession.selected() : hoveredDocked();
        if (slot == null) return;

        HudDock dock = HudEditSession.dragging() ? HudGuides.dock() : HudLayout.of(slot).dock();
        if (dock == null || !HudLayout.drawn(dock.target())) return;

        drawSeam(graphics, HudLayout.box(slot), HudLayout.box(dock.target()), dock.side(), presence);
    }

    private static HudSlot hoveredDocked() {
        HudSlot slot = HudEditSession.hovered();
        return slot != null && HudLayout.of(slot).dock() != null ? slot : null;
    }

    private static void drawSeam(GuiGraphics graphics, HudBox moved, HudBox target, HudSide side, float shown) {
        float targetWidth = target.width() * target.scale();
        float targetHeight = target.height() * target.scale();
        int color = UiTheme.withAlpha(DOCK_COLOR, shown);
        if (side.horizontal()) {
            float x = side == HudSide.LEFT ? target.x() : target.x() + targetWidth;
            float from = Math.max(moved.y(), target.y()) - DOCK_REACH;
            float to = Math.min(moved.y() + moved.height() * moved.scale(), target.y() + targetHeight)
                    + DOCK_REACH;
            UiRender.panel(graphics, x - DOCK_WIDTH / 2.0f, from, DOCK_WIDTH, Math.max(DOCK_WIDTH, to - from),
                    DOCK_WIDTH / 2.0f, color);
            return;
        }
        float y = side == HudSide.TOP ? target.y() : target.y() + targetHeight;
        float from = Math.max(moved.x(), target.x()) - DOCK_REACH;
        float to = Math.min(moved.x() + moved.width() * moved.scale(), target.x() + targetWidth) + DOCK_REACH;
        UiRender.panel(graphics, from, y - DOCK_WIDTH / 2.0f, Math.max(DOCK_WIDTH, to - from), DOCK_WIDTH,
                DOCK_WIDTH / 2.0f, color);
    }

    private static void drawHint(GuiGraphics graphics, float presence, float width) {
        if (HudEditSession.selected() != null) return;

        UiRender.textCentered(graphics, font(), Component.translatable("battlecraft.custom.hud.hint"),
                width / 2.0f, HINT_TOP, HINT_SCALE,
                UiTheme.withAlpha(HudInk.textDim(), presence * 0.85f), false);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
