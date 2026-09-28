package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.shared.client.ui.UiIcon;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// WHY: строка меню macOS 27 прозрачная: пункты лежат прямо на обоях, а наведение подсвечивает
// WHY: пункт стеклянной капсулой, которая переезжает между пунктами, а не мигает на каждом заново
final class DesktopMenuBar {
    static final float HEIGHT = 13.0f;

    enum Slot { BRAND, GAME, VIEW, SEARCH, CENTER, CLOCK }

    interface Handler {
        void pick(Slot slot, float anchorX, float anchorY);
    }

    private record Place(Slot slot, float left, float width) {
    }

    private static final Slot[] SLOTS = Slot.values();
    private static final float SIDE = 6.0f;
    private static final float ITEM_PAD = 5.0f;
    private static final float TEXT_SCALE = 0.85f;
    private static final float ICON = 9.0f;
    private static final float HELD_LIFT = 0.6f;
    private static final float HELD_SPEED = 16.0f;
    private static final float ISLAND_GAP = 8.0f;
    private static final long MINUTE_MILLIS = 60_000L;
    // WHY: строка меню держит тот же счёт часов, что и крупные часы стола: 12 часов только у en_us
    private static final String TWELVE_HOUR_LINE = "EE MMM d  h:mm a";
    private static final String DAY_HOUR_LINE = "EE d MMM  HH:mm";
    private static final Component GAME = Component.translatable("battlecraft.desktop.bar.game");
    private static final Component VIEW = Component.translatable("battlecraft.desktop.bar.view");

    private final Handler handler;
    private final Smooth pillX = new Smooth(0.0f, 20.0f);
    private final Smooth pillWidth = new Smooth(0.0f, 20.0f);
    private final Smooth pillShown = new Smooth(0.0f, 16.0f);
    private final List<Place> places = new ArrayList<>();
    private final Smooth[] held = new Smooth[SLOTS.length];
    private final boolean[] holding = new boolean[SLOTS.length];
    private Slot hovered;
    private long stamp = -1L;
    private Component clockLine = Component.empty();
    private long clockMinute = Long.MIN_VALUE;
    private float laidWidth = -1.0f;
    private Language laidLanguage;

    DesktopMenuBar(Handler handler) {
        this.handler = handler;
        for (int index = 0; index < SLOTS.length; index++) held[index] = new Smooth(0.0f, HELD_SPEED);
    }

    // WHY: пункт, чья панель открыта, держит подсветку, как значок центра управления в macOS:
    // WHY: видно, откуда выросла панель и куда щёлкнуть, чтобы её закрыть
    void hold(Slot slot, boolean value) {
        holding[slot.ordinal()] = value;
    }

    // WHY: ушедший экран не двигает подсветку, и возврат на стол начинался бы с угасания пункта,
    // WHY: чья панель или меню увели на другой экран
    void forget() {
        for (Slot slot : SLOTS) {
            holding[slot.ordinal()] = false;
            held[slot.ordinal()].snap(0.0f);
        }
        pillShown.snap(0.0f);
    }

    void render(GuiGraphics graphics, Font font, float screenWidth, int mouseX, int mouseY, float alpha) {
        layout(font, screenWidth);
        advance(mouseX, mouseY);
        paintHeld(graphics, alpha);
        if (pillShown.get() > 0.01f) {
            UiGlass.window(graphics, pillX.get(), 1.0f, pillWidth.get(), HEIGHT - 2.0f, (HEIGHT - 2.0f) / 2.0f,
                    pillShown.get() * alpha, 0.4f);
        }
        for (Place place : places) paintPlace(graphics, font, place, alpha);
    }

    private void paintHeld(GuiGraphics graphics, float alpha) {
        for (Place place : places) {
            float lit = held[place.slot().ordinal()].get();
            if (lit <= 0.01f) continue;
            UiGlass.window(graphics, place.left(), 1.0f, place.width(), HEIGHT - 2.0f, (HEIGHT - 2.0f) / 2.0f,
                    lit * alpha, HELD_LIFT);
        }
    }

    // WHY: раскладка и строка часов пересобираются только при смене минуты, ширины экрана или языка,
    // WHY: а не каждый кадр: форматирование даты и замер текста в кадре стоили бы аллокаций
    private void layout(Font font, float screenWidth) {
        long minute = Math.floorDiv(System.currentTimeMillis(), MINUTE_MILLIS);
        Language language = Language.getInstance();
        if (minute == clockMinute && screenWidth == laidWidth && language == laidLanguage) return;
        clockMinute = minute;
        laidWidth = screenWidth;
        laidLanguage = language;
        String pattern = DesktopClock.twelveHours() ? TWELVE_HOUR_LINE : DAY_HOUR_LINE;
        clockLine = Component.literal(LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern,
                DesktopClock.locale())));
        places.clear();
        float x = SIDE;
        x = add(Slot.BRAND, x, ICON);
        x = add(Slot.GAME, x, textWidth(font, label(Slot.GAME)));
        add(Slot.VIEW, x, textWidth(font, label(Slot.VIEW)));
        float clockWidth = textWidth(font, clockLine);
        float right = screenWidth - SIDE - clockWidth - ITEM_PAD * 2.0f;
        add(Slot.CLOCK, right, clockWidth);
        right -= ICON + ITEM_PAD * 2.0f;
        add(Slot.CENTER, right, ICON);
        add(Slot.SEARCH, right - ICON - ITEM_PAD * 2.0f, ICON);
    }

    private float add(Slot slot, float left, float content) {
        float width = content + ITEM_PAD * 2.0f;
        places.add(new Place(slot, left, width));
        return left + width;
    }

    private static float textWidth(Font font, Component text) {
        return UiRender.width(font, text) * TEXT_SCALE;
    }

    private void paintPlace(GuiGraphics graphics, Font font, Place place, float alpha) {
        float centreX = place.left() + place.width() / 2.0f;
        int ink = UiTheme.alpha(UiAccent.text(), alpha);
        switch (place.slot()) {
            case BRAND -> glyph(graphics, UiIcon.Kind.SPARKLE, centreX, alpha);
            case SEARCH -> glyph(graphics, UiIcon.Kind.SEARCH, centreX, alpha);
            case CENTER -> glyph(graphics, UiIcon.Kind.TOGGLES, centreX, alpha);
            case CLOCK -> UiRender.textCentered(graphics, font, clockLine, centreX, 3.0f, TEXT_SCALE, ink, false);
            default -> UiRender.textCentered(graphics, font, label(place.slot()), centreX, 3.0f, TEXT_SCALE,
                    ink, false);
        }
    }

    private void glyph(GuiGraphics graphics, UiIcon.Kind glyph, float centreX, float alpha) {
        UiIcon.draw(graphics, glyph, centreX, HEIGHT / 2.0f, ICON * 0.8f, UiTheme.alpha(UiAccent.text(), alpha));
    }

    private static Component label(Slot slot) {
        return slot == Slot.GAME ? GAME : VIEW;
    }

    private void advance(int mouseX, int mouseY) {
        long frame = UiFrame.frame();
        if (frame == stamp) return;
        stamp = frame;
        float delta = UiFrame.delta();
        for (Slot slot : SLOTS) held[slot.ordinal()].to(holding[slot.ordinal()] ? 1.0f : 0.0f, delta);
        Place over = placeAt(mouseX, mouseY);
        if (over != null && over.slot() != hovered) UiSound.hover();
        hovered = over == null ? null : over.slot();
        if (over != null && pillShown.get() < 0.02f) {
            pillX.snap(over.left());
            pillWidth.snap(over.width());
        }
        if (over != null) {
            pillX.to(over.left(), delta);
            pillWidth.to(over.width(), delta);
        }
        pillShown.to(over == null ? 0.0f : 1.0f, delta);
    }

    private Place placeAt(double mouseX, double mouseY) {
        if (mouseY < 0.0 || mouseY > HEIGHT) return null;
        for (Place place : places) {
            if (mouseX >= place.left() && mouseX < place.left() + place.width()) return place;
        }
        return null;
    }

    boolean click(double mouseX, double mouseY) {
        Place place = placeAt(mouseX, mouseY);
        if (place == null) return false;
        handler.pick(place.slot(), place.left(), HEIGHT + 2.0f);
        return true;
    }

    float anchorOf(Slot slot) {
        for (Place place : places) {
            if (place.slot() == slot) return place.left() + place.width();
        }
        return 0.0f;
    }

    // WHY: остров стоит в строке меню по центру экрана, и его таблетка не должна наезжать на пункты:
    // WHY: ширина ограничена тем краем, левой группы или правой, что ближе к середине
    float room(float middle) {
        float left = 0.0f;
        float right = middle * 2.0f;
        for (Place place : places) {
            float end = place.left() + place.width();
            if (end <= middle) left = Math.max(left, end);
            else right = Math.min(right, place.left());
        }
        return 2.0f * (Math.min(middle - left, right - middle) - ISLAND_GAP);
    }

    float middleOf(Slot slot) {
        for (Place place : places) {
            if (place.slot() == slot) return place.left() + place.width() / 2.0f;
        }
        return 0.0f;
    }
}
