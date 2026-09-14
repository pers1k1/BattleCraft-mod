package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Spring;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class PickRow extends MenuRow {
    private static final float ARROW_BOX = 15.0f;
    private static final float VALUE_WIDTH = 86.0f;
    private static final float ARROW_SIZE = 4.6f;

    private static final float ICON_SIZE = 16.0f;
    private static final float ICON_GAP = 3.0f;

    private static final float ARROW_HOVER_RESPONSE = 0.26f;
    private static final float ARROW_HOVER_DAMPING = 0.85f;
    private static final float ARROW_PRESS_RESPONSE = 0.22f;
    private static final float ARROW_PRESS_DAMPING = 0.52f;
    private static final float ARROW_PRESS_SHRINK = 0.16f;
    private static final float ARROW_HOVER_GROW = 0.08f;
    private static final float ARROW_NUDGE = 2.1f;
    private static final float ARROW_LIFT = 0.7f;
    private static final float ARROW_GLOW = 0.55f;
    private static final long ARROW_HOLD_MS = 90L;

    private static final float SWAP_SPEED = 15.0f;
    private static final float SWAP_TRAVEL = 9.0f;
    private static final float SWAP_DONE = 0.995f;

    private final List<Component> options;
    private final IntSupplier index;
    private final IntConsumer apply;
    private final Pending pending = new Pending();
    private final Spring backHover = new Spring(ARROW_HOVER_RESPONSE, ARROW_HOVER_DAMPING, 0.0f);
    private final Spring nextHover = new Spring(ARROW_HOVER_RESPONSE, ARROW_HOVER_DAMPING, 0.0f);
    private final Spring backPress = new Spring(ARROW_PRESS_RESPONSE, ARROW_PRESS_DAMPING, 0.0f);
    private final Spring nextPress = new Spring(ARROW_PRESS_RESPONSE, ARROW_PRESS_DAMPING, 0.0f);
    private final Smooth swap = new Smooth(1.0f, SWAP_SPEED);
    private Supplier<ItemStack> icon;
    private Component fallbackHint;
    private Component describedOption;
    private Component described;
    private Component leaving;
    private int swapDirection;
    private long backHeldUntil;
    private long nextHeldUntil;

    public PickRow icon(Supplier<ItemStack> source) {
        this.icon = source;
        return this;
    }

    public PickRow(int x, int y, int width, int height, Component label,
                   List<Component> options, IntSupplier index, IntConsumer apply) {
        super(x, y, width, height, label);
        this.options = options;
        this.index = index;
        this.apply = apply;
        hint(this::describe);
    }

    @Override
    public void adopt(MenuRow previous) {
        if (!(previous instanceof PickRow older)) return;

        swap.snap(older.swap.get());
        swapDirection = older.swapDirection;
        leaving = older.leaving;
        pending.take(older.pending);
    }

    // WHY: описание принадлежит выбранному значению, а не строке: у способа захвата каждый режим
    // WHY: объясняется своими словами, и общая подсказка на строке ничего из этого не говорит
    @Override
    public MenuRow hint(String key) {
        fallbackHint = MenuHint.of(key);
        describedOption = null;
        described = null;
        return hint(this::describe);
    }

    // WHY: подсказка держится за выбранным значением, а не собирается каждый кадр: перевод
    // WHY: и Component на кадр это аллокация в отрисовке, а новая ссылка ещё и рушит промер окна
    private Component describe() {
        Component option = current();
        if (option == describedOption) return described;

        describedOption = option;
        described = fallbackHint;
        if (option.getContents() instanceof TranslatableContents contents) {
            Component own = MenuHint.of(contents.getKey() + MenuHint.SUFFIX);
            if (own != null) described = own;
        }
        return described;
    }

    @Override
    protected void renderValue(GuiGraphics graphics, int mouseX, int mouseY, float focus) {
        float delta = UiFrame.delta();
        float top = getY() + (height - ARROW_BOX) / 2.0f;
        arrow(graphics, backLeft(), top, false, backHover, backPress,
                hovering(mouseX, mouseY, backLeft(), top), held(backHeldUntil), delta);
        arrow(graphics, nextLeft(), top, true, nextHover, nextPress,
                hovering(mouseX, mouseY, nextLeft(), top), held(nextHeldUntil), delta);

        float shown = renderIcon(graphics);
        float centerX = (backLeft() + ARROW_BOX + nextLeft() + shown) / 2.0f;
        float room = VALUE_WIDTH - shown;
        float turned = UiAnim.easeOut(swap.to(1.0f, delta));

        UiRender.clip(graphics, backLeft() + ARROW_BOX, getY(), nextLeft() - backLeft() - ARROW_BOX, height);
        try {
            paintValue(graphics, centerX, room, turned);
        } finally {
            graphics.flush();
            graphics.disableScissor();
        }
    }

    private void paintValue(GuiGraphics graphics, float centerX, float room, float turned) {
        int tint = this.active ? UiAccent.text() : UiAccent.textFaint();
        if (leaving != null && turned < SWAP_DONE) {
            value(graphics, leaving, centerX - swapDirection * SWAP_TRAVEL * turned, room,
                    UiTheme.alpha(tint, 1.0f - turned));
        } else {
            leaving = null;
        }
        value(graphics, current(), centerX + swapDirection * SWAP_TRAVEL * (1.0f - turned), room,
                UiTheme.alpha(tint, turned));
    }

    private void value(GuiGraphics graphics, Component text, float centerX, float room, int tint) {
        UiRender.textTrackedFit(graphics, font(), text, centerX, getY(), height, room,
                LABEL_SCALE, 0.0f, tint, false);
    }

    private float renderIcon(GuiGraphics graphics) {
        if (icon == null) return 0.0f;

        ItemStack stack = icon.get();
        if (stack.isEmpty()) return 0.0f;

        graphics.renderItem(stack, (int) (backLeft() + ARROW_BOX + ICON_GAP),
                (int) (getY() + (height - ICON_SIZE) / 2.0f));
        return ICON_SIZE + ICON_GAP;
    }

    private Component current() {
        if (options.isEmpty()) return Component.empty();
        return options.get(Math.floorMod(chosen(), options.size()));
    }

    private int chosen() {
        return pending.resolve(index.getAsInt());
    }

    private boolean hovering(int mouseX, int mouseY, float x, float y) {
        return mouseX >= x && mouseX <= x + ARROW_BOX && mouseY >= y && mouseY <= y + ARROW_BOX;
    }

    private static boolean held(long until) {
        return System.currentTimeMillis() < until;
    }

    // WHY: стрелка отвечает на щелчок сама, а не ждёт отклика строки: без своего сжатия и толчка
    // WHY: в сторону хода нажатие выглядит так, будто ничего не произошло
    private void arrow(GuiGraphics graphics, float x, float y, boolean forward, Spring hover, Spring press,
                       boolean hovered, boolean pushed, float delta) {
        float lit = hover.to(hovered ? 1.0f : 0.0f, delta);
        float sunk = Math.max(0.0f, press.to(pushed ? 1.0f : 0.0f, delta));
        float scale = 1.0f + ARROW_HOVER_GROW * lit - ARROW_PRESS_SHRINK * sunk;
        float nudge = (forward ? 1.0f : -1.0f) * ARROW_NUDGE * sunk;

        float centerX = x + ARROW_BOX / 2.0f;
        float centerY = y + ARROW_BOX / 2.0f;

        graphics.pose().pushPose();
        graphics.pose().translate(centerX + nudge, centerY, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-centerX, -centerY, 0.0f);
        try {
            paintArrow(graphics, x, y, forward, lit, sunk, centerX, centerY);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void paintArrow(GuiGraphics graphics, float x, float y, boolean forward, float lit, float sunk,
                            float centerX, float centerY) {
        UiGlass.panel(graphics, x, y, ARROW_BOX, ARROW_BOX, ARROW_BOX * 0.32f, 1.0f,
                0.2f + ARROW_LIFT * lit - ARROW_LIFT * sunk * 0.5f);

        float tipX = forward ? centerX + ARROW_SIZE * 0.6f : centerX - ARROW_SIZE * 0.6f;
        float baseX = forward ? centerX - ARROW_SIZE * 0.4f : centerX + ARROW_SIZE * 0.4f;
        int base = this.active ? UiAccent.text() : UiAccent.textFaint();
        int tint = this.active ? UiTheme.mix(base, UiAccent.color(), ARROW_GLOW * lit) : base;

        UiRender.triangle(graphics, tipX, centerY, baseX, centerY - ARROW_SIZE, baseX, centerY + ARROW_SIZE, tint);
    }

    private float nextLeft() {
        return getX() + width - PAD - ARROW_BOX;
    }

    private float backLeft() {
        return nextLeft() - VALUE_WIDTH - ARROW_BOX;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
        if (options.isEmpty()) return;

        float top = getY() + (height - ARROW_BOX) / 2.0f;
        if (mouseY < top || mouseY > top + ARROW_BOX) return;

        if (mouseX >= nextLeft() && mouseX <= nextLeft() + ARROW_BOX) {
            nextHeldUntil = System.currentTimeMillis() + ARROW_HOLD_MS;
            move(1);
        } else if (mouseX >= backLeft() && mouseX <= backLeft() + ARROW_BOX) {
            backHeldUntil = System.currentTimeMillis() + ARROW_HOLD_MS;
            move(-1);
        }
    }

    private void move(int delta) {
        UiSound.press();
        flash();

        leaving = current();
        swapDirection = delta;
        swap.snap(0.0f);

        int picked = Math.floorMod(chosen() + delta, options.size());
        pending.want(picked);
        apply.accept(picked);
    }
}
