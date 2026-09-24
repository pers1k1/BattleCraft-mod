package com.persiki84.shared.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import com.persiki84.shared.client.menu.GlidingRow;
import com.persiki84.shared.client.menu.MenuHint;
import com.persiki84.shared.client.menu.RowAnchor;
import com.persiki84.shared.client.menu.ScrollLanes;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class UiButton extends AbstractButton implements GlidingRow, SelfPainted {
    private static final float HOVER_RESPONSE = 0.30f;
    private static final float HOVER_DAMPING = 0.92f;
    private static final float PRESS_RESPONSE = 0.26f;
    private static final float PRESS_DAMPING = 0.58f;
    private static final float LIVE_SPEED = 12.0f;
    private static final float SWAP_SPEED = 14.0f;

    private final Consumer<UiButton> action;
    private final Spring hover = new Spring(HOVER_RESPONSE, HOVER_DAMPING, 0.0f);
    private final Spring press = new Spring(PRESS_RESPONSE, PRESS_DAMPING, 0.0f);
    private final Smooth live = new Smooth(1.0f, LIVE_SPEED);
    private boolean primed;
    private final Smooth swap = new Smooth(1.0f, SWAP_SPEED);
    private Component outgoing;
    private String shownLabel;
    private final RowAnchor anchor = new RowAnchor();
    private Component explanation;
    private int swatch;
    private boolean lit;
    private boolean muted;
    private boolean announced;
    private boolean held;

    public UiButton swatch(int argb) {
        swatch = argb;
        return this;
    }

    // WHY: выбранная строка гасит себя как выключенная, и в магазине именно её подпись читается хуже всех
    public UiButton lit() {
        lit = true;
        return this;
    }

    public UiButton hint(Component text) {
        explanation = text;
        return this;
    }

    public UiButton hint(String key) {
        return hint(MenuHint.of(key));
    }

    // WHY: пробник звуковой схемы играет образец чужой схемы, и свой щелчок кнопки лёг бы поверх него
    // WHY: экран пересобирает кнопки от любой правки данных, а нажатие, наведение и переход
    // WHY: доступности живут в самой кнопке: без передачи новая кнопка начинала бы с нуля
    public void adopt(UiButton older) {
        if (older == null || older == this) return;
        hover.take(older.hover);
        press.take(older.press);
        live.snap(older.live.get());
        primed = older.primed;
        swap.snap(older.swap.get());
        outgoing = older.outgoing;
        shownLabel = older.shownLabel;
        held = older.held;
    }

    public UiButton muted() {
        muted = true;
        return this;
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
        if (anchor.gone(getY(), this.height)) return;

        anchor.draw(graphics, getX(), this.width, getY(), this.height,
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

    public UiButton(int x, int y, int width, int height, Component message, Consumer<UiButton> action) {
        super(x, y, width, height, message);
        this.action = action;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        held = true;
        super.onClick(mouseX, mouseY);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        held = false;
        super.onRelease(mouseX, mouseY);
    }

    @Override
    public void onPress() {
        if (!muted) UiSound.press();
        action.accept(this);
    }

    @Override
    public void playDownSound(SoundManager sounds) {
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean focused = this.active && UiInput.pointed(this);
        if (focused != announced) {
            announced = focused;
            if (focused && !muted) UiSound.hover();
        }

        if (held && !UiInput.mouseDown()) held = false;
        if (this.visible && this.isHovered()) {
            MenuHint.offer(this, explanation, getY(), this.height, mouseX);
        }

        float delta = UiFrame.delta();
        if (!primed) {
            primed = true;
            live.snap(this.active ? 1.0f : 0.0f);
        }
        trackLabel();
        UiSkin.button(graphics, this.getX(), this.getY(), this.width, this.height, this.getMessage(),
                outgoing, swap.to(1.0f, delta), live.to(this.active ? 1.0f : 0.0f, delta),
                hover.to(focused ? 1.0f : 0.0f, delta), press.to(held ? 1.0f : 0.0f, delta),
                UiSkin.fit(graphics), swatch, lit);
    }

    private void trackLabel() {
        String now = this.getMessage().getString();
        if (shownLabel == null) {
            shownLabel = now;
            return;
        }
        if (now.equals(shownLabel)) return;

        String previous = shownLabel;
        shownLabel = now;
        if (swap.get() < 0.999f) {
            outgoing = null;
            swap.snap(1.0f);
            return;
        }
        outgoing = Component.literal(previous);
        swap.snap(0.0f);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
