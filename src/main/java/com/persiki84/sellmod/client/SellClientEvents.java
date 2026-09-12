package com.persiki84.sellmod.client;

import com.persiki84.sellmod.SellMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SellMod.MODID, value = Dist.CLIENT)
public final class SellClientEvents {

    private SellClientEvents() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSellData.clear();
    }
}
