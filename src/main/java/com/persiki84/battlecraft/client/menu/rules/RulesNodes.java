package com.persiki84.battlecraft.client.menu.rules;

import com.persiki84.battlecraft.client.menu.panel.craft.CraftView;
import com.persiki84.battlecraft.client.menu.panel.mode.ModeView;
import com.persiki84.battlecraft.craft.CraftPhase;
import com.persiki84.battlecraft.mode.ModeId;
import com.persiki84.shared.client.menu.studio.StudioNav;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RulesNodes {
    private static final String HEADING = "heading:";
    private static final String RULES = "rules:";

    private RulesNodes() {}

    public static List<StudioNav.Node> build(ModeView modes, CraftView crafts, Map<String, RulesGameRows> rules) {
        List<StudioNav.Node> built = new ArrayList<>();
        built.add(StudioNav.Node.heading(HEADING + "modes", Component.translatable("rules.match.group.modes")));
        for (ModeId id : ModeId.values()) {
            built.add(StudioNav.Node.item("mode:" + id.id(), Component.translatable(id.translationKey()),
                    badge(modes, id), 0, false));
        }
        built.add(StudioNav.Node.heading(HEADING + "craft", Component.translatable("rules.match.group.craft")));
        for (CraftPhase phase : CraftPhase.values()) {
            built.add(StudioNav.Node.item("craft:" + phase.id(), Component.translatable("studio.craft.node." + phase.id()),
                    Component.literal(String.valueOf(crafts.tiles(phase).size())), 0, false));
        }
        built.add(StudioNav.Node.heading(HEADING + "rules", Component.translatable("rules.match.group.rules")));
        for (Map.Entry<String, RulesGameRows> entry : rules.entrySet()) {
            String group = entry.getKey().substring(RULES.length());
            built.add(StudioNav.Node.item(entry.getKey(), Component.translatable("battlecraft.rules.tab." + group),
                    Component.literal(String.valueOf(entry.getValue().size())), 0, false));
        }
        return List.copyOf(built);
    }

    // WHY: заголовок группы тоже узел навигации, и щелчок по нему не должен сбрасывать открытый раздел
    public static boolean selectable(String key) {
        return !key.startsWith(HEADING);
    }

    public static Component stats(ModeView modes, ModeId current) {
        boolean running = ModeId.byId(modes.running()) != null;
        String state = running ? "running" : modes.locked() ? "locked" : "default";
        return Component.translatable("rules.match.stats", Component.translatable(current.translationKey()),
                Component.translatable("rules.match.state." + state));
    }

    private static Component badge(ModeView modes, ModeId id) {
        if (id == ModeId.byId(modes.running())) return Component.translatable("rules.match.badge.running");
        if (modes.running().isEmpty() && id == modes.active()) return Component.translatable("rules.match.badge.default");
        return Component.empty();
    }
}
