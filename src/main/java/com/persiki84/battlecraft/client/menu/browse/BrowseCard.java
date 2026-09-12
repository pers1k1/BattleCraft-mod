package com.persiki84.battlecraft.client.menu.browse;

import com.persiki84.shared.client.menu.GlidingRow;
import com.persiki84.shared.client.menu.RowAnchor;
import com.persiki84.shared.client.menu.ScrollLanes;
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
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public abstract class BrowseCard extends AbstractWidget implements GlidingRow {
    protected static final int ICON = 26;
    protected static final float PAD = UiMetrics.PAD;
    protected static final float TEXT_GAP = 7.0f;
    protected static final float TITLE_SCALE = 1.0f;
    protected static final float NOTE_SCALE = 0.68f;
    protected static final float TRACKING = 0.3f;

    private static final float HOVER_RESPONSE = 0.30f;
    private static final float HOVER_DAMPING = 0.92f;
    private static final float PRESS_RESPONSE = 0.24f;
    private static final float PRESS_DAMPING = 0.60f;
    private static final float BASE_LIFT = 0.06f;
    private static final float HOVER_LIFT = 0.22f;
    private static final float PICKED_LIFT = 0.30f;
    private static final float MARK_WIDTH = 2.2f;
    private static final float MARK_INSET = 3.5f;
    private static final float MARK_FADE = 0.02f;
    private static final float APPEAR_MS = 190.0f;
    private static final float STAGGER_MS = 22.0f;
    private static final float APPEAR_SLIDE = 6.0f;
    private static final long DOUBLE_CLICK_MS = 250L;
    private static final float TITLE_SPLIT = 0.46f;

    private final BrowseScreen owner;
    private final Spring hover = new Spring(HOVER_RESPONSE, HOVER_DAMPING, 0.0f);
    private final Spring press = new Spring(PRESS_RESPONSE, PRESS_DAMPING, 0.0f);
    private final RowAnchor anchor = new RowAnchor();
    private boolean announced;
    private boolean held;
    private boolean staggered;
    private long shownAt = System.currentTimeMillis();
    private float staggerMs;
    private long lastClick;

    protected BrowseCard(BrowseScreen owner, int width, int height, Component label) {
        super(0, 0, width, height, label);
        this.owner = owner;
    }

    protected abstract void paintIcon(GuiGraphics graphics, float x, float y, float size);

    protected abstract Component titleLine();

    protected abstract Component noteLine();

    protected void paintTrailing(GuiGraphics graphics, float rightX, float focus) {
    }

    protected float trailingWidth() {
        return 0.0f;
    }

    public boolean picked() {
        return owner.picked() == this;
    }

    public void close() {
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

        boolean clipped = anchor.clip(graphics, getX(), width, getY(), height);
        try {
            super.render(graphics, mouseX, anchor.pointer(mouseY), partialTick);
        } finally {
            if (clipped) graphics.disableScissor();
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return anchor.covers(mouseY) && super.isMouseOver(mouseX, mouseY);
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return anchor.covers(mouseY) && super.clicked(mouseX, mouseY);
    }

    // WHY: раскладка пересобирается на каждый выбор и прокрутку, поэтому появление
    // WHY: заводится один раз на карточку, иначе список переигрывает вход от любого клика
    public void stagger(int index) {
        if (staggered) return;

        staggered = true;
        staggerMs = index * STAGGER_MS;
        shownAt = System.currentTimeMillis();
    }

    public abstract String identity();

    @Override
    public void onClick(double mouseX, double mouseY) {
        held = true;
        owner.pick(this);
        long now = Util.getMillis();
        if (now - lastClick < DOUBLE_CLICK_MS) owner.enter();
        lastClick = now;
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        held = false;
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager sounds) {
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean focused = this.active && UiInput.pointed(this);
        if (focused != announced) {
            announced = focused;
            if (focused) UiSound.hover();
        }
        if (held && !UiInput.mouseDown()) held = false;

        float delta = UiFrame.delta();
        float focus = hover.to(focused ? 1.0f : 0.0f, delta);
        press.to(held ? 1.0f : 0.0f, delta);

        float appear = UiAnim.easeOut((System.currentTimeMillis() - shownAt - staggerMs) / APPEAR_MS);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, (1.0f - appear) * APPEAR_SLIDE, 0.0f);
        try {
            paintBody(graphics, focus);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void paintBody(GuiGraphics graphics, float focus) {
        float radius = UiMetrics.radius(ICON);
        UiGlass.panel(graphics, getX(), getY(), width, height, radius, 1.0f, lift(focus));
        if (picked()) mark(graphics);

        float iconY = getY() + (height - ICON) / 2.0f;
        paintIcon(graphics, getX() + PAD, iconY, ICON);
        paintText(graphics, focus);
        paintTrailing(graphics, getX() + width - PAD, focus);
    }

    private float lift(float focus) {
        return BASE_LIFT + focus * HOVER_LIFT + (picked() ? PICKED_LIFT : 0.0f);
    }

    private void mark(GuiGraphics graphics) {
        UiRender.panel(graphics, getX() + MARK_INSET, getY() + MARK_INSET, MARK_WIDTH,
                height - MARK_INSET * 2.0f, MARK_WIDTH / 2.0f,
                UiTheme.alpha(UiAccent.color(), 1.0f - MARK_FADE));
    }

    private void paintText(GuiGraphics graphics, float focus) {
        float left = getX() + PAD + ICON + TEXT_GAP;
        float boxWidth = getX() + width - PAD - trailingWidth() - TEXT_GAP - left;
        if (boxWidth <= 0.0f) return;

        float split = height * TITLE_SPLIT;
        UiRender.textTrackedBox(graphics, font(), titleLine(), left, getY() + PAD * 0.4f, split,
                boxWidth, TITLE_SCALE, TRACKING, titleColor(focus), false, 0.0f);
        UiRender.textTrackedBox(graphics, font(), noteLine(), left, getY() + split, height - split - PAD * 0.4f,
                boxWidth, NOTE_SCALE, 0.0f, UiAccent.textFaint(), false, 0.0f);
    }

    private int titleColor(float focus) {
        if (!this.active) return UiAccent.textFaint();
        return UiTheme.mix(UiAccent.text(), UiTheme.WHITE, Math.max(focus, picked() ? 0.5f : 0.0f));
    }

    protected static void blitSquare(GuiGraphics graphics, ResourceLocation texture,
                                     float x, float y, float size) {
        UiRender.standardBlend();
        int span = Math.round(size);
        graphics.blit(texture, Math.round(x), Math.round(y), 0.0f, 0.0f, span, span, span, span);
    }

    protected static Font font() {
        return Minecraft.getInstance().font;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, titleLine());
    }
}
