package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.Spring;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiInput;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public abstract class MenuRow extends AbstractWidget implements GlidingRow {
    // WHY: апдейтер экрана пересобирает строки, а пересборка теряет набранное: пока строка ловит
    // WHY: клавиши, экран обязан стоять на месте, иначе цифра или клавиша пропадают на полпути

    protected static final float LABEL_SCALE = 1.0f;
    protected static final float TRACKING = 0.3f;
    protected static final float PAD = UiMetrics.PAD_WIDE;
    protected static final float SURFACE_ALPHA = 1.0f;
    protected static final float ALIGN_LEFT = 0.0f;
    protected static final float ALIGN_CENTER = 0.5f;
    private static final float NOTE_SCALE = 0.62f;
    private static final float NOTE_SPLIT = 0.54f;

    private static final float HOVER_RESPONSE = 0.30f;
    private static final float HOVER_DAMPING = 0.92f;
    private static final float BASE_LIFT = 0.06f;
    private static final float HOVER_LIFT = 0.24f;
    private static final float DIM_WELL_ALPHA = 0.62f;
    private static final float DIM_VALUE_ALPHA = 0.38f;

    private static final float PRESS_LIFT = 0.20f;
    private static final float MARK_WIDTH = 2.2f;
    private static final float MARK_INSET = 3.5f;
    private static final float MARK_ALPHA = 0.85f;
    private static final float MARK_FADE = 0.02f;

    private static final float PRESS_RESPONSE = 0.24f;
    private static final float PRESS_DAMPING = 0.60f;
    private static final float PRESS_SQUASH = 0.018f;
    private static final float PRESS_LIMIT = 0.35f;
    private static final float SMOOTH_THRESHOLD = 0.0004f;

    private static final float APPEAR_MS = 190.0f;
    private static final float STAGGER_MS = 20.0f;

    private final Spring hover = new Spring(HOVER_RESPONSE, HOVER_DAMPING, 0.0f);
    private final Spring press = new Spring(PRESS_RESPONSE, PRESS_DAMPING, 0.0f);
    private boolean announced;
    private boolean held;
    private Component blockedReason;
    private Component note;
    private Supplier<Component> explanation;
    private long shownAt = System.currentTimeMillis();
    private float staggerMs;
    private float slideFrom;
    private final RowAnchor anchor = new RowAnchor();

    protected MenuRow(int x, int y, int width, int height, Component label) {
        super(x, y, width, height, label);
    }

    protected abstract void renderValue(GuiGraphics graphics, int mouseX, int mouseY, float focus);

    public boolean capturing() {
        return false;
    }

    // WHY: анимация живёт в самой строке, а экран пересобирает строки от любой правки данных:
    // WHY: без передачи состояния новой строке движение обрывалось на первом же кадре и
    // WHY: переключение значения выглядело мгновенной подменой
    public void adopt(MenuRow previous) {
    }

    public MenuRow note(Component text) {
        note = text;
        return this;
    }

    Component note() {
        return note;
    }

    public MenuRow hint(Component text) {
        explanation = text == null ? null : () -> text;
        return this;
    }

    public MenuRow hint(Supplier<Component> source) {
        explanation = source;
        return this;
    }

    public MenuRow hint(String key) {
        return hint(MenuHint.of(key));
    }

    protected Component explanation() {
        return explanation == null ? null : explanation.get();
    }

    public void block(Component reason) {
        blockedReason = reason;
    }

    public void unblock() {
        blockedReason = null;
    }

    public boolean blocked() {
        return blockedReason != null;
    }

    protected boolean dimmed() {
        return blocked();
    }

    protected boolean faded() {
        return blocked() || !this.active;
    }

    protected float valueAlpha() {
        return dimmed() ? DIM_VALUE_ALPHA : 1.0f;
    }

    protected int valueTint(int color) {
        return dimmed() ? UiAccent.faint() : color;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (blocked() && this.visible && this.clicked(mouseX, mouseY)) {
            MenuFeedback.show(blockedReason, true);
            UiSound.deny();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
    public boolean isMouseOver(double mouseX, double mouseY) {
        return anchor.covers(mouseY) && super.isMouseOver(mouseX, mouseY);
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return anchor.covers(mouseY) && super.clicked(mouseX, mouseY);
    }

    public void stagger(int index, float slide) {
        staggerMs = index * STAGGER_MS;
        shownAt = System.currentTimeMillis();
        slideFrom = slide;
    }

    private float appear() {
        if (APPEAR_MS <= 0.0f) return 1.0f;
        return UiAnim.easeOut((System.currentTimeMillis() - shownAt - staggerMs) / APPEAR_MS);
    }

    protected float valueLeft() {
        return getX() + width * 0.52f;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean focused = !faded() && UiInput.pointed(this);
        if (focused != announced) {
            announced = focused;
            if (focused) UiSound.hover();
        }
        if (this.visible && this.isHovered()) {
            MenuHint.offer(this, explanation(), getY(), height, mouseX);
        }

        if (held && !UiInput.mouseDown()) held = false;

        float delta = UiFrame.delta();
        float focus = hover.to(focused ? 1.0f : 0.0f, delta);
        float pushed = Math.max(-PRESS_LIMIT, Math.min(1.0f, press.to(held ? 1.0f : 0.0f, delta)));

        float appear = appear();
        float squash = pushed * PRESS_SQUASH;
        boolean rawScale = UiRender.rawScale(false);
        pushSquash(graphics, squash, (1.0f - appear) * slideFrom);
        boolean quantized = UiRender.rawScale(rawScale || Math.abs(squash) > SMOOTH_THRESHOLD);

        try {
            renderBody(graphics, mouseX, mouseY, focus, pushed);
        } finally {
            UiRender.rawScale(quantized);
            graphics.pose().popPose();
        }
    }

    private void renderBody(GuiGraphics graphics, int mouseX, int mouseY, float focus, float pushed) {
        float radius = UiMetrics.radius(height);
        UiGlass.panel(graphics, getX(), getY(), width, height, radius, SURFACE_ALPHA,
                dimmed() ? 0.0f : BASE_LIFT + focus * HOVER_LIFT + Math.max(0.0f, pushed) * PRESS_LIFT);
        if (dimmed()) UiGlass.sunken(graphics, getX(), getY(), width, height, radius, DIM_WELL_ALPHA);
        renderFocusMark(graphics, focus);

        renderLabel(graphics, focus);
        renderValue(graphics, mouseX, mouseY, focus);
    }

    private void renderLabel(GuiGraphics graphics, float focus) {
        float boxWidth = width * 0.5f - PAD;
        if (note == null) {
            UiRender.textTrackedBox(graphics, font(), getMessage(), getX() + PAD, getY(), height,
                    boxWidth, LABEL_SCALE, TRACKING, labelColor(focus), false, ALIGN_LEFT);
            return;
        }

        float top = height * NOTE_SPLIT;
        UiRender.textTrackedBox(graphics, font(), getMessage(), getX() + PAD, getY(), top,
                boxWidth, LABEL_SCALE, TRACKING, labelColor(focus), false, ALIGN_LEFT);
        UiRender.textTrackedBox(graphics, font(), note, getX() + PAD, getY() + top, height - top,
                boxWidth, NOTE_SCALE, 0.0f, UiAccent.textFaint(), false, ALIGN_LEFT);
    }

    private void renderFocusMark(GuiGraphics graphics, float focus) {
        if (focus <= MARK_FADE) return;

        UiRender.panel(graphics, getX() + MARK_INSET, getY() + MARK_INSET, MARK_WIDTH,
                height - MARK_INSET * 2.0f, MARK_WIDTH / 2.0f,
                UiTheme.alpha(UiAccent.color(), focus * MARK_ALPHA));
    }

    // WHY: строка втягивается на нажатии одинаково со всех сторон, а не долей своей ширины: при
    // WHY: общем масштабе значение у правого края широкой строки ехало влево и отскакивало обратно,
    // WHY: и в конце нажатия число заметно смещалось относительно своих стрелок
    private void pushSquash(GuiGraphics graphics, float squash, float lift) {
        float centerX = getX() + width / 2.0f;
        float centerY = getY() + height / 2.0f;
        float across = width > 0 ? 1.0f - squash * height / (float) width : 1.0f;

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY + lift, 0.0f);
        graphics.pose().scale(across, 1.0f - squash, 1.0f);
        graphics.pose().translate(-centerX, -centerY, 0.0f);
    }

    protected void flash() {
        press.snap(1.0f);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        held = true;
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        held = false;
    }

    protected int labelColor(float focus) {
        if (faded()) return UiAccent.textFaint();
        return UiTheme.mix(UiAccent.text(), UiTheme.WHITE, focus);
    }

    protected static net.minecraft.client.gui.Font font() {
        return Minecraft.getInstance().font;
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager sounds) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, getMessage());
    }
}
