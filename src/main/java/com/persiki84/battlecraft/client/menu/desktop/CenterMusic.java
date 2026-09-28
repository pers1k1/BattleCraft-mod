package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.battlecraft.client.island.IslandArt;
import com.persiki84.battlecraft.client.island.IslandFace;
import com.persiki84.battlecraft.client.island.IslandGlyph;
import com.persiki84.battlecraft.client.island.IslandImage;
import com.persiki84.battlecraft.client.media.MediaTrack;
import com.persiki84.battlecraft.client.media.MediaWatch;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiIcon;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// WHY: мини-плеер центра управления: обложка, название и исполнитель бегущей строкой, полоса
// WHY: перемотки и кнопки плеера через мост. Без трека плитка не прячется и не меняет высоту,
// WHY: а показывает заглушку: сетка центра не должна прыгать от того, играет ли музыка
final class CenterMusic {
    static final float HEIGHT = 68.0f;

    private static final float RADIUS = 14.0f;
    private static final float INSET = 7.0f;
    private static final float COVER = 32.0f;
    private static final float TEXT_GAP = 7.0f;
    private static final float TITLE_SCALE = 0.8f;
    private static final float ARTIST_SCALE = 0.7f;
    private static final float TITLE_TOP = 10.0f;
    private static final float ARTIST_TOP = 21.0f;
    private static final float VISUALIZER_DROP = 8.5f;
    private static final float VISUALIZER_GAP = 5.0f;
    private static final float BAR_TOP = 47.0f;
    private static final float ROW_TOP = 59.0f;
    private static final float NOTE_SHARE = 0.5f;
    private static final Component SILENCE = Component.translatable("battlecraft.desktop.center.silence");
    private static final Component NO_CONTROL = Component.translatable("battlecraft.desktop.island.no_control");
    private static final Component BLANK = Component.empty();

    private final CenterLabel titleLabel = new CenterLabel(TITLE_SCALE);
    private final CenterLabel artistLabel = new CenterLabel(ARTIST_SCALE);
    private final CenterScrub scrub = new CenterScrub();
    private final CenterTransport transport = new CenterTransport();
    private MediaTrack held = MediaTrack.NONE;
    private Component title = SILENCE;
    private Component artist = BLANK;
    private float left;
    private float top;
    private float width;

    void place(float x, float y, float w) {
        left = x;
        top = y;
        width = w;
        scrub.place(x + INSET, y + BAR_TOP, w - INSET * 2.0f);
        transport.place(x + w / 2.0f, y + ROW_TOP);
    }

    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float alpha, float delta) {
        follow();
        scrub.advance(held, mouseX, mouseY, delta);
        transport.advance(mouseX, mouseY, delta);
        UiGlass.window(graphics, left, top, width, HEIGHT, RADIUS, alpha, 0.2f);
        paintCover(graphics, alpha);
        paintText(graphics, font, alpha);
        if (held.present()) {
            IslandGlyph.cardBars(graphics, left + width - INSET - IslandGlyph.CARD_WIDTH / 2.0f,
                    top + TITLE_TOP + VISUALIZER_DROP, alpha);
        }
        scrub.paint(graphics, UiAccent.text(), alpha);
        scrub.paintTimes(graphics, font, top + ROW_TOP, UiAccent.textDim(), alpha);
        transport.paint(graphics, UiAccent.text(), alpha);
    }

    // WHY: в меню HUD не рисуется, и модель острова, обложка и визуализатор стоят, пока их не
    // WHY: продвинет тот, кто их показывает. Модель защёлкнута на кадр, двойной ход ей не страшен.
    // WHY: Полоски те же, что на карточке острова HUD: размеры общие, свои пропорции их искажали
    private void follow() {
        IslandGlyph.drive();
        MediaTrack live = MediaWatch.current();
        if (live.sameTrack(held) && live.present() == held.present()) {
            held = live;
            return;
        }
        held = live;
        title = live.present() ? Component.literal(live.title().isEmpty() ? live.origin() : live.title()) : SILENCE;
        artist = live.present() ? artistOf(live) : BLANK;
    }

    private static Component artistOf(MediaTrack track) {
        if (track.blind()) return NO_CONTROL;
        return Component.literal(track.artist().isEmpty() ? track.origin() : track.artist());
    }

    private void paintText(GuiGraphics graphics, Font font, float alpha) {
        float textX = left + INSET + COVER + TEXT_GAP;
        float full = left + width - INSET - textX;
        float titleSlot = held.present() ? full - IslandGlyph.CARD_WIDTH - VISUALIZER_GAP : full;
        titleLabel.paint(graphics, font, title, textX, top + TITLE_TOP, titleSlot,
                UiTheme.alpha(UiAccent.text(), alpha));
        artistLabel.paint(graphics, font, artist, textX, top + ARTIST_TOP, full,
                UiTheme.alpha(UiAccent.textDim(), alpha));
    }

    // WHY: пока обложка ждёт моста, место держит пустая площадка, а нота встаёт только у трека без
    // WHY: обложки или без трека вовсе: иначе на каждой смене песни нота мелькала бы перед картинкой
    private void paintCover(GuiGraphics graphics, float alpha) {
        float centerX = left + INSET + COVER / 2.0f;
        float centerY = top + INSET + COVER / 2.0f;
        if (held.present() && !held.blind() && IslandArt.ready()) {
            IslandFace.draw(graphics, centerX, centerY, COVER, alpha, true, false);
            return;
        }
        UiRender.panel(graphics, left + INSET, top + INSET, COVER, COVER, COVER * IslandImage.CORNER_SHARE,
                UiTheme.alpha(UiAccent.color(), 0.5f * alpha));
        if (!held.present() || held.blind() || !IslandArt.pending()) {
            UiIcon.draw(graphics, UiIcon.Kind.NOTE, centerX, centerY, COVER * NOTE_SHARE,
                    UiTheme.alpha(UiTheme.WHITE, alpha));
        }
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= left && mouseX <= left + width && mouseY >= top && mouseY <= top + HEIGHT;
    }

    boolean click(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) return false;
        if (transport.click(mouseX, mouseY)) return true;
        scrub.press(mouseX, mouseY);
        return true;
    }

    void drag(double mouseX) {
        scrub.drag(mouseX);
    }

    boolean release() {
        return scrub.release(held);
    }

    void forget() {
        scrub.cancel();
        transport.forget();
    }
}
