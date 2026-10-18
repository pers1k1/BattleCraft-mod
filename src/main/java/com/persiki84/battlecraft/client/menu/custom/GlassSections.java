package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.GlassKey;
import com.persiki84.battlecraft.client.custom.PaletteKey;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class GlassSections {
    private GlassSections() {}

    static List<CustomSection> build(RowKit kit) {
        List<CustomSection> built = new ArrayList<>();
        built.add(liquid(kit));
        for (GlassKey.Group group : GlassKey.Group.values()) {
            if (group != GlassKey.Group.TEXT) built.add(group(kit, group));
        }
        return built;
    }

    private static CustomSection liquid(RowKit kit) {
        AbstractWidget row = kit.saved("battlecraft.custom.liquid", Customization::liquid, Customization::liquid)
                .note(Component.translatable("battlecraft.custom.liquid.credit"));
        return CustomSection.of("glass.liquid", CustomTab.GLASS, "battlecraft.custom.liquid", false, List.of(row),
                () -> Customization.liquid(Customization.preset().liquid()), PreviewFocus.GLASS, List.of());
    }

    private static CustomSection group(RowKit kit, GlassKey.Group group) {
        List<AbstractWidget> rows = new ArrayList<>();
        boolean material = group == GlassKey.Group.MATERIAL;
        if (material) {
            rows.add(kit.saved("battlecraft.custom.glass_theme", Customization::glassFollowsTheme,
                    Customization::glassFollowsTheme));
            rows.add(kit.color(PaletteKey.GLASS));
        }
        rows.addAll(knobs(kit, group));
        Runnable reset = material ? () -> resetMaterial(group) : () -> resetGroup(group);
        PreviewFocus focus = group == GlassKey.Group.DEPTH ? PreviewFocus.DEPTH : PreviewFocus.GLASS;
        return CustomSection.of("glass." + group.id().toLowerCase(Locale.ROOT), CustomTab.GLASS,
                "battlecraft.custom.group." + group.id(), true, rows, reset, focus,
                material ? List.of(PaletteKey.GLASS) : List.of());
    }

    static List<AbstractWidget> knobs(RowKit kit, GlassKey.Group group) {
        List<AbstractWidget> built = new ArrayList<>();
        for (GlassKey key : GlassKey.values()) {
            if (key.group() == group && key != GlassKey.HUD_SCALE) built.add(kit.glass(key));
        }
        return built;
    }

    private static void resetMaterial(GlassKey.Group group) {
        Customization.clearColor(PaletteKey.GLASS);
        Customization.glassFollowsTheme(false);
        resetGroup(group);
    }

    // WHY: ручки группы встают на значения выбранного пресета, а не на заводские константы: сброс
    // WHY: группы обязан давать то же, что сброс всего стекла, только в пределах группы
    static void resetGroup(GlassKey.Group group) {
        Map<GlassKey, Float> tuning = Customization.preset().tuning();
        for (GlassKey key : GlassKey.values()) {
            if (key.group() != group || key == GlassKey.HUD_SCALE) continue;
            Float tuned = tuning.get(key);
            Customization.glass(key, tuned == null ? key.fallback() : tuned);
        }
    }
}
