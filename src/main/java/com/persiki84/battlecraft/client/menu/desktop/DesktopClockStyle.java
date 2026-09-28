package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.ui.Spring;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiGlassText;
import net.minecraft.network.chat.Component;

import java.util.List;

// WHY: вид часов из меню по правой кнопке: стекло или сплошные цифры, жирность и размытость.
// WHY: Каждая величина едет пружиной без отскока около 0.4 с, и пока хоть одна в пути, форма цифр
// WHY: расплывается и к концу перехода собирается обратно в чёткий контур
final class DesktopClockStyle {
    private static final float RESPONSE = 0.4f;
    private static final float DAMPING = 1.0f;
    // WHY: у критически задемпфированной пружины пик скорости на единичном шаге равен ω/e
    private static final float PEAK_SPEED = (float) (2.0 * Math.PI / RESPONSE / Math.E);
    private static final float SOFTEN_SHARE = 0.02f;
    private static final float[] WEIGHTS = {-0.023f, 0.0f, 0.023f, 0.047f};
    private static final float[] FROSTS = {0.04f, 0.16f, 0.36f, 0.58f};
    private static final String[] WEIGHT_NAMES = {"thin", "regular", "bold", "heavy"};
    private static final String[] FROST_NAMES = {"clear", "light", "regular", "strong"};
    private static final String KEY = "battlecraft.desktop.clock.";

    private final Spring solid = new Spring(RESPONSE, DAMPING);
    private final Spring weight = new Spring(RESPONSE, DAMPING);
    private final Spring frost = new Spring(RESPONSE, DAMPING);
    private final UiGlassText.Style style = new UiGlassText.Style();

    UiGlassText.Style advance(float delta) {
        float solidNow = UiAnim.clamp01(solid.to(HudConfig.clockGlass() ? 0.0f : 1.0f, delta));
        float weightNow = stepped(WEIGHTS, weight.to(HudConfig.clockWeight(), delta));
        float frostNow = stepped(FROSTS, frost.to(HudConfig.clockFrost(), delta));
        style.set(solidNow, UiAccent.text(), weightNow, frostNow, settling() * SOFTEN_SHARE);
        return style;
    }

    UiGlassText.Style look() {
        return style;
    }

    float weight() {
        return stepped(WEIGHTS, weight.get());
    }

    private float settling() {
        float fastest = Math.max(Math.abs(solid.velocity()),
                Math.max(Math.abs(weight.velocity()), Math.abs(frost.velocity())));
        return UiAnim.clamp01(fastest / PEAK_SPEED);
    }

    private static float stepped(float[] table, float index) {
        float clamped = Math.max(0.0f, Math.min(table.length - 1.0f, index));
        int below = Math.min(table.length - 2, (int) Math.floor(clamped));
        float along = clamped - below;
        return table[below] + (table[below + 1] - table[below]) * along;
    }

    List<StudioMenu.Action> menu() {
        boolean glass = HudConfig.clockGlass();
        return List.of(
                item("glass", glass ? "on" : "off", () -> HudConfig.clockGlass(!HudConfig.clockGlass())),
                item("weight", "weight." + WEIGHT_NAMES[HudConfig.clockWeight()],
                        () -> HudConfig.clockWeight((HudConfig.clockWeight() + 1) % WEIGHTS.length)),
                item("frost", "frost." + FROST_NAMES[HudConfig.clockFrost()],
                        () -> HudConfig.clockFrost((HudConfig.clockFrost() + 1) % FROSTS.length)).when(glass));
    }

    private static StudioMenu.Action item(String setting, String value, Runnable run) {
        Component label = Component.translatable(KEY + setting, Component.translatable(KEY + value));
        return new StudioMenu.Action(label, run, false, false, true);
    }
}
