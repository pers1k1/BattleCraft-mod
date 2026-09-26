package com.persiki84.shared.client.menu.studio;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public interface StudioTile {
    float NO_METER = -1.0f;

    ItemStack icon();

    Component name();

    Component corner();

    Component plate();

    default float meter() {
        return NO_METER;
    }

    default int meterColor() {
        return 0;
    }

    default boolean faded() {
        return false;
    }

    default Component mark() {
        return Component.empty();
    }
}
