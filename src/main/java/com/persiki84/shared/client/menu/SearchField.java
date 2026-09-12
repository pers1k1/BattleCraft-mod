package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiField;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

// WHY: поиск живёт в шапке экрана, а не строкой списка: строка уезжает с прокруткой, и набранное
// WHY: пропадало бы из виду ровно тогда, когда игрок смотрит на найденное
public final class SearchField {
    public static final int HEIGHT = 16;

    private static final int LIMIT = 48;
    private static final float ICON_BOX = 13.0f;
    private static final float ICON_RADIUS = 3.1f;
    private static final float ICON_THICKNESS = 1.1f;
    private static final float ICON_TAIL = 2.6f;
    private static final float RIM = 1.0f;
    private static final float RIM_ALPHA = 0.6f;
    private static final float FOCUS_ALPHA = 0.22f;
    private static final float BORN_SCALE = 0.93f;
    private static final float APPEAR_SPEED = 15.0f;
    private static final float RISE = 4.0f;
    private static final float ICON_GLOW = 0.7f;

    private final EditBox box;
    private final Smooth appear = new Smooth(0.0f, APPEAR_SPEED);

    public SearchField(Component hint, Consumer<String> apply) {
        Minecraft minecraft = Minecraft.getInstance();
        this.box = new EditBox(minecraft.font, 0, 0, 0, HEIGHT, hint);
        this.box.setBordered(false);
        this.box.setMaxLength(LIMIT);
        this.box.setHint(hint);
        this.box.setTextColor(UiAccent.text());
        this.box.setResponder(apply);
        UiField.own(this.box);
    }

    public EditBox box() {
        return box;
    }

    public String query() {
        return box.getValue();
    }

    public boolean typing() {
        return box.isFocused();
    }

    public void clear() {
        box.setValue("");
    }

    public void place(int x, int y, int width) {
        box.setX(x + (int) (ICON_BOX + UiMetrics.PAD));
        box.setY(y);
        box.setWidth(Math.max(1, width - (int) (ICON_BOX + UiMetrics.PAD * 2.0f)));
    }

    public void render(GuiGraphics graphics) {
        float grown = UiAnim.easeOut(appear.to(1.0f, UiFrame.delta()));
        if (grown <= 0.004f) return;

        float x = box.getX() - ICON_BOX - UiMetrics.PAD;
        float width = box.getWidth() + ICON_BOX + UiMetrics.PAD * 2.0f;
        float centerX = x + width / 2.0f;
        float bottom = box.getY() + HEIGHT;

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, bottom + (1.0f - grown) * RISE, 0.0f);
        graphics.pose().scale(scale(grown), scale(grown), 1.0f);
        graphics.pose().translate(-centerX, -bottom, 0.0f);
        try {
            paint(graphics, x, box.getY(), width, grown);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static float scale(float grown) {
        return BORN_SCALE + (1.0f - BORN_SCALE) * grown;
    }

    private void paint(GuiGraphics graphics, float x, float y, float width, float grown) {
        float focus = UiField.focus(box);
        float radius = UiMetrics.radius(HEIGHT);

        UiGlass.sunken(graphics, x, y, width, HEIGHT, radius, grown);
        if (focus > 0.01f) {
            UiGlass.inner(graphics, x, y, width, HEIGHT, radius, FOCUS_ALPHA * focus * grown, 0.4f);
            UiRender.rim(graphics, x, y, width, HEIGHT, radius, RIM,
                    UiTheme.alpha(UiAccent.color(), RIM_ALPHA * focus * grown));
        }
        paintIcon(graphics, x + UiMetrics.PAD + ICON_BOX / 2.0f, y + HEIGHT / 2.0f, focus, grown);
    }

    private void paintIcon(GuiGraphics graphics, float centerX, float centerY, float focus, float grown) {
        int tint = UiTheme.alpha(UiTheme.mix(UiAccent.textDim(), UiAccent.color(), ICON_GLOW * focus), grown);
        float lensX = centerX - ICON_RADIUS * 0.28f;
        float lensY = centerY - ICON_RADIUS * 0.28f;

        UiRender.ring(graphics, lensX, lensY, ICON_RADIUS, ICON_THICKNESS, 1.0f, tint);
        UiRender.line(graphics, lensX + ICON_RADIUS * 0.7f, lensY + ICON_RADIUS * 0.7f,
                lensX + ICON_RADIUS * 0.7f + ICON_TAIL, lensY + ICON_RADIUS * 0.7f + ICON_TAIL,
                ICON_THICKNESS, tint);
    }
}
