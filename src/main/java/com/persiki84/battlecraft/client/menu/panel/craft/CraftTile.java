package com.persiki84.battlecraft.client.menu.panel.craft;

import com.persiki84.battlecraft.craft.CraftBan;
import com.persiki84.battlecraft.craft.CraftPhase;
import com.persiki84.shared.client.menu.studio.StudioTile;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

// WHY: запрет, который сейчас не действует (фаза другая), лежит на витрине погашенным: админ видит,
// WHY: что закрыто прямо сейчас, не сверяя фазу узла с тем, идёт ли матч. Ключ запрета собирается
// WHY: один раз: по нему ищут выбор, отметки и счёт рецептов, и строка на кадр была бы аллокацией
record CraftTile(CraftBan ban, String key, boolean active, ItemStack icon, Component name, Component corner,
                 Component plate, boolean faded) implements StudioTile {

    static CraftTile of(CraftBan ban, boolean matchRunning) {
        ItemStack stack = ban.stack();
        boolean active = ban.activeWhen(matchRunning);
        Component corner = ban.tag() == null ? Component.empty() : Component.translatable("studio.craft.variant");
        return new CraftTile(ban, ban.key(), active, stack, stack.getHoverName(), corner,
                stateOf(ban.phase(), active), !active);
    }

    static Component stateOf(CraftPhase phase, boolean active) {
        if (active) return Component.translatable("studio.craft.state.active");
        return Component.translatable(phase == CraftPhase.MATCH ? "studio.craft.state.wait_match"
                : "studio.craft.state.wait_lobby");
    }

    CraftPhase phase() {
        return ban.phase();
    }
}
