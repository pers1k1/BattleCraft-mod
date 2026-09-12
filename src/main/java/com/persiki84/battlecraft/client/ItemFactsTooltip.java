package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.shared.ItemFacts;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT)
public final class ItemFactsTooltip {

    private ItemFactsTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!HudConfig.itemFacts()) return;

        List<Component> facts = ItemFacts.describe(event.getItemStack());
        if (facts.isEmpty()) return;

        event.getToolTip().addAll(facts);
    }
}
