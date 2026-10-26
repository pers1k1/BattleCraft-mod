package com.persiki84.zones.client;

import com.persiki84.battlecraft.client.bases.TravelScreen;
import com.persiki84.capturepoints.client.KeyBindings;
import com.persiki84.zones.ZonesMod;
import com.persiki84.zones.client.menu.ShopScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZonesMod.MOD_ID, value = Dist.CLIENT)
public final class ZoneInteractKey {

    private ZoneInteractKey() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) return;

        boolean shop = ShopPlaces.here();
        if (!shop && !ShopPlaces.travelOnly(ZoneOccupancy.current())) return;
        if (!KeyBindings.CAPTURE_KEY.consumeClick()) return;

        if (shop) {
            ShopScreen.open();
        } else {
            TravelScreen.open(null);
        }
    }
}
