package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiAssemble;
import com.persiki84.shared.client.ui.UiBackdrop;
import com.persiki84.shared.client.ui.UiColor;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMotion;
import com.persiki84.shared.client.ui.UiMotionSet;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiReveal;
import com.persiki84.shared.client.ui.UiShards;
import com.persiki84.shared.client.ui.UiShatter;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiStage;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.IntConsumer;

public final class PaletteWindow {
    public static final float WIDTH = 214.0f;
    public static final float HEIGHT = 249.0f;

    private static final float PAD = 12.0f;
    private static final float TITLE_ROW = 18.0f;
    private static final float FIELD_WIDTH = 132.0f;
    private static final float FIELD_HEIGHT = 96.0f;
    private static final float HUE_WIDTH = 14.0f;
    private static final float GAP = 8.0f;
    private static final float BAR_HEIGHT = 11.0f;
    private static final float HEX_HEIGHT = 15.0f;
    private static final float SWATCH = 13.0f;
    private static final float SWATCH_GAP = 3.0f;
    private static final int SWATCH_COLUMNS = 6;
    private static final float SWATCH_ROWS = 2.0f;
    private static final float MARKER = 3.4f;
    private static final float LABEL_SCALE = 0.85f;
    private static final float TITLE_SCALE = 0.95f;
    private static final float TITLE_BASELINE = 6.0f;
    private static final float RESET_HEIGHT = 15.0f;
    private static final float HOVER_ALPHA = 0.22f;
    private static final float CLOSE_BOX = 12.0f;
    private static final float CLOSE_MARK = 3.0f;
    private static final float CLOSE_STROKE = 1.1f;
    private static final float CLOSE_TOP = 3.0f;
    private static final float ACTION_HEIGHT = 15.0f;
    private static final float ACTION_GAP = 6.0f;
    private static final float ACTION_SINK = 1.2f;
    private static final float ACTION_FLASH = 0.45f;
    private static final float PULSE_SECONDS = 0.9f;
    private static final float PANEL_RADIUS = 9.0f;
    private static final float PANEL_LIFT = 0.25f;

    private static final float ENTER_SECONDS = 0.45f;
    private static final float BURN_SECONDS = 0.32f;
    private static final float MAX_STEP = 1.0f / 15.0f;
    private static final float BURN_MARGIN = 28.0f;
    private static final float WINDOW_CHARGE = 0.55f;
    private static final long SERVER_GAP_MS = 400L;

    private static final int[] PRESETS = {
            0xFFE7E9F4, 0xFFCE2A22, 0xFFE8A33D, 0xFFE6D34A, 0xFF3FDB6A, 0xFF4FC9B0,
            0xFF4F9BE8, 0xFFA96BE0, 0xFFF07FB8, 0xFF8A8FA6, 0xFF1B1B24, 0xFF0C0C10
    };

    // WHY: цвет, который держит сервер, уходит командой: протяжка по полю слала бы её каждый кадр,
    // WHY: а сторож частоты на сервере молча выбрасывает лишнее, и в окне оставался бы один цвет,
    // WHY: а в мире другой; поэтому уходит последнее значение не чаще раза в SERVER_GAP_MS.
    // WHY: Альфа там не значит ничего, мир рисует метки и зоны непрозрачными
    public enum Kind {
        LOCAL,
        SERVER
    }

    private enum Grab {
        NONE,
        FIELD,
        HUE,
        ALPHA,
        MOVE
    }

    private final String owner;
    private final Kind kind;
    private final Component title;
    private final IntConsumer apply;
    private final Runnable clear;

    private float left;
    private float top;
    private float hue;
    private float saturation;
    private float brightness = 1.0f;
    private float alpha = 1.0f;

    private Grab grab = Grab.NONE;
    private float grabX;
    private float grabY;
    private boolean held;
    private long sentAt;
    private boolean typing;
    private String typed = "";
    private int hexValue;
    private String hexText;
    private float copyPulse;
    private float pastePulse;

    private float entered;
    private float burned = -1.0f;
    private long stamp = -1L;
    private float screenWidth;
    private float screenHeight;

    private UiMotionSet set;
    private UiShards shards;
    private long grain;

    public PaletteWindow(String owner, Kind kind, Component title, int color, IntConsumer apply, Runnable clear) {
        this.owner = owner;
        this.kind = kind;
        this.title = title;
        this.apply = apply;
        this.clear = clear;
        this.set = chosenMotion();
        adopt(color);
    }

    public String owner() {
        return owner;
    }

    public void place(float x, float y) {
        left = x;
        top = y;
    }

    public boolean closing() {
        return burned >= 0.0f;
    }

    public boolean gone() {
        return burned >= leaveSpan() && !held;
    }

    // WHY: набор движения защёлкивается на каждой стадии отдельно: смена его на экране кастомизации
    // WHY: не должна ломать уже идущее появление или уход открытого окна
    public void beginClose() {
        if (closing()) return;

        typing = false;
        grab = Grab.NONE;
        burned = 0.0f;
        set = chosenMotion();
        shards = null;
        grain = System.nanoTime();
    }

    private static UiMotionSet chosenMotion() {
        return UiMotion.of(Minecraft.getInstance().screen);
    }

    private float enterSpan() {
        return set == UiMotionSet.GLASS ? UiMotionSet.GLASS.enterSeconds() : ENTER_SECONDS;
    }

    private float leaveSpan() {
        return set == UiMotionSet.GLASS
                ? UiMotionSet.GLASS.leaveSeconds(Minecraft.getInstance().level != null)
                : BURN_SECONDS;
    }

    public boolean covers(double pointX, double pointY) {
        return !closing() && within(pointX, pointY, left, top, WIDTH, height());
    }

    public float height() {
        return opaque() ? HEIGHT - BAR_HEIGHT - GAP : HEIGHT;
    }

    private boolean opaque() {
        return kind == Kind.SERVER;
    }

    public boolean dragging() {
        return grab != Grab.NONE;
    }

    private void adopt(int color) {
        alpha = opaque() ? 1.0f : ((color >>> 24) & 0xFF) / 255.0f;
        float[] hsb = UiColor.toHsb(color);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
    }

    public int color() {
        int rgb = UiColor.fromHsb(hue, saturation, brightness) & 0xFFFFFF;
        return ((int) (UiAnim.clamp01(alpha) * 255.0f) << 24) | rgb;
    }

    public void render(GuiGraphics graphics, float width, float height, int mouseX, int mouseY) {
        screenWidth = width;
        screenHeight = height;
        left = clamp(left, width - WIDTH);
        top = clamp(top, height - height());
        advance();

        graphics.flush();
        UiShards field = splitting() ? field() : null;
        UiBackdrop.scatter(null, field, phase(), width, height);
        boolean staged = animating() && UiStage.beginWindow(field, phase(), width, height);
        if (closing() && !staged) {
            burned = leaveSpan();
            UiBackdrop.plain();
            return;
        }

        try {
            paint(graphics, mouseX, mouseY);
        } finally {
            UiBackdrop.plain();
            if (staged) {
                graphics.flush();
                UiStage.endWindow();
            }
        }
        if (!staged) return;

        resolve(graphics, width, height);
    }

    // WHY: у раскола осколки уходят за габарит окна, поэтому он рисуется треугольниками по своим
    // WHY: местам, а не композитным квадом по прямоугольнику окна, как вход из света и горение
    private void resolve(GuiGraphics graphics, float width, float height) {
        if (splitting()) {
            UiShatter.draw(graphics, width, height, UiStage.windowTexture(), field(), phase());
            return;
        }

        float mode = closing() ? UiReveal.BURN : enterMode();
        UiAssemble.window(graphics, width, height, UiStage.windowTexture(), left, top, WIDTH, height(),
                BURN_MARGIN, phase(), seconds(), mode, WINDOW_CHARGE);
    }

    private boolean splitting() {
        return closing() && set == UiMotionSet.GLASS;
    }

    // WHY: замощение нужно раньше композита: через него переносятся и копия кадра в стадии окна, и
    // WHY: снимок подложки, иначе мир за улетевшим осколком остаётся с места, где ячейка была
    private UiShards field() {
        if (shards == null) shards = UiShards.of(left, top, WIDTH, height(), grain);
        return shards;
    }

    private float enterMode() {
        return set == UiMotionSet.GLASS ? UiReveal.FOCUS : UiReveal.ENTER;
    }

    private void advance() {
        long frame = UiFrame.frame();
        if (frame == stamp) return;
        stamp = frame;
        settle();

        float step = Math.min(MAX_STEP, UiFrame.delta());
        copyPulse = Math.max(0.0f, copyPulse - step / PULSE_SECONDS);
        pastePulse = Math.max(0.0f, pastePulse - step / PULSE_SECONDS);
        if (closing()) {
            burned += step;
            return;
        }
        entered += step;
    }

    private boolean animating() {
        if (!UiReveal.enabled()) return false;
        return closing() || entered < enterSpan();
    }

    private float phase() {
        return closing() ? UiAnim.clamp01(burned / leaveSpan()) : UiAnim.clamp01(entered / enterSpan());
    }

    private float seconds() {
        return closing() ? burned : entered;
    }

    private void paint(GuiGraphics graphics, int mouseX, int mouseY) {
        UiGlass.window(graphics, left, top, WIDTH, height(), PANEL_RADIUS, 1.0f, PANEL_LIFT);
        paintTitle(graphics, mouseX, mouseY);
        paintField(graphics);
        paintHue(graphics);
        if (!opaque()) paintAlpha(graphics);
        paintHex(graphics);
        paintActions(graphics, mouseX, mouseY);
        paintPresets(graphics);
        paintReset(graphics, mouseX, mouseY);
    }

    private void paintTitle(GuiGraphics graphics, int mouseX, int mouseY) {
        UiRender.textCentered(graphics, font(), title, left + WIDTH / 2.0f, top + TITLE_BASELINE, TITLE_SCALE,
                UiAccent.text(), false);

        float x = closeLeft();
        float y = closeTop();
        boolean hovered = within(mouseX, mouseY, x, y, CLOSE_BOX, CLOSE_BOX);
        if (hovered) {
            UiRender.panel(graphics, x, y, CLOSE_BOX, CLOSE_BOX, 3.0f,
                    UiTheme.alpha(UiAccent.color(), HOVER_ALPHA));
        }

        float centerX = x + CLOSE_BOX / 2.0f;
        float centerY = y + CLOSE_BOX / 2.0f;
        int tint = hovered ? UiAccent.text() : UiAccent.textDim();
        UiRender.line(graphics, centerX - CLOSE_MARK, centerY - CLOSE_MARK,
                centerX + CLOSE_MARK, centerY + CLOSE_MARK, CLOSE_STROKE, tint);
        UiRender.line(graphics, centerX + CLOSE_MARK, centerY - CLOSE_MARK,
                centerX - CLOSE_MARK, centerY + CLOSE_MARK, CLOSE_STROKE, tint);
    }

    private void paintField(GuiGraphics graphics) {
        float x = fieldLeft();
        float y = fieldTop();
        int pure = UiColor.fromHsb(hue, 1.0f, 1.0f);

        UiRender.gradientAcross(graphics, x, y, FIELD_WIDTH, FIELD_HEIGHT, 0xFFFFFFFF, pure);
        UiRender.gradient(graphics, x, y, FIELD_WIDTH, FIELD_HEIGHT, 0x00000000, 0xFF000000);

        float markX = x + FIELD_WIDTH * saturation;
        float markY = y + FIELD_HEIGHT * (1.0f - brightness);
        UiRender.ring(graphics, markX, markY, MARKER, 1.4f, 1.0f, 0xFFFFFFFF);
        UiRender.ring(graphics, markX, markY, MARKER + 1.2f, 1.0f, 1.0f, 0x99000000);
    }

    private void paintHue(GuiGraphics graphics) {
        float x = hueLeft();
        float y = fieldTop();
        int bands = 12;
        float band = FIELD_HEIGHT / bands;
        for (int i = 0; i < bands; i++) {
            int from = UiColor.fromHsb(i / (float) bands, 1.0f, 1.0f);
            int to = UiColor.fromHsb((i + 1) / (float) bands, 1.0f, 1.0f);
            UiRender.gradient(graphics, x, y + i * band, HUE_WIDTH, band + 0.5f, from, to);
        }
        float markY = y + FIELD_HEIGHT * hue;
        UiRender.panel(graphics, x - 1.5f, markY - 1.2f, HUE_WIDTH + 3.0f, 2.4f, 1.2f, 0xFFFFFFFF);
    }

    private void paintAlpha(GuiGraphics graphics) {
        float x = fieldLeft();
        float y = alphaTop();
        int solid = UiColor.fromHsb(hue, saturation, brightness);

        UiGlass.sunken(graphics, x, y, FIELD_WIDTH, BAR_HEIGHT, BAR_HEIGHT / 2.0f, 0.9f);
        UiRender.gradientAcross(graphics, x + 1.0f, y + 1.0f, FIELD_WIDTH - 2.0f, BAR_HEIGHT - 2.0f,
                solid & 0x00FFFFFF, solid);
        float markX = x + FIELD_WIDTH * UiAnim.clamp01(alpha);
        UiRender.panel(graphics, markX - 1.2f, y - 1.5f, 2.4f, BAR_HEIGHT + 3.0f, 1.2f, 0xFFFFFFFF);
    }

    private void paintHex(GuiGraphics graphics) {
        float x = fieldLeft();
        float y = hexTop();
        UiGlass.sunken(graphics, x, y, FIELD_WIDTH, HEX_HEIGHT, 4.0f, 0.92f);

        String shown = typing ? typed + caret() : hex();
        UiRender.textCentered(graphics, font(), shown, x + FIELD_WIDTH / 2.0f,
                UiRender.centerY(y, HEX_HEIGHT, LABEL_SCALE), LABEL_SCALE, UiAccent.text(), false);
    }

    private void paintActions(GuiGraphics graphics, int mouseX, int mouseY) {
        paintAction(graphics, mouseX, mouseY, copyLeft(), copyPulse,
                copyPulse > 0.0f ? "battlecraft.custom.copied" : "battlecraft.custom.copy");
        paintAction(graphics, mouseX, mouseY, pasteLeft(), pastePulse,
                pastePulse > 0.0f ? "battlecraft.custom.pasted" : "battlecraft.custom.paste");
    }

    private void paintAction(GuiGraphics graphics, int mouseX, int mouseY, float x, float pulse, String label) {
        float y = actionTop();
        float width = actionWidth();
        boolean hovered = within(mouseX, mouseY, x, y, width, ACTION_HEIGHT);
        float sink = ACTION_SINK * pulse;

        UiGlass.sunken(graphics, x, y, width, ACTION_HEIGHT, 4.0f, 0.92f);
        float fill = (hovered ? HOVER_ALPHA : 0.0f) + ACTION_FLASH * pulse;
        if (fill > 0.002f) {
            UiRender.panel(graphics, x + sink, y + sink, width - sink * 2.0f, ACTION_HEIGHT - sink * 2.0f, 4.0f,
                    UiTheme.alpha(UiAccent.color(), fill));
        }

        int tint = pulse > 0.0f ? UiAccent.text() : (hovered ? UiAccent.text() : UiAccent.textDim());
        UiRender.textCentered(graphics, font(), Component.translatable(label), x + width / 2.0f,
                UiRender.centerY(y, ACTION_HEIGHT, LABEL_SCALE), LABEL_SCALE, tint, false);
    }

    private void paintPresets(GuiGraphics graphics) {
        for (int index = 0; index < PRESETS.length; index++) {
            UiRender.panel(graphics, swatchLeft(index), swatchTop(index), SWATCH, SWATCH, 3.0f, PRESETS[index]);
        }
    }

    private void paintReset(GuiGraphics graphics, int mouseX, int mouseY) {
        float x = fieldLeft();
        float y = resetTop();
        boolean hovered = clear != null && within(mouseX, mouseY, x, y, resetWidth(), RESET_HEIGHT);

        UiGlass.sunken(graphics, x, y, resetWidth(), RESET_HEIGHT, 4.0f, 0.92f);
        if (hovered) {
            UiRender.panel(graphics, x, y, resetWidth(), RESET_HEIGHT, 4.0f,
                    UiTheme.alpha(UiAccent.color(), HOVER_ALPHA));
        }

        int tint = clear == null ? UiAccent.textFaint() : (hovered ? UiAccent.text() : UiAccent.textDim());
        UiRender.textCentered(graphics, font(), Component.translatable("battlecraft.custom.reset.color"),
                x + resetWidth() / 2.0f, UiRender.centerY(y, RESET_HEIGHT, LABEL_SCALE), LABEL_SCALE, tint, false);
    }

    private String hex() {
        int argb = color();
        if (argb == hexValue && hexText != null) return hexText;

        hexValue = argb;
        hexText = opaque() ? String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF)
                : String.format(Locale.ROOT, "#%08X", argb);
        return hexText;
    }

    private static String caret() {
        return (System.currentTimeMillis() / 500L) % 2L == 0L ? "_" : "";
    }

    private float fieldLeft() {
        return left + PAD;
    }

    private float fieldTop() {
        return top + TITLE_ROW;
    }

    private float hueLeft() {
        return fieldLeft() + FIELD_WIDTH + GAP;
    }

    private float alphaTop() {
        return fieldTop() + FIELD_HEIGHT + GAP;
    }

    private float hexTop() {
        return opaque() ? alphaTop() : alphaTop() + BAR_HEIGHT + GAP;
    }

    private float actionTop() {
        return hexTop() + HEX_HEIGHT + GAP;
    }

    private float actionWidth() {
        return (resetWidth() - ACTION_GAP) / 2.0f;
    }

    private float copyLeft() {
        return fieldLeft();
    }

    private float pasteLeft() {
        return fieldLeft() + actionWidth() + ACTION_GAP;
    }

    private float presetTop() {
        return actionTop() + ACTION_HEIGHT + GAP;
    }

    private float swatchLeft(int index) {
        return fieldLeft() + (index % SWATCH_COLUMNS) * (SWATCH + SWATCH_GAP);
    }

    private float swatchTop(int index) {
        return presetTop() + (index / SWATCH_COLUMNS) * (SWATCH + SWATCH_GAP);
    }

    private float resetTop() {
        return presetTop() + SWATCH_ROWS * SWATCH + (SWATCH_ROWS - 1.0f) * SWATCH_GAP + GAP;
    }

    private float resetWidth() {
        return FIELD_WIDTH + GAP + HUE_WIDTH;
    }

    private float closeLeft() {
        return left + WIDTH - PAD / 2.0f - CLOSE_BOX;
    }

    private float closeTop() {
        return top + CLOSE_TOP;
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (grabClose(mouseX, mouseY) || grabField(mouseX, mouseY) || grabHue(mouseX, mouseY)) return true;
        if (grabAlpha(mouseX, mouseY) || grabHex(mouseX, mouseY)) return true;
        if (grabCopy(mouseX, mouseY) || grabPaste(mouseX, mouseY)) return true;
        if (grabPreset(mouseX, mouseY) || grabReset(mouseX, mouseY)) return true;

        grab = Grab.MOVE;
        grabX = (float) (mouseX - left);
        grabY = (float) (mouseY - top);
        return true;
    }

    private boolean grabClose(double mouseX, double mouseY) {
        if (!within(mouseX, mouseY, closeLeft(), closeTop(), CLOSE_BOX, CLOSE_BOX)) return false;

        UiSound.press();
        beginClose();
        return true;
    }

    private boolean grabField(double mouseX, double mouseY) {
        if (!within(mouseX, mouseY, fieldLeft(), fieldTop(), FIELD_WIDTH, FIELD_HEIGHT)) return false;

        grab = Grab.FIELD;
        seekField(mouseX, mouseY);
        return true;
    }

    private boolean grabHue(double mouseX, double mouseY) {
        if (!within(mouseX, mouseY, hueLeft(), fieldTop(), HUE_WIDTH, FIELD_HEIGHT)) return false;

        grab = Grab.HUE;
        seekHue(mouseY);
        return true;
    }

    private boolean grabAlpha(double mouseX, double mouseY) {
        if (opaque() || !within(mouseX, mouseY, fieldLeft(), alphaTop(), FIELD_WIDTH, BAR_HEIGHT)) return false;

        grab = Grab.ALPHA;
        seekAlpha(mouseX);
        return true;
    }

    private boolean grabHex(double mouseX, double mouseY) {
        if (!within(mouseX, mouseY, fieldLeft(), hexTop(), FIELD_WIDTH, HEX_HEIGHT)) return false;

        UiSound.press();
        typing = true;
        typed = "";
        return true;
    }

    private boolean grabCopy(double mouseX, double mouseY) {
        if (!within(mouseX, mouseY, copyLeft(), actionTop(), actionWidth(), ACTION_HEIGHT)) return false;

        UiSound.press();
        Minecraft.getInstance().keyboardHandler.setClipboard(hex());
        copyPulse = 1.0f;
        return true;
    }

    private boolean grabPaste(double mouseX, double mouseY) {
        if (!within(mouseX, mouseY, pasteLeft(), actionTop(), actionWidth(), ACTION_HEIGHT)) return false;

        Integer parsed = parse(Minecraft.getInstance().keyboardHandler.getClipboard());
        if (parsed == null) {
            UiSound.chip(false);
            return true;
        }

        UiSound.press();
        adopt(parsed);
        push();
        pastePulse = 1.0f;
        return true;
    }

    private static Integer parse(String value) {
        if (value == null) return null;

        String digits = value.trim();
        if (digits.startsWith("#")) digits = digits.substring(1);
        if (digits.length() == 6) digits = "FF" + digits;
        if (digits.length() != 8) return null;

        try {
            return (int) Long.parseLong(digits, 16);
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    private boolean grabPreset(double mouseX, double mouseY) {
        for (int index = 0; index < PRESETS.length; index++) {
            if (!within(mouseX, mouseY, swatchLeft(index), swatchTop(index), SWATCH, SWATCH)) continue;

            UiSound.press();
            adopt(PRESETS[index]);
            push();
            return true;
        }
        return false;
    }

    private boolean grabReset(double mouseX, double mouseY) {
        if (clear == null || !within(mouseX, mouseY, fieldLeft(), resetTop(), resetWidth(), RESET_HEIGHT)) {
            return false;
        }

        UiSound.press();
        beginClose();
        clear.run();
        return true;
    }

    private static boolean within(double pointX, double pointY, float x, float y, float width, float height) {
        return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        switch (grab) {
            case FIELD -> seekField(mouseX, mouseY);
            case HUE -> seekHue(mouseY);
            case ALPHA -> seekAlpha(mouseX);
            case MOVE -> moveTo(mouseX, mouseY);
            case NONE -> {
                return false;
            }
        }
        return true;
    }

    public boolean mouseReleased() {
        if (grab == Grab.NONE) return false;

        grab = Grab.NONE;
        return true;
    }

    private void settle() {
        if (!held || grab == Grab.FIELD || grab == Grab.HUE) return;
        if (System.currentTimeMillis() - sentAt < SERVER_GAP_MS) return;

        held = false;
        sentAt = System.currentTimeMillis();
        if (apply != null) apply.accept(color());
    }

    private void moveTo(double mouseX, double mouseY) {
        left = clamp((float) (mouseX - grabX), screenWidth - WIDTH);
        top = clamp((float) (mouseY - grabY), screenHeight - height());
    }

    private static float clamp(float value, float limit) {
        return Math.max(0.0f, Math.min(value, Math.max(0.0f, limit)));
    }

    private void seekField(double mouseX, double mouseY) {
        saturation = UiAnim.clamp01((float) ((mouseX - fieldLeft()) / FIELD_WIDTH));
        brightness = 1.0f - UiAnim.clamp01((float) ((mouseY - fieldTop()) / FIELD_HEIGHT));
        push();
    }

    private void seekHue(double mouseY) {
        hue = UiAnim.clamp01((float) ((mouseY - fieldTop()) / FIELD_HEIGHT));
        push();
    }

    private void seekAlpha(double mouseX) {
        alpha = UiAnim.clamp01((float) ((mouseX - fieldLeft()) / FIELD_WIDTH));
        push();
    }

    private void push() {
        if (kind == Kind.SERVER) {
            held = true;
            return;
        }
        if (apply != null) apply.accept(color());
    }

    public boolean keyPressed(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            beginClose();
            return true;
        }
        if (!typing) return false;
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!typed.isEmpty()) typed = typed.substring(0, typed.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            commit();
            return true;
        }
        return true;
    }

    public boolean charTyped(char symbol) {
        if (!typing || typed.length() >= (opaque() ? 6 : 8)) return false;

        char upper = Character.toUpperCase(symbol);
        if ((upper >= '0' && upper <= '9') || (upper >= 'A' && upper <= 'F')) {
            typed += upper;
            return true;
        }
        return symbol == '#';
    }

    private void commit() {
        String entered = typed;
        typing = false;
        typed = "";
        if (entered.length() != 6 && entered.length() != 8) return;

        try {
            long parsed = Long.parseLong(entered, 16);
            adopt(entered.length() == 6 ? (int) (0xFF000000L | parsed) : (int) parsed);
            push();
        } catch (NumberFormatException malformed) {
            UiSound.chip(false);
        }
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
