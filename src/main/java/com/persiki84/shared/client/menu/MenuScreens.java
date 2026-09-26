package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.menu.studio.StudioScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class MenuScreens {
    private static final Map<String, Supplier<Screen>> factories = new HashMap<>();

    private MenuScreens() {}

    public static void register(String menuId, Supplier<Screen> factory) {
        factories.put(menuId, factory);
    }

    public static void open(String menuId) {
        open(menuId, (Screen) null);
    }

    // WHY: команда умеет открыть экран на своей вкладке, и вкладка ставится до init: экран
    // WHY: строит содержимое один раз, а переключение после показа мигнуло бы первой вкладкой
    public static void open(String menuId, int tab) {
        Supplier<Screen> factory = factories.get(menuId);
        if (factory == null) return;

        MenuFeedback.clear();
        Screen screen = factory.get();
        if (tab >= 0 && screen instanceof PanelScreen panel) panel.openOn(tab);
        Minecraft.getInstance().setScreen(screen);
    }

    public static void open(String menuId, Screen parent) {
        Supplier<Screen> factory = factories.get(menuId);
        if (factory == null) return;

        MenuFeedback.clear();
        Screen screen = factory.get();
        if (parent != null && screen instanceof ManagerScreen manager) {
            manager.returnTo(parent);
        }
        if (parent != null && screen instanceof StudioScreen studio) {
            studio.returnTo(parent);
        }
        Minecraft.getInstance().setScreen(screen);
    }
}
