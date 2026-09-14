package com.persiki84.battlecraft.client.menu;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.setup.SetupScreen;
import com.persiki84.battlecraft.client.setup.SetupState;
import com.persiki84.battlecraft.client.menu.browse.ServerBrowseScreen;
import com.persiki84.battlecraft.client.menu.browse.WorldBrowseScreen;
import com.persiki84.shared.client.ui.UiBoot;
import com.persiki84.shared.client.ui.UiSound;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class MenuEvents {

    private MenuEvents() {}

    // WHY: здесь остались только экраны с другим содержимым: пауза, инвентарь, творческий
    // WHY: инвентарь и ящики одеваются миксинами и второй раз не строятся
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        skipAccessibility(event);
        replaceTitle(event);
        replaceBrowsers(event);
        announce(event);
    }

    private static void skipAccessibility(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof AccessibilityOnboardingScreen)) return;

        Minecraft client = Minecraft.getInstance();
        client.options.onboardAccessibility = false;
        client.options.save();
        event.setNewScreen(SetupState.needed() ? new SetupScreen() : new MinimalTitleScreen());
    }

    private static void replaceTitle(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof TitleScreen) || event.getNewScreen() instanceof MinimalTitleScreen) {
            return;
        }
        event.setNewScreen(SetupState.needed() ? new SetupScreen() : new MinimalTitleScreen());
    }

    // WHY: подмена не только в титульном экране: в ванильный выбор мира и сервера возвращают
    // WHY: создание мира, правка сервера и подтверждения, и без перехвата возврат уводил бы в ваниль
    private static void replaceBrowsers(ScreenEvent.Opening event) {
        if (!HudConfig.browseScreens()) return;

        Screen opening = event.getNewScreen();
        Screen parent = event.getCurrentScreen();
        if (opening instanceof SelectWorldScreen) {
            event.setNewScreen(new WorldBrowseScreen(parent));
        } else if (opening instanceof JoinMultiplayerScreen) {
            event.setNewScreen(new ServerBrowseScreen(parent));
        }
    }

    // WHY: главное меню подставляется ещё под мозанговским оверлеем, и звук открытия
    // WHY: раздался бы за секунду до того, как экран станет виден
    private static void announce(ScreenEvent.Opening event) {
        if (event.getCurrentScreen() == null && !UiBoot.loading()) UiSound.screenOpen();
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        UiSound.screenClose();
    }
}
