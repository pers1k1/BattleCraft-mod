package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

// WHY: предмет в свойствах показывается самим предметом, а не идентификатором строкой: владелец
// WHY: узнаёт награду по виду, и щелчок по ней открывает полку, где её же меняют
public class ItemRow extends MenuRow {
    private static final float VALUE_WIDTH = 116.0f;
    private static final float ICON = 16.0f;
    private static final float ICON_GAP = 4.0f;

    private final Supplier<ItemStack> stack;
    private final Supplier<Component> empty;
    private final Runnable action;

    public ItemRow(int x, int y, int width, int height, Component label, Supplier<ItemStack> stack,
                   Supplier<Component> empty, Runnable action) {
        super(x, y, width, height, label);
        this.stack = stack;
        this.empty = empty;
        this.action = action;
    }

    @Override
    protected void renderValue(GuiGraphics graphics, int mouseX, int mouseY, float focus) {
        ItemStack shown = stack.get();
        int color = UiTheme.mix(UiAccent.text(), UiTheme.WHITE, focus);
        float right = getX() + width - PAD;
        if (shown.isEmpty()) {
            UiRender.textTrackedBox(graphics, font(), empty.get(), right - VALUE_WIDTH, getY(), height, VALUE_WIDTH,
                    LABEL_SCALE, 0.0f, UiAccent.textDim(), false, 1.0f);
            return;
        }
        float textWidth = VALUE_WIDTH - ICON - ICON_GAP;
        UiRender.textTrackedBox(graphics, font(), shown.getHoverName(), right - textWidth, getY(), height, textWidth,
                LABEL_SCALE, 0.0f, color, false, 1.0f);
        paintIcon(graphics, shown, right - VALUE_WIDTH, getY() + (height - ICON) / 2.0f);
    }

    private static void paintIcon(GuiGraphics graphics, ItemStack shown, float x, float y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        try {
            graphics.renderItem(shown, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
        UiSound.press();
        flash();
        action.run();
    }
}
