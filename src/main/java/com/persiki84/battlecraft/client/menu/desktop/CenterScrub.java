package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.battlecraft.client.island.TrackProgress;
import com.persiki84.battlecraft.client.media.MediaControl;
import com.persiki84.battlecraft.client.media.MediaTrack;
import com.persiki84.battlecraft.client.media.MediaWatch;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiCrisp;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// WHY: полоса мини-плеера ведёт себя как в «Сейчас играет» iOS: под курсором и на перетаскивании
// WHY: плавно толстеет, на перетаскивании показывает долю курсора, а не плеера, и время идёт за
// WHY: ней. Строки времени пересобираются только на смене секунды
final class CenterScrub {
    static final float TIME_SCALE = 0.52f;

    private static final float THIN = 3.0f;
    private static final float THICK = 5.0f;
    private static final float SLOP_X = 3.0f;
    private static final float SLOP_Y = 5.0f;
    private static final float GROW_SPEED = 14.0f;
    private static final float READY_SPEED = 10.0f;
    private static final float TRACK_ALPHA = 0.22f;
    private static final float FILL_ALPHA = 0.9f;
    private static final float TIME_BOX = 8.0f;

    private final TrackProgress progress = new TrackProgress();
    private final Smooth grow = new Smooth(0.0f, GROW_SPEED);
    private final Smooth ready = new Smooth(0.0f, READY_SPEED);
    private Component elapsed = Component.literal("0:00");
    private Component remaining = Component.literal("-0:00");
    private long elapsedSeconds = -1L;
    private long remainingSeconds = -1L;
    private boolean dragging;
    private float dragShare;
    private float left;
    private float centerY;
    private float width;

    void place(float x, float y, float span) {
        left = x;
        centerY = y;
        width = span;
    }

    void advance(MediaTrack track, double mouseX, double mouseY, float delta) {
        boolean seekable = MediaControl.canSeek();
        if (!seekable) dragging = false;
        progress.advance(track, delta);
        grow.to(seekable && (dragging || over(mouseX, mouseY)) ? 1.0f : 0.0f, delta);
        ready.to(timed(track) ? 1.0f : 0.0f, delta);
        if (timed(track)) time(shownElapsed(track), track.durationMs());
    }

    private static boolean timed(MediaTrack track) {
        return track.present() && !track.blind() && track.durationMs() > 0L;
    }

    private long shownElapsed(MediaTrack track) {
        if (dragging) return (long) (dragShare * track.durationMs());
        return track.elapsedMs(System.currentTimeMillis());
    }

    private void time(long elapsedMs, long durationMs) {
        long played = Math.max(0L, elapsedMs / 1000L);
        long rest = Math.max(0L, (durationMs - elapsedMs + 999L) / 1000L);
        if (played != elapsedSeconds) {
            elapsedSeconds = played;
            elapsed = Component.literal(clock(played));
        }
        if (rest != remainingSeconds) {
            remainingSeconds = rest;
            remaining = Component.literal("-" + clock(rest));
        }
    }

    private static String clock(long seconds) {
        return seconds / 60L + (seconds % 60L < 10L ? ":0" : ":") + seconds % 60L;
    }

    private boolean over(double mouseX, double mouseY) {
        return ready.get() > 0.5f && mouseX >= left - SLOP_X && mouseX <= left + width + SLOP_X
                && Math.abs(mouseY - centerY) <= SLOP_Y;
    }

    void paint(GuiGraphics graphics, int ink, float alpha) {
        float shown = alpha * ready.get();
        if (shown <= 0.02f || width <= 0.0f) return;
        float thick = THIN + (THICK - THIN) * grow.get();
        float top = centerY - thick / 2.0f;
        float share = dragging ? dragShare : progress.value();
        graphics.flush();
        panel(graphics, left, top, width, thick, UiTheme.alpha(ink, TRACK_ALPHA * shown));
        panel(graphics, left, top, width * share, thick, UiTheme.alpha(ink, FILL_ALPHA * shown));
    }

    void paintTimes(GuiGraphics graphics, Font font, float rowY, int dim, float alpha) {
        float shown = alpha * ready.get();
        if (shown <= 0.02f) return;
        float textY = UiRender.centerY(rowY - TIME_BOX / 2.0f, TIME_BOX, TIME_SCALE);
        int tone = UiTheme.alpha(dim, shown);
        UiRender.labelScaled(graphics, font, elapsed, left, textY, TIME_SCALE, tone);
        UiRender.labelRight(graphics, font, remaining, left + width, textY, TIME_SCALE, tone);
    }

    private static void panel(GuiGraphics graphics, float x, float y, float width, float height, int color) {
        if (width <= 0.0f) return;
        if (UiCrisp.ready()) {
            UiCrisp.panel(graphics, x, y, width, height, height / 2.0f, color);
            return;
        }
        UiRender.panel(graphics, x, y, width, height, height / 2.0f, color);
    }

    boolean press(double mouseX, double mouseY) {
        if (!MediaControl.canSeek() || !over(mouseX, mouseY)) return false;
        dragging = true;
        drag(mouseX);
        return true;
    }

    void drag(double mouseX) {
        if (!dragging || width <= 0.0f) return;
        dragShare = (float) Math.max(0.0, Math.min(1.0, (mouseX - left) / width));
    }

    boolean release(MediaTrack held) {
        if (!dragging) return false;
        dragging = false;
        long target = (long) (dragShare * held.durationMs());
        MediaControl.seek(target);
        MediaTrack moved = MediaWatch.current();
        if (moved.sameTrack(held) && moved.positionMs() == target) progress.land(moved);
        return true;
    }

    void cancel() {
        dragging = false;
    }
}
