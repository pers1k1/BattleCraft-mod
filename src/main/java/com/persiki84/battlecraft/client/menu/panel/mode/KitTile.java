package com.persiki84.battlecraft.client.menu.panel.mode;

import com.persiki84.battlecraft.mode.KitEntry;
import com.persiki84.shared.client.menu.studio.StudioTile;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// WHY: предмет, которого нет в сборке (мод сняли), остаётся в наборе погашенной плиткой с меткой:
// WHY: сервер его при выдаче пропустит, а молча выкинуть запись значило бы потерять настройку
public record KitTile(int index, KitEntry entry, String key, ItemStack icon, Component name, Component corner,
               Component plate, boolean faded) implements StudioTile {

    static KitTile of(int index, KitEntry entry) {
        ItemStack stack = entry.stack();
        boolean missing = stack.isEmpty();
        ItemStack icon = missing ? new ItemStack(Items.BARRIER) : stack;
        Component name = missing ? Component.literal(entry.itemId()) : stack.getHoverName();
        Component plate = missing ? Component.translatable("studio.modes.kit.missing")
                : Component.translatable("studio.modes.kit.plate", entry.count());
        return new KitTile(index, entry, index + ":" + entry.itemId(), icon, name,
                Component.literal("x" + entry.count()), plate, missing);
    }

    public int count() {
        return entry.count();
    }
}
