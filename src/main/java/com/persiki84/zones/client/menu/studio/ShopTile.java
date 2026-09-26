package com.persiki84.zones.client.menu.studio;

import com.persiki84.shared.client.menu.studio.StudioTile;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.zones.shop.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public record ShopTile(ShopEntry entry, Component name, Component corner, Component plate, Component mark,
                       boolean faded) implements StudioTile {

    public static ShopTile of(ShopEntry entry, String viewedTeam) {
        boolean hidden = viewedTeam != null && !entry.access().visibleTo(viewedTeam);
        return new ShopTile(entry, entry.stack().getHoverName(), corner(entry),
                Component.translatable("studio.shop.price", entry.price()), mark(entry, hidden), hidden);
    }

    private static Component corner(ShopEntry entry) {
        if (entry.limited()) {
            return Component.translatable("studio.shop.left", Math.max(0, entry.available()) * entry.bundle());
        }
        return entry.bundle() > 1 ? Component.literal("x" + entry.bundle()) : Component.empty();
    }

    private static Component mark(ShopEntry entry, boolean hidden) {
        if (hidden) return Component.translatable("studio.shop.hidden_mark");
        if (!entry.access().everyone()) return Component.translatable("studio.shop.teams_mark");
        return Component.empty();
    }

    @Override
    public ItemStack icon() {
        return entry.stack();
    }

    @Override
    public float meter() {
        if (!entry.limited() || entry.stock() <= 0) return NO_METER;
        return Math.max(0, entry.available()) / (float) entry.stock();
    }

    @Override
    public int meterColor() {
        return entry.soldOut() ? UiPalette.alert() : UiAccent.color();
    }
}
