package com.persiki84.shared.client.menu.studio;

import com.persiki84.shared.client.menu.GlidingRow;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.ScrollHint;
import com.persiki84.shared.client.menu.ScrollLanes;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiFrame;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.List;

// WHY: строки колонки раскладываются сверху вниз и едут прокруткой всей стопкой: у инспектора
// WHY: товара десяток строк, и на невысоком окне нижние уходили бы за кромку без доступа к ним
public final class StudioStack {
    private static final float GLIDE_SPEED = 19.0f;
    private static final int WHEEL_STEP = 26;
    private static final float APPEAR_SLIDE = 10.0f;

    private final ScrollLanes lanes = new ScrollLanes();
    private final Smooth glide = new Smooth(0.0f, GLIDE_SPEED);
    private final ScrollHint above = new ScrollHint();
    private final ScrollHint below = new ScrollHint();
    private final List<AbstractWidget> placed = new ArrayList<>();
    private Object subject;
    private int scroll;
    private int top;
    private int bottom;
    private int content;
    private int cursor;
    private int gap;
    private int shownIndex;
    private boolean fresh;

    public void begin(int stackTop, int stackBottom, int rowGap, Object shownSubject) {
        top = stackTop;
        bottom = stackBottom;
        gap = rowGap;
        cursor = 0;
        shownIndex = 0;
        placed.clear();
        fresh = subject == null || !subject.equals(shownSubject);
        if (fresh) {
            subject = shownSubject;
            scroll = 0;
            glide.snap(0.0f);
        }
    }

    public <T extends AbstractWidget> T add(T widget, int x) {
        return add(widget, x, 0);
    }

    public <T extends AbstractWidget> T add(T widget, int x, int extraGap) {
        cursor += extraGap;
        widget.setX(x);
        int y = top + cursor - scroll;
        if (widget instanceof GlidingRow row) {
            row.anchor(y, GlidingRow.Lane.ROWS);
        } else {
            widget.setY(y);
        }
        if (fresh && widget instanceof MenuRow row) row.stagger(shownIndex, APPEAR_SLIDE);
        shownIndex++;
        cursor += widget.getHeight() + gap;
        placed.add(widget);
        return widget;
    }

    public <T extends AbstractWidget> T beside(T widget, int x) {
        int y = top + cursor - gap - widget.getHeight() - scroll;
        widget.setX(x);
        if (widget instanceof GlidingRow row) {
            row.anchor(y, GlidingRow.Lane.ROWS);
        } else {
            widget.setY(y);
        }
        placed.add(widget);
        return widget;
    }

    public void skip(int space) {
        cursor += space;
    }

    public int cursor() {
        return top + cursor - scroll;
    }

    public void end() {
        content = Math.max(0, cursor - gap);
        scroll = clamp(scroll);
    }

    public List<AbstractWidget> placed() {
        return placed;
    }

    public boolean scrollBy(double amount) {
        int next = clamp(scroll + (amount > 0 ? -WHEEL_STEP : WHEEL_STEP));
        if (next == scroll) return false;
        glide.snap(glide.get() + next - scroll);
        scroll = next;
        return true;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(value, Math.max(0, content - (bottom - top))));
    }

    public void glide() {
        lanes.rows(glide.to(0.0f, UiFrame.delta()), top, bottom);
        for (AbstractWidget widget : placed) {
            if (widget instanceof GlidingRow row) row.glide(lanes);
        }
    }

    public void renderHints(GuiGraphics graphics, float centerX) {
        boolean overflow = content > bottom - top;
        above.render(graphics, centerX, top - ScrollHint.BAND_TOP / 2.0f, false, overflow && scroll > 0);
        below.render(graphics, centerX, bottom + ScrollHint.BAND_BOTTOM / 2.0f - 2.0f, true,
                overflow && scroll < content - (bottom - top));
    }

    public boolean covers(double mouseX, double mouseY, int left, int width) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < bottom;
    }
}
