package com.persiki84.battlecraft.client.menu;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiInput;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.TabButton;

import java.util.Map;
import java.util.WeakHashMap;

// WHY: полоса вкладок ванильная: чёрная лента, шов и кнопки землёй по девяти кускам. Она рисуется
// WHY: своим виджетом, а не кнопкой, поэтому мимо перекраски WidgetRestyle проходила целиком
public final class ScreenTabs {
    private static final float BAR_HEIGHT = 24.0f;
    private static final float TAB_INSET = 2.0f;
    private static final float TAB_RADIUS = 6.0f;
    private static final float LABEL_SCALE = 0.9f;
    private static final float LABEL_TRACKING = 0.2f;
    private static final float UNDERLINE_HEIGHT = 1.4f;
    private static final float UNDERLINE_INSET = 6.0f;
    private static final float UNDERLINE_BOTTOM = 3.0f;
    private static final float HOVER_SPEED = 16.0f;
    private static final float PICK_SPEED = 13.0f;
    private static final float LIFT_IDLE = 0.16f;
    private static final float LIFT_FOCUS = 0.7f;
    private static final float BAR_ALPHA = 0.85f;

    private static final Map<TabButton, State> states = new WeakHashMap<>();

    private ScreenTabs() {}

    public static boolean dressed() {
        if ((HudConfig.plainScreens() & 1) != 0) return false;
        return WidgetRestyle.dressing();
    }

    public static void paintBar(GuiGraphics graphics, int width) {
        UiGlass.sunken(graphics, -TAB_RADIUS, -BAR_HEIGHT, width + TAB_RADIUS * 2.0f,
                BAR_HEIGHT * 2.0f, TAB_RADIUS, BAR_ALPHA);
    }

    public static void paintTab(GuiGraphics graphics, TabButton button) {
        State state = states.computeIfAbsent(button, key -> new State());
        float delta = UiFrame.delta();
        boolean pointed = button.active && UiInput.pointed(button);
        if (pointed != state.announced) {
            state.announced = pointed;
            if (pointed) UiSound.hover();
        }

        float focus = state.hover.to(pointed ? 1.0f : 0.0f, delta);
        float picked = state.picked.to(button.isSelected() ? 1.0f : 0.0f, delta);
        paintSurface(graphics, button, focus, picked);
        paintLabel(graphics, button, focus, picked);
    }

    private static void paintSurface(GuiGraphics graphics, TabButton button, float focus, float picked) {
        float x = button.getX() + TAB_INSET;
        float y = button.getY() + TAB_INSET;
        float width = button.getWidth() - TAB_INSET * 2.0f;
        float height = button.getHeight() - TAB_INSET;

        UiGlass.panel(graphics, x, y, width, height, TAB_RADIUS,
                Math.max(picked, focus * 0.75f), LIFT_IDLE + LIFT_FOCUS * Math.max(picked, focus));
        if (picked <= 0.01f) return;

        float lineWidth = (width - UNDERLINE_INSET * 2.0f) * picked;
        UiRender.panel(graphics, x + (width - lineWidth) / 2.0f, y + height - UNDERLINE_BOTTOM,
                lineWidth, UNDERLINE_HEIGHT, UNDERLINE_HEIGHT / 2.0f,
                UiTheme.alpha(UiAccent.color(), picked));
    }

    private static void paintLabel(GuiGraphics graphics, TabButton button, float focus, float picked) {
        int ink = UiTheme.mix(UiAccent.textDim(), UiAccent.text(), Math.max(picked, focus));
        float height = button.getHeight() - UNDERLINE_BOTTOM;
        UiRender.textTracked(graphics, Minecraft.getInstance().font, button.getMessage(),
                button.getX() + button.getWidth() / 2.0f,
                UiRender.centerY(button.getY(), height, LABEL_SCALE), LABEL_SCALE, LABEL_TRACKING, ink);
    }

    private static final class State {
        private final Smooth hover = new Smooth(0.0f, HOVER_SPEED);
        private final Smooth picked = new Smooth(0.0f, PICK_SPEED);
        private boolean announced;
    }
}
