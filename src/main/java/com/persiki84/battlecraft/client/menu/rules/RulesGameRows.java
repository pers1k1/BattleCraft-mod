package com.persiki84.battlecraft.client.menu.rules;

import com.persiki84.battlecraft.client.ClientGameRules;
import com.persiki84.battlecraft.rules.GameRule;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// WHY: игроку правила только показываются: строка погашена и не жмётся, а значение читается из маски,
// WHY: которую сервер уже прислал при входе, поэтому меню для них не нужно
public final class RulesGameRows {
    private static final int ROW = 22;
    private static final Component ON = Component.translatable("rules.match.rule.on");
    private static final Component OFF = Component.translatable("rules.match.rule.off");

    private final List<ActionRow> rows = new ArrayList<>();
    private final List<String> names = new ArrayList<>();

    public RulesGameRows(List<GameRule> group) {
        for (GameRule rule : group) {
            Component label = Component.translatable(rule.label());
            ActionRow row = new ActionRow(0, 0, 10, ROW, label, () -> ClientGameRules.allows(rule) ? ON : OFF, () -> { });
            row.hint(rule.hint());
            row.active = false;
            rows.add(row);
            names.add(label.getString().toLowerCase(Locale.ROOT));
        }
    }

    public int size() {
        return rows.size();
    }

    public void build(StudioStack stack, int x, int width, String query) {
        for (int index = 0; index < rows.size(); index++) {
            if (!query.isEmpty() && !names.get(index).contains(query)) continue;
            ActionRow row = rows.get(index);
            row.setWidth(width);
            stack.add(row, x);
        }
    }
}
