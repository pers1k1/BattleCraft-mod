package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class ScreenDim {
    private static final int VEIL = 0x101010;
    private static final float TOP_ALPHA = 0.753f;
    private static final float BOTTOM_ALPHA = 0.816f;

    public interface Painter {
        void paint(GuiGraphics graphics, int width, int height);
    }

    private static Painter painter;

    private ScreenDim() {}

    public static void painter(Painter value) {
        painter = value;
    }

    public static void render(GuiGraphics graphics, int width, int height, float strength) {
        if (Minecraft.getInstance().level == null) {
            paintBackdrop(graphics, width, height);
            return;
        }

        float shown = UiAnim.clamp01(strength);
        if (shown <= 0.004f) return;

        UiRender.gradient(graphics, 0.0f, 0.0f, width, height,
                UiTheme.withAlpha(VEIL, TOP_ALPHA * shown),
                UiTheme.withAlpha(VEIL, BOTTOM_ALPHA * shown));
    }

    private static void paintBackdrop(GuiGraphics graphics, int width, int height) {
        if (painter != null) {
            painter.paint(graphics, width, height);
            return;
        }
        UiRender.rect(graphics, 0.0f, 0.0f, width, height, UiPalette.backdrop());
    }
}
