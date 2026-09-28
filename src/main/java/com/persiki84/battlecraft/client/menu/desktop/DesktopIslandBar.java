package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiCrisp;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

// WHY: полоса ведёт себя как в «Сейчас играет» iOS: под курсором и на перетаскивании она
// WHY: плавно толстеет, а на время перетаскивания показывает долю курсора, а не плеера, и
// WHY: время слева и справа идёт за ней: игрок видит, куда отпустит, ещё до перемотки
final class DesktopIslandBar {
    private static final float THIN = 3.0f;
    private static final float THICK = 5.0f;
    private static final float SLOP_Y = 5.0f;
    private static final float SLOP_X = 3.0f;
    private static final float GROW_SPEED = 14.0f;
    private static final float TRACK_ALPHA = 0.22f;
    private static final float FILL_ALPHA = 0.92f;
    private static final float READY_REVEAL = 0.9f;

    private final Smooth grow = new Smooth(0.0f, GROW_SPEED);
    private boolean dragging;
    private float dragShare;

    void advance(DesktopIslandFrame frame, double mouseX, double mouseY, boolean seekable, float delta) {
        if (!seekable) dragging = false;
        grow.to(seekable && (dragging || over(frame, mouseX, mouseY)) ? 1.0f : 0.0f, delta);
    }

    private boolean over(DesktopIslandFrame frame, double mouseX, double mouseY) {
        return frame.reveal >= READY_REVEAL && frame.barWidth > 0.0f
                && mouseX >= frame.barX - SLOP_X && mouseX <= frame.barX + frame.barWidth + SLOP_X
                && Math.abs(mouseY - frame.barY) <= SLOP_Y;
    }

    boolean press(DesktopIslandFrame frame, double mouseX, double mouseY) {
        if (!over(frame, mouseX, mouseY)) return false;

        dragging = true;
        drag(frame, mouseX);
        return true;
    }

    void drag(DesktopIslandFrame frame, double mouseX) {
        if (!dragging || frame.barWidth <= 0.0f) return;

        dragShare = (float) Math.max(0.0, Math.min(1.0, (mouseX - frame.barX) / frame.barWidth));
    }

    float release() {
        dragging = false;
        return dragShare;
    }

    void cancel() {
        dragging = false;
    }

    boolean dragging() {
        return dragging;
    }

    float shown(float played) {
        return dragging ? dragShare : played;
    }

    void paint(GuiGraphics graphics, Font font, DesktopIslandFrame frame, DesktopIslandText text,
               float share, int dim, float alpha) {
        if (alpha <= 0.02f || frame.barWidth <= 0.0f) return;

        float thick = THIN + (THICK - THIN) * grow.get();
        float top = frame.barY - thick / 2.0f;
        graphics.flush();
        panel(graphics, frame.barX, top, frame.barWidth, thick, UiTheme.withAlpha(UiTheme.WHITE, TRACK_ALPHA * alpha));
        panel(graphics, frame.barX, top, frame.barWidth * share, thick,
                UiTheme.withAlpha(UiTheme.WHITE, FILL_ALPHA * alpha));

        int tone = UiTheme.alpha(dim, alpha);
        float textY = UiRender.centerY(frame.barY - thick, thick * 2.0f, DesktopIslandText.TIME_SCALE);
        UiRender.labelScaled(graphics, font, text.elapsed(), frame.timeLeft(), textY,
                DesktopIslandText.TIME_SCALE, tone);
        UiRender.labelRight(graphics, font, text.remaining(), frame.timeRight(), textY,
                DesktopIslandText.TIME_SCALE, tone);
    }

    private static void panel(GuiGraphics graphics, float x, float y, float width, float height, int color) {
        if (width <= 0.0f) return;

        if (UiCrisp.ready()) {
            UiCrisp.panel(graphics, x, y, width, height, height / 2.0f, color);
            return;
        }
        UiRender.panel(graphics, x, y, width, height, height / 2.0f, color);
    }
}
