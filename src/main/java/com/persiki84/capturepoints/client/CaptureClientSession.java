package com.persiki84.capturepoints.client;

import com.persiki84.capturepoints.CapturePointsMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CapturePointsMod.MOD_ID, value = Dist.CLIENT)
public final class CaptureClientSession {

    private CaptureClientSession() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientCaptureData.forget();
        CaptureReturnHud.clear();
        ObjectiveHud.reset();
    }
}
