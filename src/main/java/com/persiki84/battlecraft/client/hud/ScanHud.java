package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Toggle;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public final class ScanHud {
    public static final float WIDTH = 176.0f;
    public static final float HEIGHT = 44.0f;

    private static final float PADDING = 9.0f;
    private static final float TITLE_SCALE = 0.85f;
    private static final float COUNT_SCALE = 0.75f;
    private static final float TALLY_SCALE = 0.62f;
    private static final float TRACK_HEIGHT = 3.0f;
    private static final float TITLE_TOP = 6.0f;
    private static final float TALLY_DROP = 7.0f;
    private static final float SLIDE = 20.0f;
    private static final float FILL_SPEED = 9.0f;
    private static final float DONE_SPEED = 6.0f;
    private static final float GONE = 0.01f;
    private static final int PERCENT = 100;
    private static final int SECONDS_PER_MINUTE = 60;

    private static final int SAMPLE_SCANNED = 1840;
    private static final int SAMPLE_TOTAL = 3598;
    private static final int SAMPLE_LAMPS = 12;
    private static final int SAMPLE_DRIPSTONES = 77;
    private static final int SAMPLE_GLASS = 703;
    private static final int SAMPLE_LEFT = 95;

    private static final Toggle visibility = new Toggle(9.0f, 140L);
    private static final Smooth fill = new Smooth(0.0f, FILL_SPEED);
    private static final Smooth done = new Smooth(0.0f, DONE_SPEED);
    private static final Smooth previewFill = new Smooth(0.0f, FILL_SPEED);
    private static final Smooth previewDone = new Smooth(0.0f, DONE_SPEED);

    private ScanHud() {}

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        if (!HudLayout.visible(HudSlot.SCAN)) return;

        float scale = UiScale.push(graphics);
        try {
            render(graphics, mc, screenWidth / scale, screenHeight / scale);
        } finally {
            UiScale.pop(graphics);
        }
    };

    private static void render(GuiGraphics graphics, Minecraft mc, float screenWidth, float screenHeight) {
        float delta = UiFrame.delta();
        float alpha = visibility.update(ScanBridge.showing(), delta);
        if (alpha <= GONE) {
            if (visibility.cleared()) forget();
            return;
        }

        float appear = UiAnim.easeOut(alpha);
        HudBox box = HudLayout.place(HudSlot.SCAN, WIDTH, HEIGHT, screenWidth, screenHeight);
        float x = box.x() + HudLayout.slideX(HudSlot.SCAN, (1.0f - appear) * SLIDE);

        HudLayout.push(graphics, box);
        try {
            drawCard(graphics, mc, x, box.y(), alpha * box.alpha(), delta, live(), fill, done);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    public static void preview(GuiGraphics graphics, HudBox box, float alpha) {
        HudLayout.sample(HudSlot.SCAN, WIDTH, HEIGHT);
        drawCard(graphics, Minecraft.getInstance(), box.x(), box.y(), alpha, UiFrame.delta(),
                sample(), previewFill, previewDone);
    }

    private static Progress live() {
        return new Progress(ScanBridge.scanned(), ScanBridge.total(), ScanBridge.lamps(),
                ScanBridge.dripstones(), ScanBridge.glass(), ScanBridge.secondsLeft(),
                ScanBridge.complete());
    }

    private static Progress sample() {
        return new Progress(SAMPLE_SCANNED, SAMPLE_TOTAL, SAMPLE_LAMPS, SAMPLE_DRIPSTONES,
                SAMPLE_GLASS, SAMPLE_LEFT, false);
    }

    private static void forget() {
        fill.snap(0.0f);
        done.snap(0.0f);
    }

    private static void drawCard(GuiGraphics graphics, Minecraft mc, float x, float y, float alpha,
                                 float delta, Progress progress, Smooth track, Smooth finish) {
        float settled = finish.to(progress.complete() ? 1.0f : 0.0f, delta);
        int accent = HudLayout.tint(HudSlot.SCAN, UiAccent.color());

        UiVital.card(graphics, x, y, WIDTH, HEIGHT, UiMetrics.radius(HEIGHT), alpha);
        drawTitle(graphics, mc, x, y, alpha, progress, settled, accent);
        drawTally(graphics, mc, x, y, alpha, progress);
        drawTrack(graphics, x, y, alpha, delta, progress, track, accent);
    }

    private static void drawTitle(GuiGraphics graphics, Minecraft mc, float x, float y, float alpha,
                                  Progress progress, float settled, int accent) {
        Component title = progress.complete()
                ? Component.translatable("battlecraft.scan.done")
                : Component.translatable("battlecraft.scan.title");

        UiRender.labelScaled(graphics, mc.font, title, x + PADDING, y + TITLE_TOP, TITLE_SCALE,
                UiTheme.alpha(UiTheme.mix(HudInk.text(), accent, settled), alpha));
        UiRender.textRight(graphics, mc.font, tail(progress), x + WIDTH - PADDING,
                y + TITLE_TOP + 1.0f, COUNT_SCALE, UiTheme.alpha(HudInk.textDim(), alpha), false);
    }

    private static Component tail(Progress progress) {
        if (progress.complete()) {
            return Component.translatable("battlecraft.scan.chunks", progress.scanned());
        }
        if (progress.secondsLeft() < 0) {
            return Component.literal(Math.round(progress.share() * PERCENT) + "%");
        }
        return Component.translatable("battlecraft.scan.left", clock(progress.secondsLeft()));
    }

    private static String clock(int seconds) {
        return seconds / SECONDS_PER_MINUTE + ":" + String.format("%02d", seconds % SECONDS_PER_MINUTE);
    }

    private static void drawTally(GuiGraphics graphics, Minecraft mc, float x, float y, float alpha,
                                  Progress progress) {
        Component tally = Component.translatable("battlecraft.scan.tally",
                progress.lamps(), progress.dripstones(), progress.glass());

        UiRender.labelScaled(graphics, mc.font, tally, x + PADDING,
                y + TITLE_TOP + mc.font.lineHeight * TITLE_SCALE + TALLY_DROP - 3.0f, TALLY_SCALE,
                UiTheme.alpha(HudInk.textFaint(), alpha));
    }

    private static void drawTrack(GuiGraphics graphics, float x, float y, float alpha, float delta,
                                  Progress progress, Smooth track, int accent) {
        float trackY = y + HEIGHT - PADDING;
        float width = WIDTH - PADDING * 2.0f;

        UiGlass.sunken(graphics, x + PADDING, trackY, width, TRACK_HEIGHT, TRACK_HEIGHT / 2.0f, alpha);
        UiGlass.progress(graphics, x + PADDING, trackY, width, TRACK_HEIGHT,
                track.to(progress.share(), delta), accent, alpha);
    }

    private record Progress(int scanned, int total, int lamps, int dripstones, int glass,
                            int secondsLeft, boolean complete) {
        float share() {
            if (complete) return 1.0f;
            return total <= 0 ? 0.0f : Math.min(1.0f, scanned / (float) total);
        }
    }
}
