package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiField;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

// WHY: поле ввода строкой списка, а не отдельной плашкой над ним: только так оно едет вместе с
// WHY: прокруткой, попадает под поиск и раскладывается общим place()
public class FieldRow extends MenuRow {
    private static final float BOX_HEIGHT = 15.0f;
    private static final float SHARE = 0.44f;
    private static final float INSET = 5.0f;
    private static final float RIM = 1.0f;
    private static final float RIM_ALPHA = 0.6f;
    private static final float FOCUS_ALPHA = 0.22f;

    private final EditBox box;

    public FieldRow(int x, int y, int width, int height, Component label, Component hint,
                    String value, int limit, Consumer<String> apply) {
        super(x, y, width, height, label);
        this.box = new EditBox(Minecraft.getInstance().font, 0, 0, 0, (int) BOX_HEIGHT, label);
        this.box.setBordered(false);
        this.box.setMaxLength(limit);
        this.box.setValue(value);
        this.box.setTextColor(UiAccent.text());
        this.box.setResponder(apply);
        if (hint != null) this.box.setHint(hint);
        UiField.own(this.box);
    }

    public EditBox box() {
        return box;
    }

    @Override
    public boolean capturing() {
        return box.isFocused();
    }

    public String value() {
        return box.getValue();
    }

    @Override
    protected void renderValue(GuiGraphics graphics, int mouseX, int mouseY, float focus) {
        float plateX = plateLeft();
        float plateWidth = getX() + width - PAD - plateX;
        float plateY = getY() + (height - BOX_HEIGHT) / 2.0f;

        box.setX((int) (plateX + INSET));
        box.setY((int) plateY);
        box.setWidth((int) (plateWidth - INSET * 2.0f));

        paintPlate(graphics, plateX, plateY, plateWidth);
        box.render(graphics, mouseX, mouseY, 0.0f);
    }

    private void paintPlate(GuiGraphics graphics, float x, float y, float width) {
        float lit = UiField.focus(box);
        float radius = UiMetrics.radius(BOX_HEIGHT);

        UiGlass.sunken(graphics, x, y, width, BOX_HEIGHT, radius, valueAlpha());
        if (lit <= 0.01f) return;

        UiGlass.inner(graphics, x, y, width, BOX_HEIGHT, radius, FOCUS_ALPHA * lit, 0.4f);
        UiRender.rim(graphics, x, y, width, BOX_HEIGHT, radius, RIM,
                UiTheme.alpha(UiAccent.color(), RIM_ALPHA * lit));
    }

    private float plateLeft() {
        return getX() + width * SHARE;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
        box.setFocused(true);
        box.mouseClicked(mouseX, mouseY, 0);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        box.setFocused(focused);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        return box.isFocused() && box.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char typed, int modifiers) {
        return box.isFocused() && box.charTyped(typed, modifiers);
    }
}
