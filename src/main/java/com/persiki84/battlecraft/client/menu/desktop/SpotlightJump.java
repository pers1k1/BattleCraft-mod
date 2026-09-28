package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.menu.ScreenReveal;
import com.persiki84.shared.client.menu.SearchBeacon;
import com.persiki84.shared.client.ui.UiFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// WHY: ванильные экраны настроек не знают о поиске, поэтому переход к строке ведётся снаружи:
// WHY: после сборки экрана список прокручивается к опции, а поверх кадра строка разгорается
@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class SpotlightJump {
    private static final SearchBeacon BEACON = new SearchBeacon();

    private static Screen screen;
    private static SpotlightTarget target;
    private static SpotlightTarget.Spot spot;

    private SpotlightJump() {}

    static void open(Screen opened, SpotlightTarget wanted) {
        screen = opened;
        target = wanted;
        spot = null;
        BEACON.arm();
        Minecraft.getInstance().setScreen(opened);
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (screen == null || event.getScreen() != screen) return;

        spot = target.locate(screen);
        if (spot == null) forget();
    }

    // WHY: стеклянные кнопки ванильного экрана перерисовываются в том же Render.Post, и порядок
    // WHY: подписчиков одного приоритета не задан: вспышка идёт последней, иначе стекло её закрывало
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRender(ScreenEvent.Render.Post event) {
        if (screen == null) return;
        if (event.getScreen() != screen) {
            forget();
            return;
        }
        float strength = BEACON.advance(ScreenReveal.settled(screen), UiFrame.delta());
        if (!BEACON.armed()) {
            forget();
            return;
        }
        if (spot != null && strength > 0.0f) spot.paint(event.getGuiGraphics(), strength);
    }

    private static void forget() {
        BEACON.stop();
        screen = null;
        target = null;
        spot = null;
    }
}
