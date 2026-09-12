package com.persiki84.battlecraft.client.combat;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.ClientGameRules;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class JumpStamina {
    private static final float PERMILLE = 1000.0f;

    private JumpStamina() {}

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !player.isLocalPlayer()) return;

        ParkourStamina.drain(player, cost(player));
    }

    private static int cost(LocalPlayer player) {
        int share = GunHold.armed(player)
                ? ClientGameRules.stamina().armedJumpPermille()
                : ClientGameRules.stamina().jumpPermille();
        if (share <= 0) return 0;

        int maximum = ParkourStamina.maxValue(player);
        if (maximum <= 0) return 0;

        return Math.max(1, Math.round(maximum * share / PERMILLE));
    }
}
