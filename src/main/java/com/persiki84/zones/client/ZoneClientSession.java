package com.persiki84.zones.client;

import com.persiki84.zones.ZonesMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZonesMod.MOD_ID, value = Dist.CLIENT)
public final class ZoneClientSession {

    private ZoneClientSession() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ZonesMod.clearClientState();
    }
}
