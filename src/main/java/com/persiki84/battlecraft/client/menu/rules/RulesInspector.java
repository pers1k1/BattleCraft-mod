package com.persiki84.battlecraft.client.menu.rules;

import com.persiki84.battlecraft.client.menu.panel.mode.KitTile;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.menu.studio.StudioTile;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

// WHY: строки создаются один раз и только получают новую плитку: в строке живёт анимация появления,
// WHY: и пересоздание на каждой раскладке проигрывало бы её заново с каждой буквой поиска
public final class RulesInspector {
    private static final int ROW = 22;

    private final HeadingRow heading = new HeadingRow(0, 0, 10, ROW, Component.empty());
    private final ActionRow count = reading("rules.match.kit.count", () -> this.countText);
    private final ActionRow when = reading("rules.match.craft.when", () -> this.whenText);
    private final ActionRow now = reading("rules.match.craft.now", () -> this.nowText);
    private StudioTile shownTile;
    private Component countText = Component.empty();
    private Component whenText = Component.empty();
    private Component nowText = Component.empty();

    public void build(StudioStack stack, int x, int width, StudioTile tile) {
        if (tile != shownTile) adopt(tile);
        stack.add(sized(heading, width), x);
        if (tile instanceof KitTile) {
            stack.add(sized(count, width), x);
        } else if (tile instanceof RuleBanTile) {
            stack.add(sized(when, width), x);
            stack.add(sized(now, width), x);
        }
    }

    private void adopt(StudioTile tile) {
        shownTile = tile;
        heading.setMessage(tile.name());
        if (tile instanceof KitTile kit) countText = Component.translatable("rules.match.kit.count.value", kit.count());
        if (tile instanceof RuleBanTile ban) {
            whenText = Component.translatable(ban.tile().phase().label());
            nowText = ban.plate();
        }
    }

    private static <T extends AbstractWidget> T sized(T row, int width) {
        row.setWidth(width);
        return row;
    }

    private static ActionRow reading(String key, Supplier<Component> value) {
        ActionRow row = new ActionRow(0, 0, 10, ROW, Component.translatable(key), value, () -> { });
        row.hint(key + ".hint");
        row.active = false;
        return row;
    }
}
