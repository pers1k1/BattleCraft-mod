package com.persiki84.battlecraft.client.combat;

import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class RespawnStamina {
    private static final int REFILL_TICKS = 20;

    private static int refilling;

    private RespawnStamina() {}

    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
        if (event.getOldPlayer().isDeadOrDying()) refilling = REFILL_TICKS;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        refilling = 0;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || refilling <= 0) return;
        if (!(event.player instanceof LocalPlayer player) || !player.isLocalPlayer()) return;

        refilling--;
        ParkourStamina.fill(player);
    }
}
