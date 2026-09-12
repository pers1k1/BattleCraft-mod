package com.persiki84.battlecraft.client.hud;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;

public final class ChatSkin {
    private static final float INPUT_RISE = 18.0f;
    private static final float INPUT_SPEED = 15.0f;
    private static final float ARCHIVE_SLIDE = 34.0f;
    private static final float ARCHIVE_SPEED = 26.0f;
    private static final float ARRIVAL_SPEED = 24.0f;
    private static final int ARRIVAL_LINE_LIMIT = 3;

    private static final Smooth inputRise = new Smooth(1.0f, INPUT_SPEED);
    private static final Smooth archiveSlide = new Smooth(1.0f, ARCHIVE_SPEED);
    private static final Smooth arrivalDrop = new Smooth(0.0f, ARRIVAL_SPEED);

    private static boolean motion = true;
    private static boolean typing;
    private static boolean archiveShown;
    private static boolean waking;

    private ChatSkin() {}

    public static void advance(float delta) {
        boolean open = Minecraft.getInstance().screen instanceof ChatScreen;
        boolean opened = open && !typing;
        typing = open;
        waking = false;
        motion = HudConfig.chatMotion();

        if (!motion) {
            rest();
            return;
        }
        if (opened) {
            inputRise.snap(0.0f);
            archiveSlide.snap(0.0f);
        }

        inputRise.to(1.0f, delta);
        archiveSlide.to(1.0f, delta);
        arrivalDrop.to(0.0f, delta);
    }

    private static void rest() {
        inputRise.snap(1.0f);
        archiveSlide.snap(1.0f);
        arrivalDrop.snap(0.0f);
    }

    public static void noteArchive(boolean visible) {
        boolean woke = visible && !archiveShown;
        archiveShown = visible;
        if (!motion || !woke) return;

        archiveSlide.snap(0.0f);
        arrivalDrop.snap(0.0f);
        waking = true;
    }

    public static void noteLines(int lines, int lineHeight) {
        if (!motion || waking || lines <= 0) return;

        float limit = ARRIVAL_LINE_LIMIT * (float) lineHeight;
        arrivalDrop.snap(Math.min(arrivalDrop.get() + lines * (float) lineHeight, limit));
    }

    public static float inputLift(GuiGraphics graphics) {
        return snapped(INPUT_RISE * (1.0f - UiAnim.easeOut(inputRise.get())), graphics);
    }

    public static float archiveShift(GuiGraphics graphics) {
        return snapped(-ARCHIVE_SLIDE * (1.0f - UiAnim.easeOut(archiveSlide.get())), graphics);
    }

    public static float arrivalDrop(GuiGraphics graphics) {
        return snapped(arrivalDrop.get(), graphics);
    }

    private static float snapped(float value, GuiGraphics graphics) {
        float pixels = UiRender.pixels(graphics);
        if (pixels <= 0.0f) return value;
        return Math.round(value * pixels) / pixels;
    }
}
