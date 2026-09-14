package com.persiki84.zones.client;

import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneType;
import com.persiki84.zones.ZonesMod;
import com.persiki84.zones.client.menu.ShopScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZonesMod.MOD_ID, value = Dist.CLIENT)
// WHY: выход из зоны закрывает магазин, но редактор витрины открывают из меню и откуда угодно:
// WHY: без исключения он захлопывался в тот же тик, и править состав было негде
public final class ShopPresence {

    private ShopPresence() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(Minecraft.getInstance().screen instanceof ShopScreen shop)) return;

        if (shop.editing()) return;

        Zone zone = ZoneOccupancy.current();
        if (zone == null || zone.type() != ZoneType.SHOP) {
            shop.dismiss();
        }
    }
}
