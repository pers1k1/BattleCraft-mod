package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.InterfaceFlag;
import com.persiki84.battlecraft.client.custom.PaletteKey;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.shared.client.menu.ToggleRow;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.List;

final class ColorSections {
    private ColorSections() {}

    static List<CustomSection> build(RowKit kit) {
        List<CustomSection> built = new ArrayList<>();
        built.add(ink(kit));
        for (PaletteKey.Group group : PaletteKey.Group.values()) {
            if (!group.onGlassTab() && !group.onInterfaceTab()) built.add(group(kit, group));
        }
        return built;
    }

    private static CustomSection ink(RowKit kit) {
        ToggleRow hudAccent = kit.toggle("battlecraft.custom.hud_accent", HudConfig::hudAccentText,
                HudConfig::hudAccentText);
        kit.hudAccent(hudAccent);
        List<AbstractWidget> rows = List.of(
                kit.toggle("battlecraft.custom.follow_launcher", Customization::followLauncher, value -> {
                    Customization.followLauncher(value);
                    Customization.save();
                }),
                kit.saved("battlecraft.custom.accent_ink", Customization::accentInk, Customization::accentInk),
                hudAccent);
        return CustomSection.of("colors.ink", CustomTab.COLORS, "battlecraft.custom.group.ink", false, rows,
                ColorSections::resetInk, PreviewFocus.TEXT, List.of());
    }

    private static void resetInk() {
        Customization.followLauncher(true);
        Customization.accentInk(false);
        InterfaceFlag.HUD_ACCENT_TEXT.reset();
    }

    private static CustomSection group(RowKit kit, PaletteKey.Group group) {
        List<AbstractWidget> rows = new ArrayList<>();
        List<PaletteKey> keys = keysOf(group);
        for (PaletteKey key : keys) {
            rows.add(kit.color(key));
        }
        return CustomSection.of("colors." + group.id(), CustomTab.COLORS, "battlecraft.custom.group." + group.id(),
                true, rows, () -> clear(keys), focusOf(group), keys);
    }

    static List<PaletteKey> keysOf(PaletteKey.Group group) {
        List<PaletteKey> keys = new ArrayList<>();
        for (PaletteKey key : PaletteKey.values()) {
            if (key.group() == group) keys.add(key);
        }
        return keys;
    }

    static void clear(List<PaletteKey> keys) {
        for (PaletteKey key : keys) {
            Customization.clearColor(key);
        }
    }

    private static PreviewFocus focusOf(PaletteKey.Group group) {
        return switch (group) {
            case WORLD -> PreviewFocus.WORLD;
            case DAMAGE -> PreviewFocus.DAMAGE;
            case SURFACE, GLASS -> PreviewFocus.GLASS;
            default -> PreviewFocus.TEXT;
        };
    }
}
