package com.persiki84.battlecraft.client.menu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.persiki84.battlecraft.client.loading.LoadingScreens;
import com.persiki84.shared.client.menu.DimmedScreen;
import com.persiki84.shared.client.menu.ScreenDim;
import com.persiki84.shared.client.menu.ScreenVeil;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiBackdrop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraftforge.client.gui.ModListScreen;

public final class ScreenSkin {
    private static final float ENTER_SCALE = 0.965f;
    private static final float ENTER_LIFT = 5.0f;
    private static final float ENTER_SPEED = 21.0f;
    private static final float SETTLED = 0.997f;
    private static final float INPUT_GATE = 0.45f;

    private static final String[] FOREIGN_MENUS = {
            "me.shedaniel.clothconfig2",
            "me.shedaniel.autoconfig",
            "me.jellysquid.mods.sodium.client.gui",
            "org.embeddedt.embeddium",
            "com.seibel.distanthorizons",
            "de.maxhenkel.voicechat.gui",
            "com.alrex.parcool.client.gui",
            "com.supermartijn642",
            "team.creative.creativecore.common.gui",
            "fr.flaton.walkietalkie.client.gui",
            "net.mcreator.survivalinstinct.client.gui",
            "com.tacz.guns.client.gui",
            "noppes.npcs.client.gui",
            "net.minecraftforge.client.gui"
    };

    private static final String[] SEE_THROUGH_MENUS = {
            "com.tacz.guns.client.gui.GunRefitScreen"
    };

    private static final String[] MEASURED_MENUS = {
            "com.tacz.guns.client.gui",
            "com.atsuishio.superbwarfare.client.screens",
            "com.seibel.distanthorizons"
    };

    private static final Smooth entrance = new Smooth(1.0f, ENTER_SPEED);

    private static Screen opened;
    private static boolean lifted;

    private ScreenSkin() {}

    public static void advance(float delta) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != opened) {
            opened = screen;
            entrance.snap(screen == null || !animated(screen) ? 1.0f : 0.0f);
        }
        entrance.to(1.0f, delta);
    }

    public static float entrance() {
        return UiAnim.easeOut(entrance.get());
    }

    public static boolean settled() {
        return entrance() >= SETTLED;
    }

    public static boolean accepting() {
        return entrance() >= INPUT_GATE;
    }

    private static float scale() {
        return ENTER_SCALE + (1.0f - ENTER_SCALE) * entrance();
    }

    private static float lift() {
        return ENTER_LIFT * (1.0f - entrance());
    }

    public static void begin(GuiGraphics graphics, Screen screen) {
        lifted = false;
        if (settled() || !animated(screen)) return;

        lifted = true;
        PoseStack pose = graphics.pose();
        float scale = scale();
        pose.pushPose();
        pose.translate(screen.width / 2.0f, screen.height / 2.0f + lift(), 0.0f);
        pose.scale(scale, scale, 1.0f);
        pose.translate(-screen.width / 2.0f, -screen.height / 2.0f, 0.0f);
    }

    public static void end(GuiGraphics graphics) {
        if (!lifted) return;
        lifted = false;
        graphics.pose().popPose();
    }

    public static boolean paint(Screen screen, GuiGraphics graphics) {
        if (seeThrough(screen) || screen instanceof MinimalTitleScreen) return false;

        boolean unwound = unwind(graphics, screen);
        if (aurora(screen)) {
            MenuBackground.shared().render(graphics, screen.width, screen.height);
        } else {
            ScreenDim.render(graphics, screen.width, screen.height, ScreenVeil.value());
        }
        if (unwound) graphics.pose().popPose();

        // WHY: поля рисуются уже в позе входа: снятая под фон поза оставляла их единственным
        // WHY: неподвижным пятном, пока весь остальной экран наезжал
        WidgetRestyle.paintFields(graphics);
        graphics.flush();
        UiBackdrop.capture();
        ScreenVeil.mark(screen);
        return true;
    }

    private static boolean unwind(GuiGraphics graphics, Screen screen) {
        if (!lifted) return false;

        PoseStack pose = graphics.pose();
        float scale = scale();
        pose.pushPose();
        pose.translate(screen.width / 2.0f, screen.height / 2.0f, 0.0f);
        pose.scale(1.0f / scale, 1.0f / scale, 1.0f);
        pose.translate(-screen.width / 2.0f, -screen.height / 2.0f - lift(), 0.0f);
        return true;
    }

    // WHY: пока идёт проявление, геометрия обязана стоять на своих пикселях: въезд масштабом кладёт
    // WHY: кромки на дробные пиксели, и в стыках элементов проступает кадр без интерфейса
    public static boolean animated(Screen screen) {
        if (screen == null || seeThrough(screen)) return false;
        if (ScreenReveal.handles(screen)) return false;
        if (screen instanceof DimmedScreen) return false;
        if (screen instanceof AbstractContainerScreen<?>) return false;
        if (screen instanceof MinimalTitleScreen) return false;
        return !(screen instanceof ChatScreen)
                && !(screen instanceof ProgressScreen)
                && !(screen instanceof ReceivingLevelScreen)
                && !(screen instanceof LevelLoadingScreen)
                && !(screen instanceof GenericDirtMessageScreen)
                && !(screen instanceof ConnectScreen);
    }

    public static boolean needsFallback(Screen screen) {
        if (screen instanceof DimmedScreen || screen instanceof MinimalTitleScreen) return false;
        if (LoadingScreens.handles(screen)) return false;
        return aurora(screen);
    }

    public static boolean aurora(Screen screen) {
        if (Minecraft.getInstance().level == null) return true;
        return screen instanceof OptionsScreen
                || screen instanceof OptionsSubScreen
                || screen instanceof PackSelectionScreen
                || screen instanceof ModListScreen
                || screen instanceof ReceivingLevelScreen
                || screen instanceof ProgressScreen
                || foreign(screen);
    }

    public static boolean measured(Screen screen) {
        return matches(screen, MEASURED_MENUS);
    }

    public static boolean seeThrough(Screen screen) {
        return matches(screen, SEE_THROUGH_MENUS);
    }

    public static boolean foreign(Screen screen) {
        return matches(screen, FOREIGN_MENUS);
    }

    private static boolean matches(Screen screen, String[] prefixes) {
        if (screen == null) return false;
        String owner = screen.getClass().getName();
        for (String prefix : prefixes) {
            if (owner.startsWith(prefix)) return true;
        }
        return false;
    }
}
