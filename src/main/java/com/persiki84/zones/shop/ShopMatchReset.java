package com.persiki84.zones.shop;

import com.persiki84.battlecraft.MatchEvent;
import com.persiki84.zones.ZonesMod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZonesMod.MOD_ID)
public final class ShopMatchReset {

    private ShopMatchReset() {}

    @SubscribeEvent
    public static void onMatchStarted(MatchEvent.Started event) {
        if (ShopCatalog.refillAll()) ZonesMod.syncShopToEveryone(event.server());
    }
}
