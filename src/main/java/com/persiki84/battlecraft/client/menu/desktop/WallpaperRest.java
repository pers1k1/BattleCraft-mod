package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.hud.HudConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class WallpaperRest {
    private WallpaperRest() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        DesktopWallpaper.shared().restIfAway(Minecraft.getInstance().level != null || !HudConfig.menuDesktop());
    }
}
