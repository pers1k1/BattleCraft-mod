package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiRender;
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

// WHY: абзац текста живёт в стопке инспектора, а не над ней: стопка прокручивается, и длинное описание
// WHY: на маленьком экране не съедает место под строками. Перенос считается один раз на ширину
public final class ParagraphRow extends AbstractWidget implements GlidingRow {
    private static final float SCALE = 0.8f;
    private static final float LINE_GAP = 2.0f;
    private static final float PAD = 4.0f;

    private final RowAnchor anchor = new RowAnchor();
    private final List<Component> lines = new ArrayList<>();
    private int wrappedFor = -1;

    public ParagraphRow(Component text) {
        super(0, 0, 10, 10, text);
        this.active = false;
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        wrap(width);
    }

    private void wrap(int width) {
        if (width == wrappedFor) return;
        wrappedFor = width;
        lines.clear();
        Font font = Minecraft.getInstance().font;
        for (FormattedCharSequence line : font.split(getMessage(), (int) (width / SCALE))) {
            lines.add(Component.literal(UiRender.flatten(line)));
        }
        setHeight((int) Math.ceil(lines.size() * (font.lineHeight * SCALE + LINE_GAP) + PAD));
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
        Font font = Minecraft.getInstance().font;
        float y = getY();
        for (int index = 0; index < lines.size(); index++) {
            UiRender.textScaled(graphics, font, lines.get(index), getX(), y, SCALE, UiAccent.textDim(), false);
            y += font.lineHeight * SCALE + LINE_GAP;
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
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
