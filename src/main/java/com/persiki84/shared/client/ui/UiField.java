package com.persiki84.shared.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;

// WHY: ваниль рисует строку поля от его верхнего края (EditBox.renderWidget ставит y без бордера),
// WHY: поэтому под нашим стеклом текст висел шапкой, а курсором было подчёркивание из шрифта
public final class UiField {
    public static final float PAD_X = 4.0f;
    public static final float PAD_Y = 1.5f;

    private static final float TEXT_SCALE = 1.0f;
    private static final float CARET_WIDTH = 1.2f;
    private static final float CARET_GAP = 0.4f;
    private static final float CARET_MARGIN = 1.0f;
    private static final float CARET_SPEED = 26.0f;
    private static final float CARET_STRETCH = 0.5f;
    private static final float CARET_MAX_STRETCH = 3.0f;
    private static final float FOCUS_SPEED = 14.0f;
    private static final float SELECT_ALPHA = 0.3f;
    private static final float FAINT_ALPHA = 0.45f;
    private static final long CARET_HOLD_MS = 520L;
    private static final float CARET_BLINK_MS = 1150.0f;

    private static final Map<EditBox, State> states = new WeakHashMap<>();
    private static final Map<EditBox, Boolean> owned = new WeakHashMap<>();

    private UiField() {}

    // WHY: поле внутри строки списка не лежит в children() экрана, и сборщик перекраски его не
    // WHY: находит: без пометки такое поле рисует ваниль, то есть текст шапкой и курсор чёрточкой
    public static void own(EditBox box) {
        owned.put(box, Boolean.TRUE);
    }

    public static boolean owns(EditBox box) {
        return owned.containsKey(box);
    }

    public static float focus(EditBox box) {
        State state = states.get(box);
        return state == null ? 0.0f : UiAnim.easeOut(state.focus.get());
    }

    public static void render(GuiGraphics graphics, EditBox box, int displayPos, int cursorPos, int highlightPos,
                              int textColor, boolean editable, boolean bordered, String suggestion, Component hint,
                              BiFunction<String, Integer, FormattedCharSequence> formatter) {
        if (!box.isVisible()) return;

        State state = states.computeIfAbsent(box, key -> new State());
        float delta = UiFrame.delta();
        float focus = state.focus.to(box.isFocused() ? 1.0f : 0.0f, delta);

        float scale = UiRender.crisp(graphics, TEXT_SCALE);
        float inset = bordered ? PAD_X : 0.0f;
        float left = box.getX() + inset;
        float room = box.getWidth() - inset * 2.0f;

        String value = box.getValue();
        float[] widths = state.widths(graphics, value, scale);
        int start = firstShown(widths, displayPos, cursorPos, room);
        Window window = new Window(value, start, lastShown(widths, start, value.length(), room), widths,
                left, room, UiRender.centerY(box.getY(), box.getHeight(), scale), scale);

        state.follow(value, cursorPos);
        float alpha = focus * state.blink();
        float caret = window.caretX(state, cursorPos, delta);

        UiRender.clip(graphics, left, box.getY(), room, box.getHeight());
        try {
            paint(graphics, box, window, cursorPos, highlightPos, textColor, editable, suggestion,
                    hint, formatter);
        } finally {
            graphics.flush();
            graphics.disableScissor();
        }

        // WHY: курсор рисуется мимо ножниц, а зажат местом поля: под ножницами его левая половина
        // WHY: срезалась в ноль на первой позиции, и от полоски оставалась ступенька
        if (alpha > 0.01f) paintCaret(graphics, window, caret, alpha);
    }

    private static void paint(GuiGraphics graphics, EditBox box, Window window, int cursorPos,
                              int highlightPos, int textColor, boolean editable, String suggestion,
                              Component hint, BiFunction<String, Integer, FormattedCharSequence> formatter) {
        Font font = Minecraft.getInstance().font;
        int ink = ink(textColor, editable);

        if (highlightPos != cursorPos) paintSelection(graphics, window, cursorPos, highlightPos);

        String shown = window.text();
        float pen = window.left;
        if (!shown.isEmpty()) {
            UiRender.textLine(graphics, font, shape(formatter, shown, window.start), pen, window.textY,
                    window.scale, ink, false);
            pen += window.width(window.start, window.end);
        } else if (hint != null && !box.isFocused()) {
            UiRender.textLine(graphics, font, hint.getVisualOrderText(), pen, window.textY, window.scale,
                    UiTheme.alpha(ink, FAINT_ALPHA), false);
        }
        if (suggestion != null && !suggestion.isEmpty()) {
            UiRender.textLine(graphics, font, plain(suggestion), pen, window.textY, window.scale,
                    UiTheme.alpha(ink, FAINT_ALPHA), false);
        }
    }

    private static FormattedCharSequence shape(BiFunction<String, Integer, FormattedCharSequence> formatter,
                                               String shown, int start) {
        return formatter == null ? plain(shown) : formatter.apply(shown, start);
    }

    private static FormattedCharSequence plain(String value) {
        return FormattedCharSequence.forward(value, Style.EMPTY);
    }

    private static void paintSelection(GuiGraphics graphics, Window window, int cursorPos, int highlightPos) {
        int from = Math.max(window.start, Math.min(cursorPos, highlightPos));
        int to = Math.min(window.end, Math.max(cursorPos, highlightPos));
        if (to <= from) return;

        float height = window.lineHeight() + CARET_MARGIN * 2.0f;
        UiRender.panel(graphics, window.left + window.width(window.start, from), window.lineTop(),
                window.width(from, to), height, height / 2.0f,
                UiTheme.alpha(UiAccent.color(), SELECT_ALPHA));
    }

    // WHY: полоска мерится строкой, а не коробкой поля: у поиска в магазине и в списках коробка
    // WHY: сдвинута вниз под ванильную отрисовку, и курсор по её высоте вылезал из-под плашки
    private static void paintCaret(GuiGraphics graphics, Window window, float caret, float alpha) {
        bar(graphics, caret, window.lineTop(), window.caretWidth(),
                window.lineHeight() + CARET_MARGIN * 2.0f, UiTheme.alpha(UiAccent.color(), alpha));
    }

    private static void bar(GuiGraphics graphics, float x, float y, float width, float height, int color) {
        if (UiCrisp.ready()) {
            UiCrisp.panel(graphics, x, y, width, height, width / 2.0f, color);
            return;
        }
        UiRender.panel(graphics, x, y, width, height, width / 2.0f, color);
    }

    private static int ink(int textColor, boolean editable) {
        if (!editable) return UiAccent.textFaint();
        return textColor == EditBox.DEFAULT_TEXT_COLOR ? UiAccent.text() : 0xFF000000 | textColor;
    }

    private static int firstShown(float[] widths, int displayPos, int cursorPos, float room) {
        int start = Math.max(0, Math.min(displayPos, widths.length - 1));
        if (cursorPos < start) return Math.max(0, cursorPos);

        int caret = Math.min(cursorPos, widths.length - 1);
        while (start < caret && widths[caret] - widths[start] > room) {
            start++;
        }
        return start;
    }

    private static int lastShown(float[] widths, int start, int length, float room) {
        int end = start;
        while (end < length && widths[end + 1] - widths[start] <= room) {
            end++;
        }
        return end;
    }

    private static final class Window {
        private final String value;
        private final int start;
        private final int end;
        private final float[] widths;
        private final float left;
        private final float room;
        private final float textY;
        private final float scale;
        private float caretWidth = CARET_WIDTH;

        private Window(String value, int start, int end, float[] widths, float left, float room,
                       float textY, float scale) {
            this.value = value;
            this.start = start;
            this.end = end;
            this.widths = widths;
            this.left = left;
            this.room = room;
            this.textY = textY;
            this.scale = scale;
        }

        private String text() {
            return value.substring(start, end);
        }

        private float width(int from, int to) {
            return widths[to] - widths[from];
        }

        private float lineHeight() {
            return Minecraft.getInstance().font.lineHeight * scale;
        }

        private float lineTop() {
            return textY - CARET_MARGIN;
        }

        private float caretWidth() {
            return caretWidth;
        }

        // WHY: полоска стоит после точки вставки, а не по центру её: центрированная ложилась
        // WHY: половиной своей ширины на предыдущую букву и съедала тонкие штрихи
        private float caretX(State state, int cursorPos, float delta) {
            int place = Math.max(start, Math.min(end, cursorPos));
            float target = left + width(start, place) + CARET_GAP;
            float shown = state.caret.to(target, CARET_SPEED, delta);
            caretWidth = CARET_WIDTH + Math.min(CARET_MAX_STRETCH, Math.abs(target - shown) * CARET_STRETCH);

            return Math.max(left, Math.min(Math.min(shown, target), left + room - caretWidth));
        }
    }

    private static final class State {
        private final Smooth focus = new Smooth(0.0f, FOCUS_SPEED);
        private final Smooth caret = new Smooth(CARET_SPEED);
        private float[] widths = new float[] {0.0f};
        private String measured = "";
        private String shownValue = "";
        private float measuredScale;
        private int shownCursor;
        private long typedAt = System.currentTimeMillis();

        private float[] widths(GuiGraphics graphics, String value, float scale) {
            if (value.equals(measured) && Math.abs(scale - measuredScale) < 0.001f) return widths;

            Font font = Minecraft.getInstance().font;
            float[] built = new float[value.length() + 1];
            for (int index = 0; index < value.length(); index++) {
                built[index + 1] = built[index] + UiRender.measureLine(graphics, font,
                        plain(String.valueOf(value.charAt(index))), scale);
            }
            widths = built;
            measured = value;
            measuredScale = scale;
            return widths;
        }

        private void follow(String value, int cursorPos) {
            if (cursorPos == shownCursor && value.equals(shownValue)) return;

            shownValue = value;
            shownCursor = cursorPos;
            typedAt = System.currentTimeMillis();
        }

        private float blink() {
            long age = System.currentTimeMillis() - typedAt;
            if (age < CARET_HOLD_MS) return 1.0f;
            return (float) (0.5 + 0.5 * Math.cos((age - CARET_HOLD_MS) / CARET_BLINK_MS * Math.PI * 2.0));
        }
    }
}
