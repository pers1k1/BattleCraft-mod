package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.battlecraft.client.island.IslandLyrics;
import com.persiki84.battlecraft.client.lyrics.LyricLine;
import com.persiki84.battlecraft.client.lyrics.LyricText;
import com.persiki84.battlecraft.client.media.MediaTrack;
import com.persiki84.shared.client.ui.UiMorphText;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSweep;
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
    private static final int FIELD_LIMIT = 200;

    private final Face shown = new Face();
    private final IslandLyrics lyrics = new IslandLyrics();
    private boolean measuredEngaged;
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
        boolean swapping = false;
        if (!track.sameTrack(described)) {
            swapping = visible && described.present() && track.present();
            described = track;
            shown.write(titleOf(track), artistOf(track), swapping);
            measuredPixels = -1.0f;
        }
        shown.sing(lyrics.line(track, System.currentTimeMillis()), visible);
        shown.widened = lyrics.widen(delta);
        if (lyrics.engaged() != measuredEngaged) measuredPixels = -1.0f;
        return swapping;
    }

    // WHY: название и исполнитель приходят со страницы: чистятся от управляющих символов, знака
    // WHY: параграфа и залго и режутся по длине, иначе строка рисовалась бы тысячами глифов за кадр
    private static String titleOf(MediaTrack track) {
        String clean = LyricText.clean(track.title(), FIELD_LIMIT);
        return clean.isEmpty() ? track.origin() : clean;
    }

    private static String artistOf(MediaTrack track) {
        if (track.blind()) return NO_CONTROL.getString();
        String clean = LyricText.clean(track.artist(), FIELD_LIMIT);
        return clean.isEmpty() ? track.origin() : clean;
    }

    UiSweep sweep() {
        return lyrics.sweep();
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

        if (language != measuredLanguage && described.blind()) shown.relabel(NO_CONTROL.getString());
        measuredPixels = pixels;
        measuredLanguage = language;
        measuredEngaged = lyrics.engaged();
        shown.measure(graphics, font, lyrics);
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
        private float pillLyricWidth;
        private float cardLyricWidth;
        private float widened;
        private float artistWidth;
        private String titleText = "";
        private String artistText = "";
        private String pairedArtist = "";

        private void write(String titleText, String artistText, boolean morphing) {
            this.titleText = titleText;
            this.artistText = artistText;
            pairedArtist = titleText.isEmpty() ? artistText : artistText + " \u00b7 " + titleText;
            if (morphing) {
                title.set(titleText);
                artist.set(artistText);
                return;
            }
            title.snap(titleText);
            artist.snap(artistText);
        }

        private void relabel(String artistText) {
            this.artistText = artistText;
            artist.snap(artistText);
        }

        // WHY: пока поётся строка, она стоит на месте названия, а название уходит к исполнителю в
        // WHY: карточке. Вне глаз игрока строка встаёт сразу, без морфа
        private void sing(LyricLine line, boolean visible) {
            String nextTitle = line == null ? titleText : line.text();
            String nextArtist = line == null ? artistText : pairedArtist;
            if (!visible) {
                title.snap(nextTitle);
                artist.snap(nextArtist);
                return;
            }
            title.set(nextTitle, line == null ? 0.0f : IslandLyrics.pace(line));
            artist.set(nextArtist);
        }

        private void advance(float delta) {
            title.advance(delta);
            artist.advance(delta);
        }

        private void measure(GuiGraphics graphics, Font font, IslandLyrics lyrics) {
            pillTitleWidth = UiRender.measureToned(graphics, font, Component.literal(titleText), PILL_TITLE_SCALE);
            cardTitleWidth = UiRender.measureToned(graphics, font, Component.literal(titleText), CARD_TITLE_SCALE);
            pillLyricWidth = lyricWidth(graphics, font, PILL_TITLE_SCALE, lyrics, pillTitleWidth);
            cardLyricWidth = lyricWidth(graphics, font, CARD_TITLE_SCALE, lyrics, cardTitleWidth);
            artistWidth = artist.measure(graphics, font, ARTIST_SCALE);
        }

        // WHY: замер идёт только на смене трека и плотности пикселей, поэтому хранятся обе ширины: по
        // WHY: названию и по самой длинной строке песни. Между ними остров переходит плавно, когда
        // WHY: начинается первая строка и после последней
        private static float lyricWidth(GuiGraphics graphics, Font font, float scale, IslandLyrics lyrics, float titleWidth) {
            if (!lyrics.engaged()) return titleWidth;
            return Math.max(lyrics.widest(graphics, font, scale), titleWidth);
        }

        UiMorphText title() {
            return title;
        }

        UiMorphText artist() {
            return artist;
        }

        float pillTitleWidth() {
            return pillTitleWidth + (pillLyricWidth - pillTitleWidth) * widened;
        }

        float cardTitleWidth() {
            return cardTitleWidth + (cardLyricWidth - cardTitleWidth) * widened;
        }

        float artistWidth() {
            return artistWidth;
        }
    }
}
