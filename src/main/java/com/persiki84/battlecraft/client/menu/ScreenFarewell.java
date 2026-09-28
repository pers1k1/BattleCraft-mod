package com.persiki84.battlecraft.client.menu;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.shared.client.menu.GlassScreen;
import com.persiki84.shared.client.ui.UiEmber;
import com.persiki84.shared.client.ui.UiFarewell;
import com.persiki84.shared.client.ui.UiMotionSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// WHY: ванильный экран входил въездом или наводкой, а уходил одним кадром: пауза по Esc пропадала
// WHY: целиком, пока гасла только вуаль. Теперь закрытый экран доигрывает уход инеем при любом наборе
// WHY: движения, как настройки берут короткий вход: расфокус, лёгкое увеличение и прозрачность
@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class ScreenFarewell implements UiEmber {
    private static final String VANILLA = "net.minecraft.";
    private static final ScreenFarewell EMBER = new ScreenFarewell();

    private static Screen leaving;
    private static boolean painting;
    private static int cursorX;
    private static int cursorY;

    private ScreenFarewell() {}

    // WHY: уход заводится до того, как ScreenRestyle на этом же событии забудет состояние виджетов:
    // WHY: при идущем уходе оно откладывается, и подсветка кнопки под курсором не гаснет на первом кадре
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() == event.getCurrentScreen()) return;
        leave(event.getCurrentScreen());
    }

    // WHY: закрытие в мир идёт без Opening, поэтому тот же уход заводится и здесь
    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing event) {
        leave(event.getScreen());
    }

    public static boolean painting(Screen screen) {
        return painting && screen == leaving;
    }

    private static void leave(Screen closed) {
        if (closed == null || closed == leaving || !leaves(closed)) return;

        freezeCursor();
        if (!UiFarewell.depart(EMBER, UiMotionSet.FROST, ScreenReveal.arrival(closed))) return;
        leaving = closed;
        UiFarewell.defer(EMBER, ScreenFarewell::forget);
    }

    private static boolean leaves(Screen screen) {
        if (screen instanceof GlassScreen) return false;
        if (!screen.getClass().getName().startsWith(VANILLA)) return false;
        return ScreenReveal.handles(screen) || ScreenSkin.animated(screen);
    }

    private static void freezeCursor() {
        Minecraft minecraft = Minecraft.getInstance();
        double screenWidth = Math.max(1, minecraft.getWindow().getScreenWidth());
        double screenHeight = Math.max(1, minecraft.getWindow().getScreenHeight());
        cursorX = (int) (minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / screenWidth);
        cursorY = (int) (minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / screenHeight);
    }

    private static void forget() {
        leaving = null;
    }

    // WHY: отменённое открытие оставляет экран на месте: живой экран рисует себя сам, и уход молчит
    @Override
    public void paintEmber(GuiGraphics graphics, float partialTick) {
        Screen screen = leaving;
        if (screen == null || screen == Minecraft.getInstance().screen) return;

        painting = true;
        ScreenDress.shared().begin(graphics, screen);
        try {
            screen.render(graphics, cursorX, cursorY, partialTick);
        } finally {
            ScreenDress.shared().end(graphics, screen);
            painting = false;
        }
    }
}
