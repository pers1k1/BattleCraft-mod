package com.persiki84.battlecraft.client.menu.panel.craft;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.craft.CraftBan;
import com.persiki84.battlecraft.craft.CraftPhase;
import com.persiki84.battlecraft.craft.CraftRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// WHY: строки подсказки собраны один раз: подсказка строится каждый кадр, пока курсор стоит на предмете
@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class CraftBanClient {
    private static final Component ACTIVE = Component.translatable("battlecraft.craft.tooltip.active")
            .withStyle(ChatFormatting.RED);
    private static final Component IN_MATCH = Component.translatable("battlecraft.craft.tooltip.match")
            .withStyle(ChatFormatting.GRAY);
    private static final Component IN_LOBBY = Component.translatable("battlecraft.craft.tooltip.lobby")
            .withStyle(ChatFormatting.GRAY);

    private CraftBanClient() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        CraftBan ban = CraftRules.clientBan(event.getItemStack());
        if (ban == null) return;
        event.getToolTip().add(lineFor(ban));
    }

    private static Component lineFor(CraftBan ban) {
        if (ban.activeWhen(CraftRules.clientRunning())) return ACTIVE;
        return ban.phase() == CraftPhase.MATCH ? IN_MATCH : IN_LOBBY;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CraftRules.forgetClient();
    }
}
