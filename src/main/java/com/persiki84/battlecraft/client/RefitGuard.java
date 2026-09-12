package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.hud.ToastHud;
import com.persiki84.battlecraft.client.hud.VisualsBridge;
import com.persiki84.battlecraft.rules.GameRule;
import com.persiki84.shared.client.ui.UiSound;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class RefitGuard {
    private static final String REFIT_SCREEN = "com.tacz.guns.client.gui.GunRefitScreen";
    private static final String BLOCKED_HURT = "battlecraft.blocked.refit_hurt";
    private static final String BLOCKED_COMBAT = "battlecraft.blocked.refit_combat";

    private RefitGuard() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!smithing(event.getNewScreen())) return;

        String reason = barred();
        if (reason == null) return;

        event.setCanceled(true);
        refuse(reason);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (!smithing(minecraft.screen)) return;

        String reason = barred();
        if (reason == null) return;

        minecraft.setScreen(null);
        refuse(reason);
    }

    private static String barred() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) return null;

        if (ClientGameRules.allows(GameRule.NO_REFIT_IN_COMBAT) && ClientCombatState.engaged()) {
            return BLOCKED_COMBAT;
        }
        if (ClientGameRules.allows(GameRule.NO_REFIT_WHEN_HURT) && VisualsBridge.wounded(player)) {
            return BLOCKED_HURT;
        }
        return null;
    }

    private static void refuse(String reason) {
        UiSound.chip(false);
        ToastHud.push(Component.translatable(reason).withStyle(ChatFormatting.RED), 0L);
    }

    private static boolean smithing(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith(REFIT_SCREEN);
    }
}
