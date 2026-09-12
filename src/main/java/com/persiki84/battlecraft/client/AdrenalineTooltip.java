package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.combat.Adrenaline;
import com.persiki84.battlecraft.combat.AdrenalineDose;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT)
public final class AdrenalineTooltip {
    private static final String STAMINA = "battlecraft.adrenaline.stamina";
    private static final String RELIEF = "battlecraft.adrenaline.relief";
    private static final String HEART = "battlecraft.adrenaline.heart";
    private static final String OVERDOSE = "battlecraft.adrenaline.overdose";

    private AdrenalineTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        AdrenalineDose dose = Adrenaline.dose(event.getItemStack());
        if (dose == null) return;

        List<Component> lines = event.getToolTip();
        lines.add(Component.translatable(STAMINA, dose.recoveryPercent())
                .withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable(RELIEF, dose.reliefPercent())
                .withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable(HEART).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(OVERDOSE, Adrenaline.DOSE_LIMIT,
                        Adrenaline.WINDOW_TICKS / Adrenaline.TICKS_PER_MINUTE,
                        Adrenaline.POISON_TICKS / Adrenaline.TICKS_PER_SECOND,
                        Adrenaline.NAUSEA_TICKS / Adrenaline.TICKS_PER_SECOND)
                .withStyle(ChatFormatting.RED));
    }
}
