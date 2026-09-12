package com.persiki84.capturepoints.event;

import com.persiki84.capturepoints.CapturePointsMod;
import com.persiki84.capturepoints.capture.CaptureSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CapturePointsMod.MOD_ID)
public class CaptureEventHandler {

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;

        creditKiller(event, victim);
        CaptureSessions.onDeath(victim, victim.getServer());
    }

    private void creditKiller(LivingDeathEvent event, ServerPlayer victim) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (killer == victim) return;

        CaptureSessions.creditKill(killer, victim);
    }
}
