package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiAccent;
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

public final class HeadingRow extends AbstractWidget implements GlidingRow {
    private final RowAnchor anchor = new RowAnchor();

    private static final float LABEL_SCALE = 0.8f;
    private static final float TRACKING = 0.9f;
    private static final float MARK_WIDTH = 2.2f;
    private static final float MARK_HEIGHT = 9.0f;
    private static final float RULE_HEIGHT = 1.0f;
    private static final float RULE_ALPHA = 0.35f;

    public HeadingRow(int x, int y, int width, int height, Component label) {
        super(x, y, width, height, label);
        this.active = false;
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
        float textX = getX() + MARK_WIDTH + UiMetrics.GAP_WIDE;
        float centerY = getY() + height / 2.0f;

        UiRender.panel(graphics, getX(), centerY - MARK_HEIGHT / 2.0f, MARK_WIDTH, MARK_HEIGHT,
                MARK_WIDTH / 2.0f, UiAccent.color());

        float textWidth = UiRender.widthLabel(font, getMessage()) * LABEL_SCALE
                + TRACKING * Math.max(0, getMessage().getString().length() - 1);
        UiRender.textTrackedLeft(graphics, font, getMessage(), textX,
                UiRender.centerY(getY(), height, LABEL_SCALE), LABEL_SCALE, TRACKING, UiAccent.textDim());

        float ruleX = textX + textWidth + UiMetrics.GAP_WIDE;
        float ruleWidth = getX() + width - ruleX;
        if (ruleWidth <= UiMetrics.GAP_WIDE) return;

        UiRender.panel(graphics, ruleX, centerY - RULE_HEIGHT / 2.0f, ruleWidth, RULE_HEIGHT,
                RULE_HEIGHT / 2.0f, UiTheme.alpha(UiPalette.stroke(), RULE_ALPHA));
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
