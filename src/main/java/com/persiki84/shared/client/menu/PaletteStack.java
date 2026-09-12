package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiGlass;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class PaletteStack {
    private static final float CASCADE = 22.0f;
    private static final float EDGE = 10.0f;
    private static final int OFF_SCREEN = -1;

    private final List<PaletteWindow> windows = new ArrayList<>();
    private PaletteWindow grabbed;

    public boolean any() {
        return !windows.isEmpty();
    }

    public void toggle(float screenWidth, float screenHeight, String owner, Component title, int color,
                       IntConsumer apply, Runnable clear) {
        PaletteWindow open = live(owner);
        if (open != null) {
            open.beginClose();
            return;
        }

        PaletteWindow window = new PaletteWindow(owner, title, color, apply, clear);
        window.place(slotX(screenWidth), slotY(screenHeight));
        windows.add(window);
    }

    private float slotX(float screenWidth) {
        float base = (screenWidth - PaletteWindow.WIDTH) / 2.0f + liveCount() * CASCADE;
        return clamp(base, screenWidth - PaletteWindow.WIDTH);
    }

    private float slotY(float screenHeight) {
        float base = (screenHeight - PaletteWindow.HEIGHT) / 2.0f + liveCount() * CASCADE;
        return clamp(base, screenHeight - PaletteWindow.HEIGHT);
    }

    private static float clamp(float value, float limit) {
        return Math.max(EDGE, Math.min(value, Math.max(EDGE, limit - EDGE)));
    }

    private int liveCount() {
        int count = 0;
        for (PaletteWindow window : windows) {
            if (!window.closing()) count++;
        }
        return count;
    }

    private PaletteWindow live(String owner) {
        for (PaletteWindow window : windows) {
            if (!window.closing() && window.owner().equals(owner)) return window;
        }
        return null;
    }

    public void render(GuiGraphics graphics, float width, float height, int mouseX, int mouseY) {
        windows.removeIf(PaletteWindow::gone);
        if (windows.isEmpty()) return;

        UiGlass.layer(graphics);
        PaletteWindow hovered = topAt(mouseX, mouseY);
        for (PaletteWindow window : windows) {
            boolean lit = window == hovered;
            window.render(graphics, width, height, lit ? mouseX : OFF_SCREEN, lit ? mouseY : OFF_SCREEN);
        }
    }

    public boolean covering(double pointX, double pointY) {
        return topAt(pointX, pointY) != null;
    }

    private PaletteWindow topAt(double pointX, double pointY) {
        for (int index = windows.size() - 1; index >= 0; index--) {
            PaletteWindow window = windows.get(index);
            if (window.covers(pointX, pointY)) return window;
        }
        return null;
    }

    private PaletteWindow top() {
        for (int index = windows.size() - 1; index >= 0; index--) {
            PaletteWindow window = windows.get(index);
            if (!window.closing()) return window;
        }
        return null;
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        PaletteWindow window = topAt(mouseX, mouseY);
        if (window == null) return false;

        windows.remove(window);
        windows.add(window);
        window.mouseClicked(mouseX, mouseY);
        grabbed = window.dragging() ? window : null;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        return grabbed != null && grabbed.mouseDragged(mouseX, mouseY);
    }

    public boolean mouseReleased() {
        if (grabbed == null) return false;

        boolean handled = grabbed.mouseReleased();
        grabbed = null;
        return handled;
    }

    public boolean keyPressed(int key) {
        PaletteWindow window = top();
        return window != null && window.keyPressed(key);
    }

    public boolean charTyped(char symbol) {
        PaletteWindow window = top();
        return window != null && window.charTyped(symbol);
    }

    public void closeAll() {
        for (PaletteWindow window : windows) {
            window.beginClose();
        }
    }
}
