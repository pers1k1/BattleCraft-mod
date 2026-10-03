package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.battlecraft.client.media.MediaTrack;
import com.persiki84.shared.client.ui.UiMorphText;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

// WHY: строки острова и их ширины пересобираются только на смене трека, секунды или плотности
// WHY: пикселей: замер текста раскладывает глифы, и делать его в каждом кадре было бы накладно.
// WHY: На смене трека и секунды строка меняется по буквам (UiMorphText), не подменяясь кадром
final class DesktopIslandText {
    static final float PILL_TITLE_SCALE = 0.75f;
    static final float CARD_TITLE_SCALE = 0.86f;
    static final float ARTIST_SCALE = 0.72f;
    static final float TIME_SCALE = 0.56f;

    private static final Component NO_CONTROL = Component.translatable("battlecraft.desktop.island.no_control");
    private static final Component TIME_SAMPLE = Component.literal("-00:00");

    private final Face shown = new Face();
    private final UiMorphText elapsed = new UiMorphText();
    private final UiMorphText remaining = new UiMorphText();
    private MediaTrack described = MediaTrack.NONE;
    private long elapsedSeconds = -1L;
    private long remainingSeconds = -1L;
    private float measuredPixels = -1.0f;
    private Language measuredLanguage;
    private float timeSlot;

    // WHY: смена по буквам нужна только на глазах у игрока: остров, вернувшийся после тишины, не
    // WHY: должен сначала показать давно ушедший трек, чтобы тут же его переписать
    boolean describe(MediaTrack track, float delta, boolean visible) {
        shown.advance(delta);
        elapsed.advance(delta);
        remaining.advance(delta);
        if (track.sameTrack(described)) return false;

        boolean swapping = visible && described.present() && track.present();
        described = track;
        shown.write(track.title().isEmpty() ? track.origin() : track.title(), artistOf(track), swapping);
        measuredPixels = -1.0f;
        return swapping;
    }

    private static String artistOf(MediaTrack track) {
        if (track.blind()) return NO_CONTROL.getString();
        return track.artist().isEmpty() ? track.origin() : track.artist();
    }

    void time(long elapsedMs, long durationMs) {
        long played = Math.max(0L, elapsedMs / 1000L);
        long left = Math.max(0L, (durationMs - elapsedMs + 999L) / 1000L);
        if (played != elapsedSeconds) {
            elapsedSeconds = played;
            elapsed.set(clock(played));
        }
        if (left != remainingSeconds) {
            remainingSeconds = left;
            remaining.set("-" + clock(left));
        }
    }

    void measure(GuiGraphics graphics, Font font) {
        float pixels = UiRender.pixels(graphics);
        Language language = Language.getInstance();
        if (pixels == measuredPixels && language == measuredLanguage) return;

        if (language != measuredLanguage && described.blind()) shown.artist.snap(NO_CONTROL.getString());
        measuredPixels = pixels;
        measuredLanguage = language;
        shown.measure(graphics, font);
        timeSlot = UiRender.measureToned(graphics, font, TIME_SAMPLE, TIME_SCALE);
    }

    private static String clock(long seconds) {
        return seconds / 60L + (seconds % 60L < 10L ? ":0" : ":") + seconds % 60L;
    }

    Face shown() {
        return shown;
    }

    UiMorphText elapsed() {
        return elapsed;
    }

    UiMorphText remaining() {
        return remaining;
    }

    float timeSlot() {
        return timeSlot;
    }

    static final class Face {
        private final UiMorphText title = new UiMorphText();
        private final UiMorphText artist = new UiMorphText();
        private float pillTitleWidth;
        private float cardTitleWidth;
        private float artistWidth;

        private void write(String titleText, String artistText, boolean morphing) {
            if (morphing) {
                title.set(titleText);
                artist.set(artistText);
                return;
            }
            title.snap(titleText);
            artist.snap(artistText);
        }

        private void advance(float delta) {
            title.advance(delta);
            artist.advance(delta);
        }

        private void measure(GuiGraphics graphics, Font font) {
            pillTitleWidth = title.measure(graphics, font, PILL_TITLE_SCALE);
            cardTitleWidth = title.measure(graphics, font, CARD_TITLE_SCALE);
            artistWidth = artist.measure(graphics, font, ARTIST_SCALE);
        }

        UiMorphText title() {
            return title;
        }

        UiMorphText artist() {
            return artist;
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
