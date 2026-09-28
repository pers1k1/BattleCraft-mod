package com.persiki84.battlecraft.client.menu;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.menu.desktop.DesktopWallpaper;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAmbience;
import com.persiki84.shared.client.ui.UiAurora;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class MenuBackground {
    private static final float POINTER_SPEED = 1.9f;
    private static final float SCREEN_CENTER = 0.5f;

    private static final MenuBackground SHARED = new MenuBackground();

    private final Smooth pointerX = new Smooth(SCREEN_CENTER, POINTER_SPEED);
    private final Smooth pointerY = new Smooth(SCREEN_CENTER, POINTER_SPEED);
    private long aimedFrame = -1L;

    public static MenuBackground shared() {
        return SHARED;
    }

    // WHY: один выбор фона на всё меню: при рабочем столе его обои стоят и под экранами загрузки
    // WHY: мира, и под настройками, иначе при входе в мир мелькало бы прежнее главное меню
    public void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) return;

        UiAmbience.advance();
        aim();
        if (HudConfig.menuDesktop()
                && DesktopWallpaper.shared().render(graphics, screenWidth, screenHeight, pointerX.get(), pointerY.get())) {
            return;
        }
        int backdrop = UiPalette.backdrop();
        if (UiAurora.field(graphics, screenWidth, screenHeight, backdrop, pointerX.get(), pointerY.get())) return;

        UiRender.rect(graphics, 0.0f, 0.0f, screenWidth, screenHeight, backdrop);
    }

    // WHY: фон за кадр рисуется не один раз (подложка под окном и сам экран), а сглаживание
    // WHY: курсора обязано шагать раз в кадр, иначе линза бегала бы вдвое быстрее
    private void aim() {
        long frame = UiFrame.frame();
        if (frame == aimedFrame) return;

        aimedFrame = frame;
        float delta = UiFrame.delta();
        pointerX.to(pointerFraction(true), delta);
        pointerY.to(pointerFraction(false), delta);
    }

    private static float pointerFraction(boolean horizontal) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen == null) return SCREEN_CENTER;

        double scale = client.getWindow().getGuiScale();
        float size = horizontal ? (float) (client.getWindow().getScreenWidth() / scale)
                : (float) (client.getWindow().getScreenHeight() / scale);
        if (size <= 0.0f) return SCREEN_CENTER;

        float position = horizontal ? (float) (client.mouseHandler.xpos() / scale)
                : (float) (client.mouseHandler.ypos() / scale);
        return Mth.clamp(position / size, 0.0f, 1.0f);
    }
}
