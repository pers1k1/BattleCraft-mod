package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.PaletteKey;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiTicker;
import com.persiki84.shared.client.ui.UiWorldPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// WHY: цвета мира и индикатор урона показываются тем, чем они станут в игре: метками зон и цифрами,
// WHY: всплывающими над целью. Цифры идут той же кривой подъёма и угасания, что у DamageHud, и
// WHY: подчиняются тем же ручкам размера, времени и сложения
final class PreviewWorld {
    private static final float TAG_HEIGHT = 18.0f;
    private static final float TAG_GAP = 6.0f;
    private static final float TAG_SCALE = 0.8f;
    private static final float RING_RADIUS = 4.2f;
    private static final float SCENE_HEIGHT = 120.0f;
    private static final float DAMAGE_SCALE = 1.05f;
    private static final float CRIT_SCALE = 1.3f;
    private static final float RISE = 22.0f;
    private static final float FADE_FROM = 0.62f;
    private static final float LIFE_MS = 1100.0f;
    private static final float POP_SECONDS = 0.22f;
    private static final float POP_FLOOR = 0.55f;
    private static final int HITS = 3;
    private static final long[] HIT_DAMAGE = {7L, 12L, 5L};
    private static final String[] HIT_TEXT = {"7", "12", "5"};
    private static final float[] HIT_DRIFT = {-26.0f, 4.0f, 30.0f};
    private static final PaletteKey[] ZONES = {PaletteKey.ZONE_FRIENDLY, PaletteKey.ZONE_HOSTILE,
            PaletteKey.ZONE_SHOP, PaletteKey.ZONE_CONTESTED, PaletteKey.MARK};
    private static final Component[] ZONE_NAMES = names(ZONES);
    private static final Component DAMAGE = Component.translatable(PaletteKey.DAMAGE.translationKey());
    private static final Component CRIT = Component.translatable(PaletteKey.DAMAGE_CRIT.translationKey());
    private static final Component OFF = Component.translatable("battlecraft.custom.preview.off");

    private final UiTicker stacked = new UiTicker().align(UiTicker.CENTER);
    private long stackedSum = -1L;

    private static Component[] names(PaletteKey[] keys) {
        Component[] built = new Component[keys.length];
        for (int index = 0; index < keys.length; index++) {
            built[index] = Component.translatable(keys[index].translationKey());
        }
        return built;
    }

    float zones(GuiGraphics graphics, float x, float y, float width, PreviewHits hits) {
        Font font = Minecraft.getInstance().font;
        float cursor = y;
        for (int index = 0; index < ZONES.length; index++) {
            paintTag(graphics, font, ZONE_NAMES[index], colorOf(ZONES[index]), x, cursor, width);
            hits.add(x, cursor, width, TAG_HEIGHT, ZONES[index]);
            cursor += TAG_HEIGHT + TAG_GAP;
        }
        return cursor;
    }

    private static int colorOf(PaletteKey key) {
        return switch (key) {
            case ZONE_FRIENDLY -> UiWorldPalette.friendly();
            case ZONE_HOSTILE -> UiWorldPalette.hostile();
            case ZONE_SHOP -> UiWorldPalette.shop();
            case ZONE_CONTESTED -> UiWorldPalette.contested();
            default -> UiWorldPalette.mark();
        };
    }

    private static void paintTag(GuiGraphics graphics, Font font, Component name, int color, float x, float y,
                                 float width) {
        UiGlass.panel(graphics, x, y, width, TAG_HEIGHT, UiMetrics.radius(TAG_HEIGHT), 1.0f, 0.15f);
        float ringX = x + TAG_HEIGHT / 2.0f + 2.0f;
        UiRender.dot(graphics, ringX, y + TAG_HEIGHT / 2.0f, RING_RADIUS, UiTheme.withAlpha(color, 0.9f));
        UiRender.ring(graphics, ringX, y + TAG_HEIGHT / 2.0f, RING_RADIUS + 2.0f, 1.0f, 1.0f,
                UiTheme.withAlpha(color, 0.6f));
        UiRender.textTrackedLeft(graphics, font, name, ringX + RING_RADIUS + 8.0f,
                UiRender.centerY(y, TAG_HEIGHT, TAG_SCALE), TAG_SCALE, 0.2f, UiAccent.text());
    }

    float damage(GuiGraphics graphics, float x, float y, float width, PreviewHits hits) {
        Font font = Minecraft.getInstance().font;
        UiGlass.sunken(graphics, x, y, width, SCENE_HEIGHT, UiMetrics.radius(SCENE_HEIGHT * 0.2f), 1.0f);
        float centerX = x + width / 2.0f;
        float baseY = y + SCENE_HEIGHT * 0.72f;
        if (!HudConfig.damageNumbers()) {
            UiRender.textCentered(graphics, font, OFF, centerX, UiRender.centerY(y, SCENE_HEIGHT, 0.9f), 0.9f,
                    UiAccent.textFaint(), false);
        } else if (HudConfig.damageStack()) {
            paintStacked(graphics, font, centerX, baseY);
        } else {
            paintSeparate(graphics, font, centerX, baseY);
        }
        return paintLegend(graphics, font, x, y + SCENE_HEIGHT + TAG_GAP, width, hits);
    }

    private static float lifeMs() {
        return LIFE_MS * HudConfig.damageTime();
    }

    private static float size() {
        return DAMAGE_SCALE * HudConfig.damageScale();
    }

    private void paintSeparate(GuiGraphics graphics, Font font, float centerX, float baseY) {
        float cycle = lifeMs() * 1.5f;
        long now = System.currentTimeMillis();
        for (int hit = 0; hit < HITS; hit++) {
            float age = ((now + (long) (hit * cycle / HITS)) % (long) cycle) / lifeMs();
            if (age >= 1.0f) continue;
            boolean crit = hit == 1;
            float scale = size() * (crit ? CRIT_SCALE : 1.0f) * pop(age * lifeMs() / 1000.0f);
            float y = baseY - RISE * UiAnim.easeOut(age) - font.lineHeight * scale / 2.0f;
            int color = crit ? UiWorldPalette.damageCrit() : UiWorldPalette.damage();
            UiRender.textCentered(graphics, font, HIT_TEXT[hit], centerX + HIT_DRIFT[hit] * UiAnim.easeOut(age), y,
                    scale, UiTheme.withAlpha(color, fade(age) * alphaOf(color)), false);
        }
    }

    // WHY: со сложением удары по одной цели копятся в одну цифру: она перекатывается к новой сумме
    // WHY: и вздрагивает на каждом ударе, а без сложения каждая цифра живёт отдельно
    private void paintStacked(GuiGraphics graphics, Font font, float centerX, float baseY) {
        float cycle = lifeMs() * 2.2f;
        float age = (System.currentTimeMillis() % (long) cycle) / cycle;
        int landed = Math.min(HITS, (int) (age * HITS * 1.6f) + 1);
        long sum = 0L;
        for (int hit = 0; hit < landed; hit++) {
            sum += HIT_DAMAGE[hit];
        }
        if (sum < stackedSum) stacked.snap(sum);
        stacked.set(sum);
        stackedSum = sum;
        float sinceHit = (age * HITS * 1.6f % 1.0f) * cycle / HITS / 1.6f / 1000.0f;
        float scale = size() * pop(sinceHit);
        float fadeAge = UiAnim.clamp01((age - 0.62f) / 0.38f);
        int color = UiWorldPalette.damage();
        stacked.draw(graphics, font, centerX, baseY - RISE * 0.4f - font.lineHeight * scale / 2.0f, scale,
                UiTheme.withAlpha(color, (1.0f - UiAnim.smoothstep(0.0f, 1.0f, fadeAge)) * alphaOf(color)));
    }

    private float paintLegend(GuiGraphics graphics, Font font, float x, float y, float width, PreviewHits hits) {
        float half = (width - TAG_GAP) / 2.0f;
        paintTag(graphics, font, DAMAGE, UiWorldPalette.damage(), x, y, half);
        hits.add(x, y, half, TAG_HEIGHT, PaletteKey.DAMAGE);
        paintTag(graphics, font, CRIT, UiWorldPalette.damageCrit(), x + half + TAG_GAP, y, half);
        hits.add(x + half + TAG_GAP, y, half, TAG_HEIGHT, PaletteKey.DAMAGE_CRIT);
        return y + TAG_HEIGHT + TAG_GAP;
    }

    private static float pop(float sinceHit) {
        float t = UiAnim.easeOutBack(UiAnim.clamp01(sinceHit / POP_SECONDS));
        return POP_FLOOR + (1.0f - POP_FLOOR) * t;
    }

    private static float fade(float progress) {
        if (progress < FADE_FROM) return 1.0f;
        return 1.0f - UiAnim.smoothstep(FADE_FROM, 1.0f, progress);
    }

    // WHY: цвет урона может прийти с прозрачностью из палитры, и её надо сохранить поверх угасания
    private static float alphaOf(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        return alpha == 0 ? 1.0f : alpha / 255.0f;
    }
}
