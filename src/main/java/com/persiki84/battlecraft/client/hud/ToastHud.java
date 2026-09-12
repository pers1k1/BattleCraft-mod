package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Spring;
import com.persiki84.shared.client.ui.Toggle;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class ToastHud {
    private static final float MARGIN = 6.0f;
    private static final String SAMPLE_KEY = "battlecraft.custom.sample.toasts";
    private static final long SAMPLE_LIFE = 6000L;

    private static Note sample;

    private static final float WIDTH = 162.0f;
    private static final float WIDTH_MIN = 58.0f;
    private static final float PADDING = UiMetrics.PAD;
    private static final float ROW_GAP = 5.0f;
    private static final float BAR_HEIGHT = 3.0f;
    private static final float BAR_GAP = 5.0f;
    private static final float BAR_BOTTOM = 5.0f;
    private static final float ACCENT_WIDTH = 2.4f;
    private static final float ACCENT_INSET = 4.0f;
    private static final float SLIDE_TRAVEL = 26.0f;
    private static final long BASE_MS = 3200L;
    private static final long PER_CHAR_MS = 45L;
    private static final long MAX_MS = 10000L;
    private static final long MIN_MS = 800L;
    private static final int MAX_NOTES = 5;

    private static final List<Note> notes = new ArrayList<>();

    private ToastHud() {}

    // WHY: объявление оператора живёт ровно столько, сколько он назначил, поэтому срок задаётся
    // WHY: прямо, а не добавкой к сроку, посчитанному по длине строки
    public static void pushFor(Component message, long lifeMs) {
        long expiresAt = System.currentTimeMillis() + Math.max(MIN_MS, lifeMs);
        add(new Note(null, () -> message, expiresAt, expiresAt, false));
    }

    public static void push(Component message, long extraMs) {
        long expiresAt = System.currentTimeMillis() + lifespan(message) + extraMs;
        add(new Note(null, () -> message, expiresAt, expiresAt, false));
    }

    public static void pushCountdown(String key, Supplier<Component> message, long endsAt) {
        Component text = message.get();
        long expiresAt = Math.min(endsAt, System.currentTimeMillis() + lifespan(text));
        add(new Note(key, message, expiresAt, endsAt, false));
    }

    private static long lifespan(Component message) {
        return Math.min(MAX_MS, BASE_MS + message.getString().length() * PER_CHAR_MS);
    }

    public static void pushLive(String key, Supplier<Component> message, long endsAt, long holdMs, boolean alert) {
        add(new Note(key, message, System.currentTimeMillis() + holdMs, endsAt, alert), false);
    }

    // WHY: серия продлевает своё окно с каждым событием, поэтому полоса заводится заново,
    // WHY: а не тянется от первого события серии, как у обычного обратного отсчёта
    public static void pushRenewing(String key, Supplier<Component> message, long holdMs) {
        long endsAt = System.currentTimeMillis() + holdMs;
        add(new Note(key, message, endsAt, endsAt, false), true);
    }

    private static void add(Note note) {
        add(note, false);
    }

    private static void add(Note note, boolean renewed) {
        if (note.key != null) {
            for (Note existing : notes) {
                if (Objects.equals(existing.key, note.key) && !existing.alpha.hidden()) {
                    existing.text = note.text;
                    existing.expiresAt = note.expiresAt;
                    existing.barTo = note.barTo;
                    existing.bornAt = renewed ? note.bornAt : Math.min(existing.bornAt, note.bornAt);
                    return;
                }
            }
        }
        notes.add(note);
        if (note.alert) {
            UiSound.alert();
        } else {
            UiSound.toast();
        }
        while (notes.size() > MAX_NOTES) {
            notes.remove(0).expiresAt = 0L;
        }
    }

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        if (notes.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            notes.clear();
            return;
        }

        if (!HudLayout.visible(HudSlot.TOASTS)) return;

        float scale = UiScale.push(graphics);
        try {
            render(graphics, mc, screenWidth / scale, screenHeight / scale);
        } finally {
            UiScale.pop(graphics);
        }
    };

    private static void render(GuiGraphics graphics, Minecraft mc, float screenWidth, float screenHeight) {
        float delta = UiFrame.delta();
        long now = System.currentTimeMillis();
        float widest = measureStack(graphics, mc, now, delta);

        HudBox field = HudLayout.placeBelow(HudSlot.TOASTS, widest, stackHeight(), screenWidth, screenHeight,
                BottomHud.statusBottom() + 6.0f);
        stackDown(field);

        HudLayout.push(graphics, field);
        try {
            drawStack(graphics, mc, now, delta);
        } finally {
            HudLayout.pop(graphics, field);
        }
    }

    private static float measureStack(GuiGraphics graphics, Minecraft mc, long now, float delta) {
        float widest = WIDTH_MIN;
        for (Note note : notes) {
            if (now < note.expiresAt || note.frozen == null) {
                note.frozen = note.text.get();
            }
            note.lines = UiRender.split(graphics, mc.font, note.frozen, 1.0f, (int) textRoom());
            note.height = PADDING * 2.0f + Math.max(1, note.lines.size()) * mc.font.lineHeight
                    + BAR_GAP + BAR_HEIGHT;
            note.width = fitWidth(graphics, mc, note);
            note.alphaValue = note.alpha.update(now < note.expiresAt, delta);
            if (note.alphaValue > 0.01f) widest = Math.max(widest, note.width);
        }
        return widest;
    }

    // WHY: коробка слота меряется самой широкой карточкой, а не общим потолком: с потолком
    // WHY: прижатая вправо стопка висела бы в стороне от кромки, а рамка редактора шире карточек
    private static void stackDown(HudBox field) {
        float y = field.y();
        for (Note note : notes) {
            if (note.alphaValue <= 0.01f) continue;

            note.targetY = y;
            note.targetX = field.x();
            y += (note.height + ROW_GAP) * UiAnim.easeOut(note.alphaValue);
        }
    }

    private static void drawStack(GuiGraphics graphics, Minecraft mc, long now, float delta) {
        Iterator<Note> iterator = notes.iterator();
        while (iterator.hasNext()) {
            Note note = iterator.next();
            if (note.alpha.hidden() && now >= note.expiresAt) {
                iterator.remove();
                continue;
            }
            if (note.alphaValue <= 0.01f) continue;
            draw(graphics, mc, note, now, delta);
        }
    }

    public static void preview(GuiGraphics graphics, HudBox box, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (sample == null) {
            sample = new Note("preview", () -> Component.translatable(SAMPLE_KEY), 0L, 0L, false);
        }

        sample.frozen = Component.translatable(SAMPLE_KEY);
        sample.expiresAt = now + SAMPLE_LIFE;
        sample.barTo = sample.expiresAt;
        sample.bornAt = now;
        sample.lines = UiRender.split(graphics, mc.font, sample.frozen, 1.0f, (int) textRoom());
        sample.height = PADDING * 2.0f + Math.max(1, sample.lines.size()) * mc.font.lineHeight
                + BAR_GAP + BAR_HEIGHT;
        sample.width = fitWidth(graphics, mc, sample);
        sample.alphaValue = alpha;
        HudLayout.sample(HudSlot.TOASTS, sample.width, sample.height);
        sample.targetX = box.x();
        sample.targetY = box.y();
        sample.y.snap(box.y());
        draw(graphics, mc, sample, now, UiFrame.delta());
    }

    private static void draw(GuiGraphics graphics, Minecraft mc, Note note, long now, float delta) {
        float eased = UiAnim.easeOut(note.alphaValue);
        float x = note.targetX + HudLayout.slideX(HudSlot.TOASTS, (1.0f - eased) * SLIDE_TRAVEL);
        float noteY = note.y.to(note.targetY, delta);
        float alpha = note.alphaValue;
        float urgency = note.alert ? UiAnim.pulse(1150.0f, 0.55f, 1.0f) : 0.0f;
        int accent = note.alert ? UiTheme.mix(UiPalette.alertDim(), UiPalette.alert(), urgency)
                : HudLayout.tint(HudSlot.TOASTS, UiAccent.color());

        if (note.alert) {
            UiVital.cardTinted(graphics, x, noteY, note.width, note.height, UiMetrics.radius(note.height), alpha,
                    0.18f * urgency, UiTheme.withAlpha(UiPalette.alert(), 0.20f + 0.10f * urgency));
        } else {
            UiVital.card(graphics, x, noteY, note.width, note.height, alpha);
        }

        UiRender.panel(graphics, x + ACCENT_INSET, noteY + ACCENT_INSET, ACCENT_WIDTH,
                note.height - ACCENT_INSET * 2.0f, ACCENT_WIDTH / 2.0f,
                UiTheme.alpha(accent, alpha * (note.alert ? 0.95f : 0.72f)));

        float textX = x + textInset();
        float textY = noteY + PADDING;
        for (FormattedCharSequence line : note.lines) {
            UiRender.textLine(graphics, mc.font, line, textX, textY, 1.0f, UiTheme.alpha(HudInk.text(), alpha), false);
            textY += mc.font.lineHeight;
        }

        countdown(graphics, note, now, delta, x, noteY, textX, alpha, accent);
    }

    private static float stackHeight() {
        float total = 0.0f;
        for (Note note : notes) {
            total += note.height + ROW_GAP;
        }
        return Math.max(1.0f, total - ROW_GAP);
    }

    private static float textInset() {
        return ACCENT_INSET + ACCENT_WIDTH + PADDING;
    }

    private static float textRoom() {
        return WIDTH - textInset() - PADDING;
    }

    private static float fitWidth(GuiGraphics graphics, Minecraft mc, Note note) {
        float widest = 0.0f;
        for (FormattedCharSequence line : note.lines) {
            widest = Math.max(widest, UiRender.measureLine(graphics, mc.font, line, 1.0f));
        }
        return Math.max(WIDTH_MIN, Math.min(WIDTH, widest + textInset() + PADDING));
    }

    private static void countdown(GuiGraphics graphics, Note note, long now, float delta,
                                  float x, float noteY, float barX, float alpha, int accent) {
        float span = Math.max(1L, note.barTo - note.bornAt);
        float remaining = now < note.expiresAt
                ? note.remaining.to(UiAnim.clamp01((note.barTo - now) / span), delta)
                : note.remaining.get();
        float barWidth = x + note.width - PADDING - barX;
        float barY = noteY + note.height - BAR_BOTTOM - BAR_HEIGHT;

        UiGlass.sunken(graphics, barX, barY, barWidth, BAR_HEIGHT, BAR_HEIGHT / 2.0f, alpha);
        UiGlass.progress(graphics, barX, barY, barWidth, BAR_HEIGHT, remaining,
                note.alert ? accent : UiAccent.dim(), alpha);
    }

    private static final class Note {
        private final String key;
        private final boolean alert;
        private final Toggle alpha = new Toggle(9.0f, 0L);
        private final Spring y = new Spring(0.36f, 0.9f);
        private final Smooth remaining = new Smooth(1.0f, 16.0f);
        private Supplier<Component> text;
        private Component frozen;
        private long bornAt = System.currentTimeMillis();
        private long expiresAt;
        private long barTo;
        private List<FormattedCharSequence> lines = List.of();
        private float height;
        private float width = WIDTH;
        private float targetY;
        private float targetX;
        private float alphaValue;

        private Note(String key, Supplier<Component> text, long expiresAt, long barTo, boolean alert) {
            this.key = key;
            this.text = text;
            this.expiresAt = expiresAt;
            this.barTo = barTo;
            this.alert = alert;
        }
    }
}
