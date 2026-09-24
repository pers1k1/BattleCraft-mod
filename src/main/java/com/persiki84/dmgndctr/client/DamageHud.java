package com.persiki84.dmgndctr.client;

import com.persiki84.battlecraft.client.ClientModules;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiWorldPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public final class DamageHud {
    private static final float BASE_SCALE = 1.05f;
    private static final float CRIT_SCALE = 1.3f;
    private static final float POP_SECONDS = 0.22f;
    private static final float POP_FLOOR = 0.55f;
    private static final float RISE = 22.0f;
    private static final float FADE_FROM = 0.62f;
    private static final float GONE = 0.02f;
    private static final float NEAR_BLOCKS = 6.0f;
    private static final float FAR_BLOCKS = 48.0f;
    private static final float NEAR_SIZE = 1.15f;
    private static final float FAR_SIZE = 0.7f;

    private DamageHud() {}

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) return;
        if (!HudConfig.damageNumbers() || !ClientModules.allows(ModuleId.DAMAGE_INDICATOR)) return;

        float scale = UiScale.push(graphics);
        try {
            render(graphics, minecraft, scale);
        } finally {
            UiScale.pop(graphics);
        }
    };

    private static void render(GuiGraphics graphics, Minecraft minecraft, float scale) {
        float size = BASE_SCALE * HudConfig.damageScale();
        for (DamageNumber number : DamageNumbers.all()) {
            if (number.shown()) draw(graphics, minecraft, number, scale, size);
        }
    }

    private static void draw(GuiGraphics graphics, Minecraft minecraft, DamageNumber number,
                             float scale, float size) {
        float progress = number.progress();
        float alpha = fade(progress);
        if (alpha <= GONE) return;

        float travel = UiAnim.easeOut(progress);
        float emphasis = number.crit() ? CRIT_SCALE : 1.0f;
        float textScale = size * emphasis * distanceSize(number.distance()) * pop(number.sinceHit());
        float x = number.screenX() / scale + number.drift() * travel;
        float y = number.screenY() / scale - RISE * travel - minecraft.font.lineHeight * textScale / 2.0f;
        int color = number.crit() ? UiWorldPalette.damageCrit() : UiWorldPalette.damage();

        UiRender.textCentered(graphics, minecraft.font, number.text(), x, y, textScale,
                UiTheme.withAlpha(color, alpha * alphaOf(color)), false);
    }

    // WHY: толчок идёт от последнего удара, а не от рождения цифры: сложенная цифра на каждом
    // WHY: новом попадании снова вздрагивает, и по ней видно, что урон дошёл
    private static float pop(float sinceHit) {
        float t = UiAnim.easeOutBack(UiAnim.clamp01(sinceHit / POP_SECONDS));
        return POP_FLOOR + (1.0f - POP_FLOOR) * t;
    }

    private static float fade(float progress) {
        if (progress < FADE_FROM) return 1.0f;
        return 1.0f - UiAnim.smoothstep(FADE_FROM, 1.0f, progress);
    }

    private static float distanceSize(float distance) {
        float t = UiAnim.clamp01((distance - NEAR_BLOCKS) / (FAR_BLOCKS - NEAR_BLOCKS));
        return NEAR_SIZE + (FAR_SIZE - NEAR_SIZE) * t;
    }

    private static float alphaOf(int argb) {
        return ((argb >>> 24) & 0xFF) / 255.0f;
    }
}
