package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// WHY: сбросы целой вкладки живут в двух местах: строками в конце обзора вкладки и пунктами меню по
// WHY: правой кнопке на её узле. Действие одно, поэтому и собирается оно в одном месте
final class TabResets {
    private TabResets() {}

    static Map<CustomTab, List<AbstractWidget>> build(RowKit kit) {
        Map<CustomTab, List<AbstractWidget>> built = new EnumMap<>(CustomTab.class);
        built.put(CustomTab.GLASS, List.of(
                kit.action("battlecraft.custom.reset.glass", "battlecraft.custom.reset",
                        () -> done(kit, Customization::resetGlass))));
        built.put(CustomTab.COLORS, List.of(
                kit.action("battlecraft.custom.follow_theme", "battlecraft.custom.apply",
                        () -> done(kit, Customization::followTheme)),
                kit.action("battlecraft.custom.reset.colors", "battlecraft.custom.reset",
                        () -> done(kit, Customization::resetColors)).alerting()));
        built.put(CustomTab.INTERFACE, List.of(
                kit.action("battlecraft.custom.reset.hud", "battlecraft.custom.reset",
                        () -> done(kit, Customization::resetHud))));
        return built;
    }

    static List<StudioMenu.Action> actions(CustomTab tab, RowKit kit) {
        return switch (tab) {
            case GLASS -> List.of(StudioMenu.Action.careful("battlecraft.custom.reset.glass",
                    () -> done(kit, Customization::resetGlass)));
            case COLORS -> List.of(
                    StudioMenu.Action.of("battlecraft.custom.follow_theme", () -> done(kit, Customization::followTheme)),
                    StudioMenu.Action.careful("battlecraft.custom.reset.colors",
                            () -> done(kit, Customization::resetColors)));
            case INTERFACE -> List.of(StudioMenu.Action.careful("battlecraft.custom.reset.hud",
                    () -> done(kit, Customization::resetHud)));
            case PRESETS -> List.of(StudioMenu.Action.careful(CustomCatalog.RESET_ALL,
                    () -> done(kit, Customization::resetAll)));
        };
    }

    static void done(RowKit kit, Runnable reset) {
        reset.run();
        Customization.save();
        kit.host().refresh();
    }
}
