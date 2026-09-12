package com.persiki84.zones.client;

import com.persiki84.capturepoints.client.KeyBindings;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneType;
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

        Zone zone = ZoneOccupancy.current();
        if (zone == null || zone.type() != ZoneType.SHOP) return;
        if (!KeyBindings.CAPTURE_KEY.consumeClick()) return;

        ShopScreen.open();
    }
}
