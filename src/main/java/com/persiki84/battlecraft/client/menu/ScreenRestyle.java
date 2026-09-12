package com.persiki84.battlecraft.client.menu;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.shared.client.menu.GlassScreen;
import com.persiki84.shared.client.ui.UiBackdrop;
import com.persiki84.shared.client.ui.UiFarewell;
import com.persiki84.shared.client.ui.UiFont;
import com.persiki84.shared.client.ui.UiSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class ScreenRestyle {
    private static final ResourceLocation VANILLA_CLICK = new ResourceLocation("minecraft", "ui.button.click");

    private static final int TOOLTIP_FILL = 0xF00C0C10;
    private static final int TOOLTIP_EDGE_TOP = 0x40FFFFFF;
    private static final int TOOLTIP_EDGE_BOTTOM = 0x18FFFFFF;

    private static boolean reported;
    private static boolean silent;
    private static Screen tracked;

    private ScreenRestyle() {}

    @SubscribeEvent
    public static void onBackground(ScreenEvent.BackgroundRendered event) {
        reported = true;
        event.getGuiGraphics().flush();
        UiBackdrop.capture();
    }

    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        Screen screen = event.getScreen();
        reported = false;

        if (screen != tracked) {
            tracked = screen;
            silent = true;
        }
        if (!(screen instanceof GlassScreen)) {
            UiBackdrop.suspend();
        }
        ScreenReveal.follow(screen);
        ScreenDress.shared().begin(event.getGuiGraphics(), screen);
        if (silent && ScreenSkin.needsFallback(screen)) ScreenSkin.paint(screen, event.getGuiGraphics());
        ScreenReveal.stage(event.getGuiGraphics(), screen);
    }

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        event.setBackground(TOOLTIP_FILL);
        event.setBorderStart(TOOLTIP_EDGE_TOP);
        event.setBorderEnd(TOOLTIP_EDGE_BOTTOM);
    }

    @SubscribeEvent
    public static void onTooltip(RenderTooltipEvent.Pre event) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) ScreenDress.restyle(event.getGraphics(), screen);
    }

    // WHY: горение одевает закрытый экран той же парой, поэтому идёт после снятия среды живого экрана
    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        ScreenDress.shared().end(event.getGuiGraphics(), screen);
        silent = !reported;
        ScreenReveal.compose(event.getGuiGraphics(), screen);
        UiFarewell.render(event.getGuiGraphics(), screen.width, screen.height, event.getPartialTick());
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!ScreenSkin.accepting()) {
            event.setCanceled(true);
            return;
        }
        if (event.getButton() == 0 && WidgetRestyle.press(event.getMouseX(), event.getMouseY())) {
            UiSound.press();
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        UiFont.pop();
        WidgetRestyle.forget();
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || Minecraft.getInstance().screen == null) return;
        if (VANILLA_CLICK.equals(sound.getLocation())) {
            event.setSound(null);
        }
    }
}
