package com.persiki84.battlecraft.client.menu.rules;

import com.persiki84.battlecraft.client.menu.panel.craft.CraftTile;
import com.persiki84.shared.client.menu.studio.StudioTile;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

// WHY: общая плитка оператора не знает, видел ли запрет этот игрок, поэтому метка «новое» живёт в обёртке
public record RuleBanTile(CraftTile tile, Component mark) implements StudioTile {
    @Override
    public ItemStack icon() {
        return tile.icon();
    }

    @Override
    public Component name() {
        return tile.name();
    }

    @Override
    public Component corner() {
        return tile.corner();
    }

    @Override
    public Component plate() {
        return tile.plate();
    }

    @Override
    public boolean faded() {
        return tile.faded();
    }
}
