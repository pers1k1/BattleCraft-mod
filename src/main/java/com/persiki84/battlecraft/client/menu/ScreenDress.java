package com.persiki84.battlecraft.client.menu;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.shared.client.ui.UiDress;
import com.persiki84.shared.client.ui.UiFont;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

// WHY: среда экрана держалась на ScreenEvent, которого у закрытого экрана нет, и горение рисовало
// WHY: ванильные виджеты голыми; теперь это пара, которую зовут и события, и горение
public final class ScreenDress implements UiDress.Wardrobe {
    private static final ScreenDress SHARED = new ScreenDress();
    private static final String TITLE_FIELD = "f_96539_";

    private static boolean painted;
    private static boolean titleProbed;
    private static Field titleField;
    private static Component plainTitle;
    private static Screen titleOwner;

    private ScreenDress() {}

    public static ScreenDress shared() {
        return SHARED;
    }

    // WHY: ванильный экран считает ширину строк в init и рисует её в render: без общего пера
    // WHY: MultiLineLabel центрирует текст по ванильной метрике, и строка уезжает влево
    public static void build(Screen screen) {
        if (dressed(screen)) UiFont.push();
    }

    public static void built(Screen screen) {
        if (dressed(screen)) UiFont.pop();
    }

    @Override
    public void begin(GuiGraphics graphics, Screen screen) {
        painted = false;
        if (dressed(screen)) {
            UiFont.push();
            emboldenTitle(screen);
        }
        WidgetRestyle.noteDress();
        WidgetRestyle.mute(screen);
        ScrollRestyle.configure(screen);
        ScreenSkin.begin(graphics, screen);
    }

    @Override
    public void end(GuiGraphics graphics, Screen screen) {
        restyle(graphics, screen);
        ScreenSkin.end(graphics);
        WidgetRestyle.restore();
        restoreTitle();
        UiFont.pop();
    }

    public static void restyle(GuiGraphics graphics, Screen screen) {
        if (painted) return;
        painted = true;
        if ((HudConfig.plainScreens() & 1) != 0) return;

        graphics.flush();
        float delta = UiFrame.delta();
        WidgetRestyle.paint(graphics, delta);
        ScrollRestyle.paint(graphics, screen, delta);
    }

    private static boolean dressed(Screen screen) {
        return !(screen instanceof AbstractContainerScreen<?>) && !ScreenSkin.measured(screen);
    }

    private static void emboldenTitle(Screen screen) {
        restoreTitle();
        if (!bindTitle()) return;

        Component title = screen.getTitle();
        if (title.getString().isEmpty()) return;

        ResourceLocation face = UiRender.boldFaceFor(
                (float) Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale()));
        try {
            titleField.set(screen, title.copy().withStyle(style -> style.withFont(face)));
            plainTitle = title;
            titleOwner = screen;
        } catch (Throwable error) {
            titleField = null;
        }
    }

    private static boolean bindTitle() {
        if (!titleProbed) {
            titleProbed = true;
            try {
                titleField = ObfuscationReflectionHelper.findField(Screen.class, TITLE_FIELD);
            } catch (Throwable error) {
                titleField = null;
            }
        }
        return titleField != null;
    }

    private static void restoreTitle() {
        if (titleOwner == null) return;
        try {
            titleField.set(titleOwner, plainTitle);
        } catch (Throwable error) {
            titleField = null;
        }
        titleOwner = null;
        plainTitle = null;
    }
}
