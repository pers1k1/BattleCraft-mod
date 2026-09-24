package com.persiki84.dmgndctr.client;

import com.persiki84.dmgndctr.DmgIndicatorMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DmgIndicatorMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DamageOverlays {

    private DamageOverlays() {}

    // WHY: цифры лежат под прицелом и под остальным худом: они часть сцены, а не интерфейса,
    // WHY: и не имеют права закрывать собой полосы здоровья и патроны
    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelow(VanillaGuiOverlay.CROSSHAIR.id(), "damage_numbers", DamageHud.OVERLAY);
    }
}
