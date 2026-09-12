package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public final class MenuHint {
    public static final String SUFFIX = ".hint";

    private static final long DWELL_MS = 380L;
    private static final long SWAP_MS = 120L;
    private static final float APPEAR_SPEED = 17.0f;
    private static final float FADE_SPEED = 27.0f;
    private static final float TRACK_SPEED = 15.0f;
    private static final float TEXT_SCALE = 0.75f;
    private static final float MAX_WIDTH = 210.0f;
    private static final float MIN_WIDTH = 54.0f;
    private static final float PAD_X = 9.0f;
    private static final float PAD_Y = 7.0f;
    private static final float GAP = 6.0f;
    private static final float SCREEN_MARGIN = 5.0f;
    private static final float RADIUS = 6.0f;
    private static final float BORN_SCALE = 0.86f;
    private static final float RISE = 7.0f;
    private static final float LINE_GAP = 1.5f;
    private static final float VISIBLE = 0.004f;

    private static final List<FormattedCharSequence> lines = new ArrayList<>();
    private static final List<FormattedCharSequence> emberLines = new ArrayList<>();
    private static final Smooth grow = new Smooth(0.0f, APPEAR_SPEED);
    private static final Smooth track = new Smooth(0.0f, TRACK_SPEED);

    private static Object owner;
    private static Object pending;
    private static Component text;
    private static Component pendingText;
    private static Component measuredText;
    private static float measuredScale;
    private static float boxWidth;
    private static float boxHeight;
    private static float ownerY;
    private static float ownerHeight;
    private static float pointerX;
    private static float pendingY;
    private static float pendingHeight;
    private static float pendingX;
    private static long dwellSince;
    private static long offeredFrame = -1L;
    private static boolean frozen;
    private static boolean rising;
    private static boolean placed;

    private static float emberX;
    private static float emberY;
    private static float emberWidth;
    private static float emberHeight;
    private static float emberPivot;
    private static float emberShown;
    private static boolean emberBelow;

    private MenuHint() {}

    public static Component of(String key) {
        if (key == null || key.isEmpty() || !Language.getInstance().has(key)) return null;
        return Component.translatable(key);
    }

    public static void offer(Object source, Component explanation, float y, float height, float cursorX) {
        if (frozen || source == null || explanation == null) return;

        if (source != pending) {
            pending = source;
            dwellSince = source == owner
                    ? 0L
                    : System.currentTimeMillis() + (grow.get() > VISIBLE ? SWAP_MS : 0L);
        }
        pendingText = explanation;
        pendingY = y;
        pendingHeight = height;
        pendingX = cursorX;
        offeredFrame = UiFrame.frame();
    }

    // WHY: курсор на закрытии замирает, и подсказка обязана замереть вместе с ним; живое состояние
    // WHY: в это время уже принадлежит следующему экрану, поэтому горение рисует свой снимок
    public static void freeze(boolean value) {
        frozen = value;
    }

    public static void render(GuiGraphics graphics) {
        if (frozen) {
            paintEmber(graphics, Minecraft.getInstance().font);
            return;
        }

        adopt();
        rising = alive();
        float shown = grow.to(rising ? 1.0f : 0.0f, rising ? APPEAR_SPEED : FADE_SPEED, UiFrame.delta());
        if (shown <= VISIBLE || text == null) {
            emberShown = 0.0f;
            if (!rising) release();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        layout(graphics, font);
        if (lines.isEmpty()) return;

        boolean below = below(minecraft);
        float boxX = anchorX(minecraft);
        float boxY = below ? ownerY + ownerHeight + GAP : ownerY - GAP - boxHeight;
        keep(boxX, boxY, shown, below);
        paint(graphics, font, boxX, boxY, boxWidth, boxHeight, shown, below,
                Math.max(boxX, Math.min(boxX + boxWidth, pointerX)), lines);
    }

    private static void keep(float x, float y, float shown, boolean below) {
        emberX = x;
        emberY = y;
        emberWidth = boxWidth;
        emberHeight = boxHeight;
        emberPivot = Math.max(x, Math.min(x + boxWidth, pointerX));
        emberShown = shown;
        emberBelow = below;
        emberLines.clear();
        emberLines.addAll(lines);
    }

    private static void paintEmber(GuiGraphics graphics, Font font) {
        if (emberShown <= VISIBLE || emberLines.isEmpty()) return;

        boolean previous = rising;
        rising = true;
        try {
            paint(graphics, font, emberX, emberY, emberWidth, emberHeight, emberShown, emberBelow,
                    emberPivot, emberLines);
        } finally {
            rising = previous;
        }
    }

    // WHY: заявка ждёт, пока прежняя подсказка догорит у своей строки: перехвати она окно сразу,
    // WHY: оно уехало бы к новой настройке с чужим текстом внутри и гасло уже там
    private static void adopt() {
        if (pending == null || (owner != null && owner != pending)) return;
        if (owner != null && rewritten()) {
            surrender();
            return;
        }
        if (owner == null) {
            if (offeredFrame != UiFrame.frame() || !dwelt() || grow.get() > VISIBLE) return;
            owner = pending;
            placed = false;
            text = pendingText;
        }
        ownerY = pendingY;
        ownerHeight = pendingHeight;
        pointerX = pendingX;
    }

    private static void release() {
        owner = null;
        text = null;
    }

    // WHY: сменившееся значение строки объясняется уже другими словами, и прежние обязаны уйти
    // WHY: гашением: подменённый на месте текст читается как дефект, а не как смена настройки
    private static boolean rewritten() {
        String was = text == null ? null : text.getString();
        String now = pendingText == null ? null : pendingText.getString();
        return was != null && !was.equals(now);
    }

    private static void surrender() {
        owner = null;
        dwellSince = System.currentTimeMillis() + SWAP_MS;
    }

    private static boolean alive() {
        if (owner == null || owner != pending || offeredFrame != UiFrame.frame()) return false;
        return dwelt();
    }

    private static boolean dwelt() {
        return System.currentTimeMillis() - dwellSince >= DWELL_MS;
    }

    // WHY: разбор на строки и промер идут через атласы шрифта, поэтому держатся до смены текста
    // WHY: или масштаба экрана, а не пересчитываются каждый кадр
    private static void layout(GuiGraphics graphics, Font font) {
        float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        if (text == measuredText && Math.abs(scale - measuredScale) < 1.0E-4f) return;

        measuredText = text;
        measuredScale = scale;
        lines.clear();
        lines.addAll(UiRender.split(graphics, font, text, TEXT_SCALE,
                (int) ((MAX_WIDTH - PAD_X * 2.0f) / TEXT_SCALE)));

        float widest = 0.0f;
        for (FormattedCharSequence line : lines) {
            widest = Math.max(widest, UiRender.measureLine(graphics, font, line, TEXT_SCALE));
        }
        boxWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, widest + PAD_X * 2.0f));
        boxHeight = lines.size() * lineStep(font) - LINE_GAP + PAD_Y * 2.0f;
    }

    private static float lineStep(Font font) {
        return font.lineHeight * TEXT_SCALE + LINE_GAP;
    }

    // WHY: подсказка встаёт целиком под строкой или целиком над ней, а не у курсора по вертикали:
    // WHY: иначе она накрывает ту самую настройку, которую объясняет
    private static boolean below(Minecraft minecraft) {
        float room = minecraft.getWindow().getGuiScaledHeight() - SCREEN_MARGIN;
        if (ownerY + ownerHeight + GAP + boxHeight <= room) return true;
        return ownerY - GAP - boxHeight < SCREEN_MARGIN;
    }

    private static float anchorX(Minecraft minecraft) {
        float limit = minecraft.getWindow().getGuiScaledWidth() - boxWidth - SCREEN_MARGIN;
        float wanted = Math.max(SCREEN_MARGIN, Math.min(limit, pointerX - boxWidth / 2.0f));
        if (!placed) {
            placed = true;
            track.snap(wanted);
            return wanted;
        }
        return track.to(wanted, UiFrame.delta());
    }

    private static void paint(GuiGraphics graphics, Font font, float x, float y, float width, float height,
                              float shown, boolean below, float pivotX, List<FormattedCharSequence> drawn) {
        float eased = UiAnim.easeOut(shown);
        float shape = rising ? UiAnim.easeOutBack(shown) : eased;
        float scale = BORN_SCALE + (1.0f - BORN_SCALE) * shape;
        float lift = (1.0f - eased) * RISE * (below ? -1.0f : 1.0f);
        float pivotY = below ? y : y + height;

        UiGlass.layer(graphics);
        graphics.pose().pushPose();
        graphics.pose().translate(pivotX, pivotY + lift, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-pivotX, -pivotY, 0.0f);
        try {
            UiGlass.window(graphics, x, y, width, height, RADIUS, eased);
            paintLines(graphics, font, x, y, width, eased, drawn);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void paintLines(GuiGraphics graphics, Font font, float x, float y, float width,
                                   float eased, List<FormattedCharSequence> drawn) {
        float step = lineStep(font);
        float textY = y + PAD_Y;
        int color = UiTheme.alpha(UiAccent.text(), eased);

        for (FormattedCharSequence line : drawn) {
            float span = UiRender.measureLine(graphics, font, line, TEXT_SCALE);
            UiRender.textLine(graphics, font, line, x + (width - span) / 2.0f, textY, TEXT_SCALE, color, false);
            textY += step;
        }
    }
}
