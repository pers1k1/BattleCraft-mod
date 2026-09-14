package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.battlecraft.client.voice.VoiceTape;
import com.persiki84.battlecraft.client.voice.VoiceTraffic;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Toggle;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public final class VoiceHud {
    private static final float SIZE = 15.0f;
    private static final float ICON = 9.0f;
    private static final float LABEL_SCALE = 0.75f;
    private static final float LABEL_GAP = 6.0f;
    private static final float LABEL_PAD = 7.0f;
    private static final int TRACE_MARKS = 16;
    private static final float TRACE_HEIGHT = 9.0f;
    private static final float TRACE_PAD = 5.0f;
    private static final float TRACE_GAP = 4.0f;
    private static final float SAMPLE_LEVEL = 0.55f;

    private static final Toggle visibility = new Toggle(10.0f, 140L);
    private static final Smooth opening = new Smooth(9.0f);
    private static final Smooth previewOpening = new Smooth(9.0f);
    private static final VoiceTape sample = new VoiceTape();

    private static VoiceBridge.State shown = VoiceBridge.State.NONE;

    private VoiceHud() {}

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) return;
        if (!HudLayout.visible(HudSlot.VOICE)) return;

        float scale = UiScale.push(graphics);
        try {
            render(graphics, screenWidth / scale, screenHeight / scale);
        } finally {
            UiScale.pop(graphics);
        }
    };

    private static void render(GuiGraphics graphics, float screenWidth, float screenHeight) {
        long now = System.currentTimeMillis();
        VoiceTraffic.advance(now, UiFrame.frame());

        VoiceBridge.State state = VoiceBridge.current();
        float delta = UiFrame.delta();
        float alpha = visibility.update(state != VoiceBridge.State.NONE, delta);
        if (state != VoiceBridge.State.NONE) shown = state;
        if (alpha <= 0.01f) {
            if (visibility.cleared()) shown = VoiceBridge.State.NONE;
            opening.to(0.0f, delta);
            return;
        }

        float appear = UiAnim.easeOut(alpha);
        float open = opening.to(tracing(now) ? 1.0f : 0.0f, delta);
        float width = SIZE + span(open);
        HudBox box = HudLayout.place(HudSlot.VOICE, width, SIZE, screenWidth, screenHeight);
        float x = box.x() + HudLayout.slideX(HudSlot.VOICE, (1.0f - appear) * 12.0f);
        float y = box.y();

        HudLayout.push(graphics, box);
        try {
            float badgeX = x + width - SIZE;
            drawTape(graphics, VoiceTraffic.own(), now, x, y, open, alpha * box.alpha());
            drawBadge(graphics, badgeX, y, alpha * box.alpha());
            drawWarning(graphics, x, y, alpha * box.alpha());
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    // WHY: лента идёт от самого голосового чата, поэтому она поднимается ровно на переданный
    // WHY: голос: ни шум системы, ни музыка, ни чужие приложения сюда не попадают
    private static boolean tracing(long now) {
        return HudConfig.voiceTrace() && shown == VoiceBridge.State.TALKING
                && VoiceTraffic.own().talking(now);
    }

    private static float span(float open) {
        return open <= 0.001f ? 0.0f : (VoiceTrace.width(TRACE_MARKS) + TRACE_PAD * 2.0f + TRACE_GAP) * open;
    }

    public static void preview(GuiGraphics graphics, HudBox box, float alpha) {
        VoiceBridge.State held = shown;
        if (shown == VoiceBridge.State.NONE) shown = VoiceBridge.State.TALKING;
        try {
            long now = System.currentTimeMillis();
            float open = previewOpening.to(HudConfig.voiceTrace() ? 1.0f : 0.0f, UiFrame.delta());
            sample.feed(SAMPLE_LEVEL * (0.4f + 0.6f * (float) Math.abs(Math.sin((now % 100000L) / 230.0))));
            sample.advance(now);

            float width = SIZE + span(open);
            HudLayout.sample(HudSlot.VOICE, width, SIZE);
            drawTape(graphics, sample, now, box.x(), box.y(), open, alpha);
            drawBadge(graphics, box.x() + width - SIZE, box.y(), alpha);
        } finally {
            shown = held;
        }
    }

    private static void drawTape(GuiGraphics graphics, VoiceTape tape, long now, float x, float y,
                                 float open, float alpha) {
        if (open <= 0.01f) return;

        float width = VoiceTrace.width(TRACE_MARKS) + TRACE_PAD * 2.0f;
        float drawn = width * open;
        float left = x + (width - drawn);
        UiVital.card(graphics, left, y, drawn, SIZE, UiGlassStyle.radiusCell(), alpha * open);
        VoiceTrace.paint(graphics, left + TRACE_PAD * open, y + SIZE / 2.0f, TRACE_HEIGHT * open, tape, now,
                HudLayout.tint(HudSlot.VOICE, UiAccent.color()), alpha * open, TRACE_MARKS);
    }

    private static void drawBadge(GuiGraphics graphics, float x, float y, float alpha) {
        float pulse = shown == VoiceBridge.State.TALKING ? UiAnim.pulse(700.0f, 0.7f, 1.0f) : 1.0f;
        int tint = switch (shown) {
            case TALKING -> HudLayout.tint(HudSlot.VOICE, UiAccent.color());
            case MUTED -> HudInk.textFaint();
            default -> UiTheme.mix(UiAccent.dim(), UiPalette.alert(), 0.75f);
        };

        UiVital.card(graphics, x, y, SIZE, SIZE, UiGlassStyle.radiusCell(), alpha);
        UiRender.iconMic(graphics, x + SIZE / 2.0f, y + SIZE / 2.0f, ICON, UiTheme.alpha(tint, alpha * pulse));

        if (crossed()) {
            UiRender.line(graphics, x + SIZE * 0.24f, y + SIZE * 0.24f,
                    x + SIZE * 0.76f, y + SIZE * 0.76f, 1.4f, UiTheme.alpha(tint, alpha));
        }
    }

    private static void drawWarning(GuiGraphics graphics, float x, float y, float alpha) {
        if (shown != VoiceBridge.State.NO_MICROPHONE) return;

        Minecraft mc = Minecraft.getInstance();
        Component label = Component.translatable("battlecraft.voice.no_microphone");
        float textWidth = UiRender.width(mc.font, label) * LABEL_SCALE;
        float cardWidth = textWidth + LABEL_PAD * 2.0f;
        float cardX = x - LABEL_GAP - cardWidth;

        UiVital.card(graphics, cardX, y, cardWidth, SIZE, UiGlassStyle.radiusCell(), alpha);
        UiRender.textCentered(graphics, mc.font, label, cardX + cardWidth / 2.0f,
                UiRender.centerY(y, SIZE, LABEL_SCALE), LABEL_SCALE,
                UiTheme.alpha(UiTheme.mix(HudInk.text(), UiPalette.alert(), 0.55f), alpha), false);
    }

    private static boolean crossed() {
        return shown == VoiceBridge.State.DISCONNECTED
                || shown == VoiceBridge.State.DISABLED
                || shown == VoiceBridge.State.NO_MICROPHONE
                || shown == VoiceBridge.State.MUTED;
    }
}
