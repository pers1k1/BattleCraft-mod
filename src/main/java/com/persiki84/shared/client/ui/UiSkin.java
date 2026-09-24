package com.persiki84.shared.client.ui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class UiSkin {
    private static final float LABEL_SWAP_LIFT = 4.0f;
    public static final float LABEL_SCALE = 1.0f;
    public static final float LABEL_TRACKING = 0.3f;

    private static final float LABEL_PADDING = 3.0f;
    private static final float ICON_RATIO = 2.2f;
    private static final int INITIALS_LIMIT = 3;
    private static final String WORD_BREAK = "[^\\p{L}\\p{N}]+";
    private static final float PRESS_SQUASH = 0.055f;
    private static final float REBOUND_LIMIT = 0.35f;
    private static final float SMOOTH_THRESHOLD = 0.0004f;
    private static final float HOVER_LIFT = 1.2f;
    private static final float TRACK_HEIGHT = 3.0f;
    private static final float TRACK_INSET = 9.0f;
    private static final float TRACK_BOTTOM = 7.0f;
    private static final float KNOB_RADIUS = 3.4f;
    private static final float SWATCH_SIZE = 14.0f;
    private static final float SWATCH_INSET = 8.0f;
    private static final float SWATCH_RADIUS = 4.0f;
    private static final int NO_SWATCH = 0;
    private static final float BOX_SIZE = 13.0f;
    private static final float BOX_GAP = 6.0f;
    private static final float BOX_FOCUS_ALPHA = 0.26f;
    private static final int SWEEP_COOL = 0xFFD8E7FF;
    private static final int SWEEP_WARM = 0xFFFFF0DA;

    private UiSkin() {}

    public static float fit(GuiGraphics graphics) {
        return UiRender.crisp(graphics, LABEL_SCALE);
    }

    public static void button(GuiGraphics graphics, float x, float y, float width, float height,
                              Component label, boolean active, float focus, float pressed) {
        button(graphics, x, y, width, height, label, active, focus, pressed, fit(graphics));
    }

    public static void button(GuiGraphics graphics, float x, float y, float width, float height,
                              Component label, boolean active, float focus, float pressed, float labelScale) {
        button(graphics, x, y, width, height, label, active, focus, pressed, labelScale, NO_SWATCH);
    }

    public static void button(GuiGraphics graphics, float x, float y, float width, float height,
                              Component label, boolean active, float focus, float pressed,
                              float labelScale, int swatch) {
        button(graphics, x, y, width, height, label, active, focus, pressed, labelScale, swatch, false);
    }

    public static void button(GuiGraphics graphics, float x, float y, float width, float height,
                              Component label, boolean active, float focus, float pressed,
                              float labelScale, int swatch, boolean lit) {
        button(graphics, x, y, width, height, label, active ? 1.0f : 0.0f, focus, pressed, labelScale, swatch, lit);
    }

    // WHY: доступность кнопки меняется переходом, а не кадром: при смене таблицы или вкладки
    // WHY: погасшая кнопка оживала рывком, и это читалось как мигание интерфейса
    public static void button(GuiGraphics graphics, float x, float y, float width, float height,
                              Component label, float live, float focus, float pressed,
                              float labelScale, int swatch, boolean lit) {
        button(graphics, x, y, width, height, label, null, 1.0f, live, focus, pressed, labelScale, swatch, lit);
    }

    // WHY: подпись кнопки меняется сменой, а не подменой: прежняя уходит вверх и гаснет, новая
    // WHY: поднимается снизу, иначе «Удалить» -> «Точно?» и смена режима выглядят как мигание
    public static void button(GuiGraphics graphics, float x, float y, float width, float height,
                              Component label, Component outgoing, float swap, float live, float focus,
                              float pressed, float labelScale, int swatch, boolean lit) {
        float squash = Math.max(-REBOUND_LIMIT, Math.min(1.0f, pressed)) * PRESS_SQUASH;
        float w = width * (1.0f - squash);
        float h = height * (1.0f - squash);
        float bx = x + (width - w) / 2.0f;
        float by = y + (height - h) / 2.0f - focus * HOVER_LIFT;

        boolean quantized = UiRender.rawScale(UiRender.rawScale(false) || Math.abs(squash) > SMOOTH_THRESHOLD);
        try {
            surface(graphics, bx, by, w, h, live, focus, pressed);
            float textLive = lit ? 1.0f : live;
            float scale = labelScale * (1.0f - squash);
            if (outgoing != null && swap < 0.999f) {
                label(graphics, outgoing, bx, by - swap * LABEL_SWAP_LIFT, w, h, textLive, focus, scale, 1.0f - swap);
            }
            label(graphics, label, bx, by + (1.0f - swap) * LABEL_SWAP_LIFT, w, h, textLive, focus, scale, swap);
            swatch(graphics, bx, by, w, h, 1.0f - squash, swatch);
        } finally {
            UiRender.rawScale(quantized);
        }
    }

    // WHY: образец цвета живёт внутри кнопки, иначе он не повторяет её подъём под курсором и сжатие нажатия
    private static void swatch(GuiGraphics graphics, float x, float y, float width, float height,
                               float scale, int argb) {
        if ((argb >>> 24) == 0) return;

        float size = SWATCH_SIZE * scale;
        UiRender.panel(graphics, x + width - SWATCH_INSET * scale - size, y + (height - size) / 2.0f,
                size, size, SWATCH_RADIUS * scale, argb);
    }

    public static void icon(GuiGraphics graphics, float x, float y, float width, float height,
                            boolean active, float focus) {
        surface(graphics, x, y, width, height, active ? 1.0f : 0.0f, focus, 0.0f);
    }

    public static void checkbox(GuiGraphics graphics, float x, float y, float width, float height,
                                Component label, boolean selected, boolean active, float focus, float labelScale) {
        float box = Math.min(height, BOX_SIZE);
        float boxY = y + (height - box) / 2.0f;
        float radius = box * 0.28f;

        if (selected) {
            UiGlass.panel(graphics, x, boxY, box, box, radius, 1.0f, 0.55f + 0.45f * focus);
            UiRender.check(graphics, x + box / 2.0f, boxY + box / 2.0f, box * 0.52f,
                    active ? UiAccent.text() : UiAccent.textFaint());
        } else {
            UiGlass.sunken(graphics, x, boxY, box, box, radius, 0.9f);
            if (focus > 0.01f) {
                UiGlass.inner(graphics, x, boxY, box, box, radius, BOX_FOCUS_ALPHA * focus, focus);
            }
        }

        if (label == null || label.getString().isEmpty()) return;
        float textX = x + box + BOX_GAP;
        UiRender.textTrackedLeft(graphics, Minecraft.getInstance().font, label, textX,
                UiRender.centerY(y, height, labelScale), labelScale, LABEL_TRACKING,
                active ? UiTheme.mix(UiAccent.text(), UiTheme.WHITE, focus) : UiAccent.textFaint());
    }

    public static void slider(GuiGraphics graphics, float x, float y, float width, float height,
                              Component label, boolean active, float focus, float progress) {
        slider(graphics, x, y, width, height, label, active, focus, progress, fit(graphics));
    }

    public static void slider(GuiGraphics graphics, float x, float y, float width, float height,
                              Component label, boolean active, float focus, float progress, float labelScale) {
        surface(graphics, x, y, width, height, active ? 1.0f : 0.0f, focus, 0.0f);
        label(graphics, label, x, y, width, Math.max(height * 0.5f, height - TRACK_BOTTOM), active ? 1.0f : 0.0f, focus, labelScale);

        float trackWidth = width - TRACK_INSET * 2.0f;
        if (trackWidth <= 2.0f) return;

        float trackX = x + TRACK_INSET;
        float trackY = y + height - TRACK_BOTTOM;
        UiGlass.sunken(graphics, trackX, trackY, trackWidth, TRACK_HEIGHT, TRACK_HEIGHT / 2.0f, 1.0f);
        UiGlass.progress(graphics, trackX, trackY, trackWidth, TRACK_HEIGHT, progress,
                UiTheme.mix(UiAccent.dim(), UiAccent.color(), focus), 1.0f);

        float radius = KNOB_RADIUS + focus * 0.9f;
        float knobX = trackX + trackWidth * UiAnim.clamp01(progress);
        UiGlass.inner(graphics, knobX - radius, trackY + TRACK_HEIGHT / 2.0f - radius,
                radius * 2.0f, radius * 2.0f, radius, 1.0f, 0.6f + 0.4f * focus);
    }

    private static void surface(GuiGraphics graphics, float x, float y, float width, float height,
                                float live, float focus, float pressed) {
        float radius = Math.min(UiGlassStyle.radiusPanel() + 2.0f, height / 2.0f);
        if (live < 0.999f) UiGlass.sunken(graphics, x, y, width, height, radius, 0.85f * (1.0f - live));
        if (live <= 0.001f) return;

        float shimmer = Math.max(0.0f, pressed);
        UiGlass.panel(graphics, x, y, width, height, radius, live, (focus * 0.85f + shimmer * 0.45f) * live);
        sweep(graphics, x, y, width, height, radius, Math.max(focus, shimmer * 1.4f) * live);
    }

    public static void sweep(GuiGraphics graphics, float x, float y, float width, float height,
                             float radius, float strength) {
        if (strength <= 0.01f) return;

        float angle = sweepAngle();
        float depth = Math.min(UiGlassStyle.sweepDepth(), Math.min(width, height) * 0.3f);
        UiRender.sheenSweep(graphics, x, y, width, height, radius, depth,
                UiTheme.withAlpha(sweepTint(angle), UiGlassStyle.sweepLight() * Math.min(1.0f, strength)),
                angle, UiGlassStyle.sweepPower());
    }

    private static float sweepAngle() {
        long period = Math.max(120L, (long) UiGlassStyle.sweepPeriod());
        double turns = (Util.getMillis() % period) / (double) period;
        return (float) (turns * Math.PI * 2.0 - Math.PI / 2.0);
    }

    private static int sweepTint(float angle) {
        float warmth = (float) (Math.sin(angle) * 0.5 + 0.5);
        return UiTheme.mix(SWEEP_COOL, SWEEP_WARM, warmth);
    }

    private static void label(GuiGraphics graphics, Component label, float x, float y, float width, float height,
                              float live, float focus, float scale) {
        label(graphics, label, x, y, width, height, live, focus, scale, 1.0f);
    }

    private static void label(GuiGraphics graphics, Component label, float x, float y, float width, float height,
                              float live, float focus, float scale, float alpha) {
        if (label == null || label.getString().isEmpty() || alpha <= 0.004f) return;
        int lit = UiTheme.mix(UiAccent.text(), UiTheme.WHITE, focus);
        int tone = live >= 0.999f ? lit : UiTheme.mix(UiAccent.textFaint(), lit, live);
        int color = alpha >= 0.999f ? tone : UiTheme.alpha(tone, alpha);
        float box = width - LABEL_PADDING * 2.0f;
        Component shown = width <= height * ICON_RATIO ? shortened(graphics, label, box, scale) : label;
        UiRender.textTrackedFit(graphics, Minecraft.getInstance().font, shown, x + width / 2.0f, y, height,
                box, scale, LABEL_TRACKING, color, true);
    }

    private static Component shortened(GuiGraphics graphics, Component label, float box, float scale) {
        if (UiRender.measure(graphics, Minecraft.getInstance().font, label, scale) <= box) return label;

        String initials = initials(label.getString());
        return initials.isEmpty() ? label : Component.literal(initials);
    }

    private static String initials(String text) {
        StringBuilder letters = new StringBuilder();
        for (String word : text.split(WORD_BREAK)) {
            if (word.isEmpty() || letters.length() >= INITIALS_LIMIT) continue;
            letters.append(Character.toUpperCase(word.charAt(0)));
        }
        return letters.length() > 1 ? letters.toString() : "";
    }
}
