package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Spring;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiIcon;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

// WHY: три вида плиток центра управления macOS: строка внутри общего модуля (кружок, название,
// WHY: состояние), отдельная плитка со значком сверху и подписью снизу и круглая кнопка.
// WHY: У всех одно поведение: мягкое наведение, короткое утапливание на пружине, заливка
// WHY: кружка цветом акцента перетекает, а не мигает
final class CenterToggle {
    enum Shape { ROW, TILE, ROUND }

    static final Component ON = Component.translatable("battlecraft.desktop.center.on");
    static final Component OFF = Component.translatable("battlecraft.desktop.center.off");
    private static final Component BLANK = Component.empty();

    private static final float TITLE_SCALE = 0.72f;
    private static final float CAPTION_SCALE = 0.62f;
    private static final float PRESS_DEPTH = 0.04f;
    private static final float ROW_INSET = 2.0f;
    private static final float ROW_TEXT_GAP = 6.0f;
    private static final float ROW_RADIUS = 8.0f;
    private static final float ROW_HOVER_ALPHA = 0.08f;
    private static final float TILE_INSET = 6.0f;
    private static final float TILE_CIRCLE = 20.0f;
    private static final float TILE_RADIUS = 12.0f;
    private static final float TILE_TEXT_GAP = 5.0f;
    private static final float CAPTION_LEAD = 8.5f;
    private static final float GLYPH_SHARE = 0.46f;

    private final Shape shape;
    private final UiIcon.Kind glyph;
    private final Component title;
    private final BooleanSupplier state;
    private final Supplier<Component> caption;
    private final Runnable flip;
    private final CenterLabel titleLabel = new CenterLabel(TITLE_SCALE);
    private final CenterLabel captionLabel = new CenterLabel(CAPTION_SCALE);
    private final Spring press = new Spring(0.3f, 0.6f, 0.0f);
    private final Smooth lit = new Smooth(0.0f, 14.0f);
    private final Smooth hover = new Smooth(0.0f, 16.0f);
    private float left;
    private float top;
    private float width;
    private float height;

    private CenterToggle(Shape shape, UiIcon.Kind glyph, Component title, BooleanSupplier state,
                         Supplier<Component> caption, Runnable flip) {
        this.shape = shape;
        this.glyph = glyph;
        this.title = title;
        this.state = state;
        this.caption = caption;
        this.flip = flip;
    }

    static CenterToggle row(UiIcon.Kind glyph, Component title, BooleanSupplier state, Runnable flip) {
        return new CenterToggle(Shape.ROW, glyph, title, state, () -> state.getAsBoolean() ? ON : OFF, flip);
    }

    static CenterToggle tile(UiIcon.Kind glyph, Component title, Supplier<Component> caption, Runnable pick) {
        return new CenterToggle(Shape.TILE, glyph, title, null, caption, pick);
    }

    static CenterToggle round(UiIcon.Kind glyph, BooleanSupplier state, Runnable flip) {
        return new CenterToggle(Shape.ROUND, glyph, BLANK, state, () -> BLANK, flip);
    }

    static CenterToggle action(UiIcon.Kind glyph, Runnable run) {
        return new CenterToggle(Shape.ROUND, glyph, BLANK, null, () -> BLANK, run);
    }

    void place(float x, float y, float w, float h) {
        left = x;
        top = y;
        width = w;
        height = h;
    }

    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float alpha, float delta) {
        hover.to(contains(mouseX, mouseY) ? 1.0f : 0.0f, delta);
        lit.to(lit() ? 1.0f : 0.0f, delta);
        press.to(0.0f, delta);
        float scale = 1.0f - press.get() * PRESS_DEPTH;
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0f);
        UiRestFrame.push(graphics, width / 2.0f, height / 2.0f, scale, scale, 0.0f, 0.0f);
        try {
            paint(graphics, font, alpha);
        } finally {
            UiRestFrame.pop(graphics);
            graphics.pose().popPose();
        }
    }

    private boolean lit() {
        if (shape == Shape.TILE) return true;
        return state != null && state.getAsBoolean();
    }

    private void paint(GuiGraphics graphics, Font font, float alpha) {
        switch (shape) {
            case ROW -> paintRow(graphics, font, alpha);
            case TILE -> paintTile(graphics, font, alpha);
            case ROUND -> paintCircle(graphics, 0.0f, 0.0f, Math.min(width, height), alpha);
        }
    }

    private void paintRow(GuiGraphics graphics, Font font, float alpha) {
        if (hover.get() > 0.01f) {
            UiRender.panel(graphics, 0.0f, 0.0f, width, height, ROW_RADIUS,
                    UiTheme.withAlpha(UiTheme.WHITE, ROW_HOVER_ALPHA * hover.get() * alpha));
        }
        float circle = height - ROW_INSET * 2.0f;
        paintCircle(graphics, ROW_INSET, ROW_INSET, circle, alpha);
        float textX = ROW_INSET + circle + ROW_TEXT_GAP;
        paintLines(graphics, font, textX, height / 2.0f - 7.0f, width - textX - ROW_INSET, alpha);
    }

    private void paintTile(GuiGraphics graphics, Font font, float alpha) {
        UiGlass.window(graphics, 0.0f, 0.0f, width, height, TILE_RADIUS, alpha, 0.2f + hover.get() * 0.3f);
        paintCircle(graphics, TILE_INSET, TILE_INSET, TILE_CIRCLE, alpha);
        float textTop = TILE_INSET + TILE_CIRCLE + TILE_TEXT_GAP;
        paintLines(graphics, font, TILE_INSET, textTop, width - TILE_INSET * 2.0f, alpha);
    }

    private void paintLines(GuiGraphics graphics, Font font, float x, float y, float slot, float alpha) {
        titleLabel.paint(graphics, font, title, x, y, slot, UiTheme.alpha(UiAccent.text(), alpha));
        captionLabel.paint(graphics, font, caption.get(), x, y + CAPTION_LEAD, slot,
                UiTheme.alpha(UiAccent.textDim(), alpha));
    }

    private void paintCircle(GuiGraphics graphics, float x, float y, float size, float alpha) {
        float glass = shape == Shape.ROUND ? 0.3f + hover.get() * 0.4f : 0.3f;
        UiGlass.window(graphics, x, y, size, size, size / 2.0f, alpha, glass);
        UiRender.panel(graphics, x, y, size, size, size / 2.0f,
                UiTheme.alpha(UiAccent.color(), lit.get() * alpha * 0.95f));
        int ink = UiTheme.mix(UiAccent.text(), UiTheme.WHITE, lit.get());
        UiIcon.draw(graphics, glyph, x + size / 2.0f, y + size / 2.0f, size * GLYPH_SHARE, UiTheme.alpha(ink, alpha));
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= left && mouseX <= left + width && mouseY >= top && mouseY <= top + height;
    }

    boolean click(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) return false;
        press.snap(1.0f);
        flip.run();
        if (state == null) {
            UiSound.press();
        } else {
            UiSound.toggle(state.getAsBoolean());
        }
        return true;
    }
}
