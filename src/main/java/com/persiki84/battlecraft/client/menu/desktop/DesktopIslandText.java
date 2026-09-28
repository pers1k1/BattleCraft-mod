package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.battlecraft.client.media.MediaTrack;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

// WHY: строки острова и их ширины пересобираются только на смене трека, секунды или плотности
// WHY: пикселей: замер текста раскладывает глифы, и делать его в каждом кадре было бы накладно.
// WHY: На смене трека строки уходящего держатся отдельным снимком и гаснут, пока новые
// WHY: проявляются: название не подменяется кадром, а переходит наплывом
final class DesktopIslandText {
    static final float PILL_TITLE_SCALE = 0.75f;
    static final float CARD_TITLE_SCALE = 0.86f;
    static final float ARTIST_SCALE = 0.72f;
    static final float TIME_SCALE = 0.56f;

    private static final float SWAP_SECONDS = 0.3f;
    private static final float LEAVE_UNTIL = 0.6f;
    private static final float ENTER_FROM = 0.3f;
    private static final float SWAP_LIFT = 4.0f;
    private static final Component NO_CONTROL = Component.translatable("battlecraft.desktop.island.no_control");
    private static final Component TIME_SAMPLE = Component.literal("-00:00");

    private final Face shown = new Face();
    private final Face leaving = new Face();
    private MediaTrack described = MediaTrack.NONE;
    private Component elapsed = Component.literal("0:00");
    private Component remaining = Component.literal("-0:00");
    private long elapsedSeconds = -1L;
    private long remainingSeconds = -1L;
    private float measuredPixels = -1.0f;
    private Language measuredLanguage;
    private float timeSlot;
    private float swap = 1.0f;

    // WHY: наплыв нужен только на глазах у игрока: остров, вернувшийся после тишины, не должен
    // WHY: сначала показать давно ушедший трек, чтобы тут же его погасить
    boolean describe(MediaTrack track, float delta, boolean visible) {
        swap = Math.min(1.0f, swap + delta / SWAP_SECONDS);
        if (track.sameTrack(described)) return false;

        boolean swapping = visible && described.present() && track.present();
        leaving.copy(shown);
        described = track;
        shown.write(track.title().isEmpty() ? track.origin() : track.title(), artistOf(track));
        measuredPixels = -1.0f;
        swap = swapping ? 0.0f : 1.0f;
        return swapping;
    }

    private static Component artistOf(MediaTrack track) {
        if (track.blind()) return NO_CONTROL;
        return Component.literal(track.artist().isEmpty() ? track.origin() : track.artist());
    }

    void time(long elapsedMs, long durationMs) {
        long played = Math.max(0L, elapsedMs / 1000L);
        long left = Math.max(0L, (durationMs - elapsedMs + 999L) / 1000L);
        if (played != elapsedSeconds) {
            elapsedSeconds = played;
            elapsed = Component.literal(clock(played));
        }
        if (left != remainingSeconds) {
            remainingSeconds = left;
            remaining = Component.literal("-" + clock(left));
        }
    }

    void measure(GuiGraphics graphics, Font font) {
        float pixels = UiRender.pixels(graphics);
        Language language = Language.getInstance();
        if (pixels == measuredPixels && language == measuredLanguage) return;

        measuredPixels = pixels;
        measuredLanguage = language;
        shown.measure(graphics, font);
        timeSlot = UiRender.measure(graphics, font, TIME_SAMPLE, TIME_SCALE);
    }

    private static String clock(long seconds) {
        return seconds / 60L + (seconds % 60L < 10L ? ":0" : ":") + seconds % 60L;
    }

    Face shown() {
        return shown;
    }

    Face leaving() {
        return leaving;
    }

    float entering() {
        return UiAnim.smoothstep(ENTER_FROM, 1.0f, swap);
    }

    float fading() {
        return 1.0f - UiAnim.smoothstep(0.0f, LEAVE_UNTIL, swap);
    }

    float leaveShift() {
        return -SWAP_LIFT * UiAnim.smoothstep(0.0f, LEAVE_UNTIL, swap);
    }

    float enterShift() {
        return SWAP_LIFT * (1.0f - entering());
    }

    Component elapsed() {
        return elapsed;
    }

    Component remaining() {
        return remaining;
    }

    float timeSlot() {
        return timeSlot;
    }

    static final class Face {
        private Component title = Component.empty();
        private Component artist = Component.empty();
        private String titleRaw = "";
        private String artistRaw = "";
        private float pillTitleWidth;
        private float cardTitleWidth;
        private float artistWidth;

        private void write(String titleText, Component artistLine) {
            titleRaw = titleText;
            title = Component.literal(titleText);
            artist = artistLine;
            artistRaw = artistLine.getString();
        }

        private void measure(GuiGraphics graphics, Font font) {
            artistRaw = artist.getString();
            pillTitleWidth = UiRender.measure(graphics, font, title, PILL_TITLE_SCALE);
            cardTitleWidth = UiRender.measure(graphics, font, title, CARD_TITLE_SCALE);
            artistWidth = UiRender.measure(graphics, font, artist, ARTIST_SCALE);
        }

        private void copy(Face other) {
            title = other.title;
            artist = other.artist;
            titleRaw = other.titleRaw;
            artistRaw = other.artistRaw;
            pillTitleWidth = other.pillTitleWidth;
            cardTitleWidth = other.cardTitleWidth;
            artistWidth = other.artistWidth;
        }

        Component title() {
            return title;
        }

        Component artist() {
            return artist;
        }

        String titleRaw() {
            return titleRaw;
        }

        String artistRaw() {
            return artistRaw;
        }

        float pillTitleWidth() {
            return pillTitleWidth;
        }

        float cardTitleWidth() {
            return cardTitleWidth;
        }

        float artistWidth() {
            return artistWidth;
        }
    }
}
