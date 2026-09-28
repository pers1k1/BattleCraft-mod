package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.shared.client.ui.UiCorner;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// WHY: форма угла HUD и меню разошлась: всё, что рисует проход Gui, держит прежний сквиркл HUD.
// WHY: Открытие на LOWEST: отменённый чужим модом Pre не дойдёт сюда, и Post без пары не оставит защёлку
@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class HudCornerPass {
    private HudCornerPass() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGuiOpen(RenderGuiEvent.Pre event) {
        UiCorner.hud(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGuiClose(RenderGuiEvent.Post event) {
        UiCorner.hud(false);
    }
}
