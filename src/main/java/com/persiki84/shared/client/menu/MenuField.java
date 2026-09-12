package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiField;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class MenuField {
    public static final int LABEL_HEIGHT = 9;
    public static final int LABEL_GAP = 5;
    public static final int FIELD_HEIGHT = 16;
    public static final int BLOCK_HEIGHT = LABEL_HEIGHT + LABEL_GAP + FIELD_HEIGHT + 4;
    public static final int ROW_EQUIVALENT = 2;

    private static final int INSET = 4;
    private static final float LABEL_SCALE = 0.85f;
    private static final float LABEL_TRACKING = 0.3f;
    private static final float PLATE_LIFT = 3.0f;
    private static final float FOCUS_ALPHA = 0.22f;
    private static final float RIM = 1.0f;
    private static final float RIM_ALPHA = 0.55f;

    private final Component label;
    private final EditBox box;

    public MenuField(int x, int y, int width, Component label, String value, Consumer<String> apply) {
        this.label = label;
        this.box = new EditBox(Minecraft.getInstance().font, x + INSET, y + LABEL_HEIGHT + LABEL_GAP,
                width - INSET * 2, FIELD_HEIGHT, label);
        // WHY: свой отступ у поля уже есть в плашке, а ванильный бордер добавлял бы к нему ещё
        // WHY: четыре пикселя, и строка вставала правее подписи над ней
        this.box.setBordered(false);
        this.box.setValue(value);
        this.box.setResponder(apply);
    }

    public EditBox box() {
        return box;
    }

    public void render(GuiGraphics graphics) {
        float focus = UiField.focus(box);
        UiRender.textTrackedLeft(graphics, Minecraft.getInstance().font, label, box.getX(),
                box.getY() - LABEL_GAP - LABEL_HEIGHT + 1.0f, LABEL_SCALE, LABEL_TRACKING,
                UiTheme.mix(UiAccent.textDim(), UiAccent.color(), focus));

        float x = box.getX() - INSET;
        float y = box.getY() - PLATE_LIFT;
        float width = box.getWidth() + INSET * 2;
        float height = FIELD_HEIGHT + PLATE_LIFT * 2;
        float radius = UiMetrics.radius(FIELD_HEIGHT);
        UiGlass.sunken(graphics, x, y, width, height, radius, 1.0f);
        if (focus <= 0.01f) return;

        UiGlass.inner(graphics, x, y, width, height, radius, FOCUS_ALPHA * focus, 0.4f);
        UiRender.rim(graphics, x, y, width, height, radius, RIM,
                UiTheme.alpha(UiAccent.color(), RIM_ALPHA * focus));
    }
}
