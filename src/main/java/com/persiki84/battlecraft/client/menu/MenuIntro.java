package com.persiki84.battlecraft.client.menu;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiAssemble;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiMotion;
import com.persiki84.shared.client.ui.UiMotionSet;
import com.persiki84.shared.client.ui.UiReveal;

public final class MenuIntro {
    private static final float IDLE = -1.0f;
    private static final float MAX_STEP = 1.0f / 15.0f;
    // WHY: второй кадр титульного экрана приходит после загрузки мира или ресурсов, и шаг без зажима
    // WHY: проглатывал короткий вход целиком: как у экранов, не меньше шести кадров на вход
    private static final float MIN_FRAMES = 6.0f;

    private static UiMotionSet set = UiMotionSet.IGNITE;
    private static float span = UiMotionSet.IGNITE.titleSeconds();
    private static float elapsed = IDLE;
    private static long stamp = -1L;

    private MenuIntro() {}

    // WHY: сцена делится последовательно, поэтому горение закрытого экрана входу больше не мешает:
    // WHY: возврат в главное меню из выбора мира и из настроек обязан проявляться, как первый заход
    public static void begin(Object screen) {
        if (!HudConfig.menuIntro() || !UiAssemble.ready()) return;

        set = UiMotion.of(screen);
        span = set.titleSeconds();
        elapsed = 0.0f;
        stamp = UiFrame.frame();
    }

    public static boolean running() {
        return elapsed >= 0.0f;
    }

    public static float mode() {
        return set.enterMode();
    }

    public static float phase() {
        return UiAnim.clamp01(elapsed / span);
    }

    public static float seconds() {
        return Math.max(0.0f, elapsed);
    }

    public static void advance() {
        if (elapsed < 0.0f) return;

        long frame = UiFrame.frame();
        if (frame == stamp) return;
        stamp = frame;

        elapsed += Math.min(Math.min(MAX_STEP, span / MIN_FRAMES), UiFrame.delta());
        if (elapsed >= span) finish();
    }

    // WHY: линза титульного экрана набирает силу вместе с входом, а без стадии вход не играет
    public static float presence(boolean staged) {
        return staged ? UiReveal.glassPresence(phase()) : 1.0f;
    }

    public static void skip() {
        if (elapsed < 0.0f) return;
        finish();
    }

    private static void finish() {
        elapsed = IDLE;
    }
}
