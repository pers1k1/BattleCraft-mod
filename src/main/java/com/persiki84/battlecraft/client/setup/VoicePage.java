package com.persiki84.battlecraft.client.setup;

import com.persiki84.battlecraft.client.voice.VoiceRows;
import com.persiki84.shared.client.menu.KeyRow;
import com.persiki84.shared.client.ui.UiButton;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class VoicePage implements SetupPage {
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_GAP = 6;
    private static final int DONE_HEIGHT = 22;
    private static final int DONE_GAP = 14;

    private final SetupStep step;
    private final Runnable done;
    private final List<AbstractWidget> rows = new ArrayList<>();
    private KeyRow talkKey;

    public VoicePage(SetupStep step, Runnable done) {
        this.step = step;
        this.done = done;
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
        return VoiceRows.COUNT * (ROW_HEIGHT + ROW_GAP) + DONE_GAP + DONE_HEIGHT;
    }

    @Override
    public void build(SetupScreen screen, int left, int top, int width) {
        rows.clear();
        int y = top;
        for (AbstractWidget row : VoiceRows.build(left, y, width, ROW_HEIGHT, ROW_GAP)) {
            rows.add(screen.place(row));
            if (row instanceof KeyRow key) talkKey = key;
            y += ROW_HEIGHT + ROW_GAP;
        }
        screen.place(new UiButton(left, y + DONE_GAP - ROW_GAP, width, DONE_HEIGHT,
                Component.translatable("battlecraft.setup.voice.done"), button -> done.run()));
    }

    @Override
    public void paint(GuiGraphics graphics, Font font, int left, int top, int width, float leaving) {
        VoiceRows.refresh(rows);
    }

    @Override
    public boolean keyPressed(int key, int scan) {
        return talkKey != null && talkKey.take(key, scan);
    }

    @Override
    public boolean mouseClicked(int button) {
        return talkKey != null && talkKey.takeButton(button);
    }
}
