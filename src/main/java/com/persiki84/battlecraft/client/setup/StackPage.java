package com.persiki84.battlecraft.client.setup;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class StackPage implements SetupPage {
    public record Option(Component label, Component note, int swatch, Runnable pick) {
        public Option(Component label, Component note, Runnable pick) {
            this(label, note, 0, pick);
        }
    }

    private static final int BUTTON_HEIGHT = 24;
    private static final int NOTE_HEIGHT = 9;
    private static final int NOTE_GAP = 3;
    private static final int ROW_GAP = 12;
    private static final float NOTE_SCALE = 0.68f;

    private final SetupStep step;
    private final List<Option> options;

    public StackPage(SetupStep step, List<Option> options) {
        this.step = step;
        this.options = options;
    }

    @Override
    public Component title() {
        return Component.translatable(step.titleKey());
    }

    @Override
    public Component note() {
        return Component.translatable(step.noteKey());
    }

    @Override
    public int contentHeight() {
        return options.size() * rowHeight();
    }

    private static int rowHeight() {
        return BUTTON_HEIGHT + NOTE_HEIGHT + NOTE_GAP + ROW_GAP;
    }

    @Override
    public void build(SetupScreen screen, int left, int top, int width) {
        int y = top;
        for (Option option : options) {
            screen.place(new UiButton(left, y, width, BUTTON_HEIGHT, option.label(),
                    button -> option.pick().run()).swatch(option.swatch()));
            y += rowHeight();
        }
    }

    @Override
    public void paint(GuiGraphics graphics, Font font, int left, int top, int width, float leaving) {
        float centerX = left + width / 2.0f;
        int y = top;
        for (Option option : options) {
            UiRender.textCentered(graphics, font, option.note(), centerX,
                    y + BUTTON_HEIGHT + NOTE_GAP, NOTE_SCALE, UiAccent.textDim(), false);
            y += rowHeight();
        }
    }
}
