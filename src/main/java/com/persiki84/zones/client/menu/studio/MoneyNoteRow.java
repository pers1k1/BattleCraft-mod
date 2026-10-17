package com.persiki84.zones.client.menu.studio;

import com.persiki84.shared.client.menu.GlidingRow;
import com.persiki84.shared.client.menu.RowAnchor;
import com.persiki84.shared.client.menu.ScrollLanes;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

// WHY: предупреждение перед необратимым шагом не влезает в подпись строки: оно стоит в колонке
// WHY: свободным текстом в несколько строк на утопленной плашке. Строки режутся пером, которым
// WHY: рисуются, один раз на смену текста или ширины, и проявляются, а не встают кадром
final class MoneyNoteRow extends AbstractWidget implements GlidingRow {
    private static final float SCALE = 0.66f;
    private static final float LEADING = 1.5f;
    private static final float APPEAR_MS = 220.0f;
    private static final int LINES = 4;
    private static final float ROW_RADIUS = 22.0f;

    private final RowAnchor anchor = new RowAnchor();
    private final List<Component> lines = new ArrayList<>();
    private Component text = Component.empty();
    private Component splitFor;
    private int splitWidth = -1;
    private boolean alerting;
    private long shownAt = System.currentTimeMillis();

    MoneyNoteRow(int height) {
        super(0, 0, 10, height, Component.empty());
        this.active = false;
    }

    void show(Component shown, boolean alert) {
        if (shown != text) shownAt = System.currentTimeMillis();
        text = shown;
        alerting = alert;
    }

    @Override
    public void anchor(int y, Lane lane) {
        anchor.set(y, lane);
        setY(y);
    }

    @Override
    public void glide(ScrollLanes lanes) {
        if (!anchor.placed()) return;

        anchor.follow(lanes);
        setY(anchor.shifted());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (anchor.gone(getY(), height)) return;

        anchor.draw(graphics, getX(), width, getY(), height,
                () -> super.render(graphics, mouseX, anchor.pointer(mouseY), partialTick));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float appear = UiAnim.easeOut((System.currentTimeMillis() - shownAt) / APPEAR_MS);
        UiGlass.sunken(graphics, getX(), getY(), width, height, UiMetrics.radius(ROW_RADIUS), appear);
        relines(graphics);
        Font font = Minecraft.getInstance().font;
        float step = font.lineHeight * SCALE + LEADING;
        float y = getY() + (height - Math.min(LINES, lines.size()) * step) / 2.0f;
        int color = UiTheme.alpha(alerting ? UiPalette.alert() : UiAccent.textDim(), appear);
        for (int index = 0; index < Math.min(LINES, lines.size()); index++) {
            UiRender.textCentered(graphics, font, lines.get(index), getX() + width / 2.0f, y, SCALE, color, false);
            y += step;
        }
    }

    private void relines(GuiGraphics graphics) {
        if (text == splitFor && width == splitWidth) return;

        splitFor = text;
        splitWidth = width;
        lines.clear();
        Font font = Minecraft.getInstance().font;
        int wrap = (int) ((width - UiMetrics.PAD_WIDE * 2.0f) / SCALE);
        for (FormattedCharSequence line : UiRender.split(graphics, font, text, SCALE, wrap)) {
            lines.add(Component.literal(UiRender.flatten(line)));
        }
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, text);
    }
}
