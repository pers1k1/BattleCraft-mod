package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ToastAddEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class ChatSecurityToastFilter {
    private ChatSecurityToastFilter() {}

    @SubscribeEvent
    public static void onToastAdded(ToastAddEvent event) {
        if (!(event.getToast() instanceof SystemToast toast)) return;
        if (toast.getToken() != SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) return;

        event.setCanceled(true);
    }
}
