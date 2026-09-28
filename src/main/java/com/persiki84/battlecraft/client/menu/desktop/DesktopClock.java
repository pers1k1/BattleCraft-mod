package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlassText;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// WHY: часы как на экране блокировки iOS 26: сверху обычная строка даты, под ней цифры из стекла.
// WHY: Смена цифры как numericText в iOS: старая уезжает вверх, гаснет и расплывается, новая
// WHY: приходит снизу из размытия, ширина места плавно переходит от старой цифры к новой,
// WHY: остальные цифры только сдвигаются
final class DesktopClock {
    private static final int SLOTS = 5;
    private static final char BLANK = ' ';
    private static final long MINUTE_MILLIS = 60_000L;
    private static final float ROLL_SECONDS = 0.45f;
    private static final float ROLL_TRAVEL = 0.6f;
    private static final float APPEAR_SECONDS = 0.3f;
    private static final float DATE_SCALE = 1.15f;
    private static final float DATE_GAP = 4.0f;
    private static final float TEXT_HEIGHT = 9.0f;
    private static final float FIGURE_SHARE = 0.8f;
    private static final DateTimeFormatter TWELVE = DateTimeFormatter.ofPattern("h:mm");
    private static final DateTimeFormatter TWENTY_FOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final char[] shown = new char[SLOTS];
    private final char[] leaving = new char[SLOTS];
    private final float[] roll = new float[SLOTS];
    private final UiGlassText.Painter painter = this::paintDigits;
    private final DesktopClockStyle style = new DesktopClockStyle();
    private boolean primed;
    private float appear;
    private float paintLeft;
    private float paintTop;
    private float paintFigure;
    private float paintWeight;
    private float boundTop;
    private long shownMinute = Long.MIN_VALUE;
    private String shownLanguage = "";
    private Component date = Component.empty();
    private Component plainTime = Component.empty();
    private int dateDay = -1;
    private long stamp = -1L;

    DesktopClock() {
        Arrays.fill(shown, BLANK);
        Arrays.fill(leaving, BLANK);
        Arrays.fill(roll, 1.0f);
    }

    float height(float scale) {
        return TEXT_HEIGHT * DATE_SCALE + DATE_GAP + TEXT_HEIGHT * scale;
    }

    void render(GuiGraphics graphics, Font font, float centerX, float top, float scale, float alpha) {
        advance();
        UiRender.emphasisCentered(graphics, font, date, centerX, top, DATE_SCALE,
                UiTheme.alpha(UiAccent.text(), alpha * 0.92f));
        float line = TEXT_HEIGHT * scale;
        boundTop = top;
        paintWeight = style.weight();
        paintFigure = line * FIGURE_SHARE;
        paintTop = top + TEXT_HEIGHT * DATE_SCALE + DATE_GAP + (line - paintFigure) / 2.0f;
        paintLeft = centerX - rowAdvance() * paintFigure / 2.0f;
        if (UiGlassText.unavailable()) {
            UiRender.emphasisCentered(graphics, font, plainTime, centerX, top + TEXT_HEIGHT * DATE_SCALE + DATE_GAP,
                    scale, UiTheme.alpha(UiAccent.text(), alpha * appear));
            return;
        }
        UiGlassText.draw(graphics, alpha * appear, style.look(), painter);
    }

    // WHY: рамка часов берётся с последнего кадра без сдвига сна: во сне щелчок только будит экран
    boolean contains(double mouseX, double mouseY) {
        float right = paintLeft + rowAdvance() * paintFigure;
        return mouseX >= paintLeft && mouseX < right && mouseY >= boundTop && mouseY < paintTop + paintFigure;
    }

    List<StudioMenu.Action> menu() {
        return style.menu();
    }

    private float rowAdvance() {
        float sum = 0.0f;
        for (int slot = 0; slot < SLOTS; slot++) sum += slotAdvance(slot);
        return sum;
    }

    private float slotAdvance(int slot) {
        float from = UiGlassText.advance(leaving[slot], paintWeight);
        return from + (UiGlassText.advance(shown[slot], paintWeight) - from) * eased(slot);
    }

    private float eased(int slot) {
        float rest = 1.0f - roll[slot];
        return 1.0f - rest * rest * rest;
    }

    private void paintDigits(UiGlassText.Glyphs glyphs) {
        float pen = paintLeft;
        float travel = paintFigure * ROLL_TRAVEL;
        for (int slot = 0; slot < SLOTS; slot++) {
            float eased = eased(slot);
            float width = slotAdvance(slot) * paintFigure;
            glyphs.glyph(leaving[slot], centred(pen, width, leaving[slot]), paintTop - travel * eased, paintFigure,
                    1.0f - eased, eased);
            glyphs.glyph(shown[slot], centred(pen, width, shown[slot]), paintTop + travel * (1.0f - eased),
                    paintFigure, eased, 1.0f - eased);
            pen += width;
        }
    }

    private float centred(float pen, float width, char glyph) {
        return pen + (width - UiGlassText.advance(glyph, paintWeight) * paintFigure) / 2.0f;
    }

    private void advance() {
        long frame = UiFrame.frame();
        if (frame == stamp) return;
        stamp = frame;
        fadeIn(UiFrame.delta());
        style.advance(UiFrame.delta());
        for (int slot = 0; slot < SLOTS; slot++) {
            roll[slot] = Math.min(1.0f, roll[slot] + UiFrame.delta() / ROLL_SECONDS);
        }
        long minute = Math.floorDiv(System.currentTimeMillis(), MINUTE_MILLIS);
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (minute != shownMinute || !language.equals(shownLanguage)) refresh(minute, language);
    }

    // WHY: поле цифр строится в фоне при загрузке ресурсов и почти всегда готово к первому кадру;
    // WHY: если нет, цифры проявляются, а не выскакивают. Если поле не собралось совсем (JRE без
    // WHY: шрифтовой подсистемы), время рисуется обычным крупным текстом интерфейса
    private void fadeIn(float delta) {
        boolean ready = UiGlassText.ready() || UiGlassText.unavailable();
        if (!primed) {
            primed = true;
            appear = ready ? 1.0f : 0.0f;
            return;
        }
        if (ready) appear = Math.min(1.0f, appear + delta / APPEAR_SECONDS);
    }

    private void refresh(long minute, String language) {
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(twelveHours() ? TWELVE : TWENTY_FOUR);
        remember(time, shownMinute == Long.MIN_VALUE);
        plainTime = Component.literal(time);
        shownMinute = minute;
        if (now.getDayOfYear() != dateDay || !language.equals(shownLanguage)) {
            dateDay = now.getDayOfYear();
            date = dateLine(now);
        }
        shownLanguage = language;
    }

    private void remember(String text, boolean first) {
        int blank = SLOTS - text.length();
        for (int slot = 0; slot < SLOTS; slot++) {
            char glyph = slot < blank ? BLANK : text.charAt(slot - blank);
            if (glyph == shown[slot]) continue;
            leaving[slot] = first ? BLANK : shown[slot];
            shown[slot] = glyph;
            roll[slot] = first ? 1.0f : 0.0f;
        }
    }

    private static Component dateLine(LocalDateTime now) {
        Locale locale = locale();
        String pattern = locale.getLanguage().equals("en") ? "EEE MMM d" : "EEE, d MMM";
        String text = now.format(DateTimeFormatter.ofPattern(pattern, locale));
        return Component.literal(Character.toUpperCase(text.charAt(0)) + text.substring(1));
    }

    static boolean twelveHours() {
        return Minecraft.getInstance().getLanguageManager().getSelected().startsWith("en_us");
    }

    static Locale locale() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        int split = code.indexOf('_');
        return split > 0 ? new Locale(code.substring(0, split), code.substring(split + 1)) : new Locale(code);
    }
}
