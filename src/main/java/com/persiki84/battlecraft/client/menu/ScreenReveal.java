package com.persiki84.battlecraft.client.menu;

import com.persiki84.shared.client.ui.UiAssemble;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiMotion;
import com.persiki84.shared.client.ui.UiMotionSet;
import com.persiki84.shared.client.ui.UiReveal;
import com.persiki84.shared.client.ui.UiStage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;

// WHY: настройки проявляются наводкой на резкость при любом наборе движения, кроме инея: проход
// WHY: короткий, стоит один полноэкранный квад и не зависит от разбора кадра на осколки
public final class ScreenReveal {
    private static final float CHARGE = 0.55f;

    private static final UiReveal REVEAL = new UiReveal();

    private static Screen owner;
    private static boolean staged;
    private static float outerPresence = 1.0f;

    private ScreenReveal() {}

    public static boolean handles(Screen screen) {
        return UiReveal.enabled() && wanted(screen);
    }

    public static boolean settled(Screen screen) {
        return screen == owner && !(handles(screen) && REVEAL.active());
    }

    // WHY: уход настроек идёт инеем, и его доля входа имеет смысл только у входа инеем; наводка
    // WHY: резкости к середине входа уже почти полная, и уход берёт её как доигранную
    public static float arrival(Screen screen) {
        if (screen != owner || !handles(screen) || !REVEAL.active()) return 1.0f;
        return motion(screen) == UiMotionSet.FROST ? REVEAL.phase() : 1.0f;
    }

    private static boolean wanted(Screen screen) {
        return screen instanceof OptionsScreen || screen instanceof OptionsSubScreen;
    }

    public static void follow(Screen screen) {
        if (screen != owner) {
            owner = screen;
            REVEAL.restart();
            REVEAL.pace(motion(screen).enterSeconds());
        }
        if (handles(screen)) REVEAL.advance();
    }

    // WHY: фон экрана уже лежит в кадре, поэтому стадия снимается после него и собирает только
    // WHY: содержимое: ровно как у своих экранов, где вуаль и подложка идут до UiStage.begin
    public static void stage(GuiGraphics graphics, Screen screen) {
        release();
        if (!handles(screen) || !REVEAL.active()) return;

        graphics.flush();
        staged = UiStage.begin();
        REVEAL.report(screen.getClass().getSimpleName(), staged);
        if (staged) outerPresence = UiGlassStyle.scalePresence(UiReveal.glassPresence(REVEAL.phase()));
    }

    public static void compose(GuiGraphics graphics, Screen screen) {
        if (!staged) return;

        staged = false;
        graphics.flush();
        UiGlassStyle.restorePresence(outerPresence);
        UiStage.end();
        UiAssemble.draw(graphics, screen.width, screen.height, UiStage.texture(),
                REVEAL.phase(), REVEAL.seconds(), motion(screen).enterMode(),
                0.0f, screen.height, CHARGE);
    }

    // WHY: иней короче резкости и тоже без разбора кадра, поэтому настройки берут его, когда он выбран
    private static UiMotionSet motion(Screen screen) {
        return UiMotion.of(screen) == UiMotionSet.FROST ? UiMotionSet.FROST : UiMotionSet.GLASS;
    }

    // WHY: между снятием стадии и композитом стоит целый кадр чужой отрисовки, и оставленная
    // WHY: привязка означала бы чёрный экран до перезахода: кадр без композита её снимает сам.
    // WHY: Сила стекла на входе держится на том же отрезке и возвращается там же, где снимается стадия
    private static void release() {
        if (!staged) return;
        staged = false;
        UiGlassStyle.restorePresence(outerPresence);
        UiStage.end();
    }
}
