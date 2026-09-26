package com.persiki84.battlecraft.client.menu.panel.loot;

import com.persiki84.airdrop.loot.LootEntry;
import com.persiki84.airdrop.loot.LootTable;
import com.persiki84.shared.client.menu.studio.StudioTile;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record LootTile(LootEntry entry, ItemStack icon, Component name, Component corner, Component plate,
                       float meter) implements StudioTile {

    public static List<LootTile> of(LootTable table) {
        List<LootTile> tiles = new ArrayList<>();
        for (int index = 0; index < table.entries().size(); index++) {
            tiles.add(of(table, index));
        }
        return tiles;
    }

    private static LootTile of(LootTable table, int index) {
        LootEntry entry = table.entries().get(index);
        ItemStack stack = LootSnapshot.stack(entry);
        Component name = stack.isEmpty() ? Component.literal(entry.itemId().toString()) : stack.getHoverName();
        String count = entry.min() == entry.max() ? "x" + entry.min() : entry.min() + "-" + entry.max();
        Component plate = Component.literal(LootChance.shown(entry.chance() * 100.0f) + " %");
        return new LootTile(entry, stack, name, Component.literal(count), plate, LootOdds.appears(table, index));
    }

    @Override
    public int meterColor() {
        if (entry.guaranteed()) return UiAccent.color();
        return UiTheme.mix(UiPalette.alert(), UiAccent.color(), (float) LootChance.toSlider(entry.chance() * 100.0f));
    }

    @Override
    public boolean faded() {
        return icon.isEmpty();
    }

    @Override
    public Component mark() {
        return icon.isEmpty() ? Component.translatable("studio.loot.unknown_mark") : Component.empty();
    }
}
