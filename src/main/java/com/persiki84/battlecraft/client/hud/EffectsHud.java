package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.mojang.blaze3d.systems.RenderSystem;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EffectsHud {
    private static final int CHIP = 20;
    private static final int CHIP_GAP = 4;
    private static final int MARGIN = 6;
    private static final int PER_ROW = 6;
    private static final int LABEL_BLOCK = 8;
    private static final int ROW_HEIGHT = CHIP + CHIP_GAP + 7;

    private static final int SAMPLE_DURATION = 1200;
    private static final MobEffectInstance[] SAMPLES = {
            new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SAMPLE_DURATION),
            new MobEffectInstance(MobEffects.REGENERATION, SAMPLE_DURATION),
            new MobEffectInstance(MobEffects.DAMAGE_BOOST, SAMPLE_DURATION)
    };
    private static final Chip sample = new Chip();

    private static final Map<MobEffect, Chip> chips = new LinkedHashMap<>();

    private EffectsHud() {}

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        float scale = UiScale.push(graphics);
        try {
            render(graphics, screenWidth / scale, screenHeight / scale);
        } finally {
            UiScale.pop(graphics);
        }
    };

    private static void render(GuiGraphics graphics, float screenWidth, float screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !HudConfig.effectChips()
                || !HudLayout.visible(HudSlot.EFFECTS)) {
            chips.clear();
            return;
        }

        float delta = UiFrame.delta();
        for (Chip chip : chips.values()) {
            chip.present = false;
        }

        // WHY: пустой слот всё равно кормил решателя боксом на шесть фишек и расталкивал соседей
        // WHY: невидимой площадью, поэтому габарит считается по тому, что реально будет нарисовано
        int shown = Math.max(countVisible(mc), chips.size());
        if (shown == 0) {
            chips.clear();
            return;
        }

        float top = com.persiki84.minimap.client.MinimapOverlay.hudBottom() + MARGIN;
        HudBox field = HudLayout.placeBelow(HudSlot.EFFECTS, rowWidth(shown), stackHeight(shown),
                screenWidth, screenHeight, top);
        float right = field.x() + field.width();
        top = field.y();
        int index = 0;

        for (MobEffectInstance instance : mc.player.getActiveEffects()) {
            if (!instance.showIcon()) continue;
            if (!IClientMobEffectExtensions.of(instance).isVisibleInGui(instance)) continue;

            Chip chip = chips.get(instance.getEffect());
            boolean fresh = chip == null;
            if (fresh) {
                chip = new Chip();
                chips.put(instance.getEffect(), chip);
            }

            chip.instance = instance;
            chip.present = true;
            chip.targetX = right - CHIP - (index % PER_ROW) * (CHIP + CHIP_GAP);
            chip.targetY = top + (index / PER_ROW) * ROW_HEIGHT;

            if (fresh) {
                chip.x.snap(chip.targetX + 16.0f);
                chip.y.snap(chip.targetY);
                UiSound.chip(true);
            }
            index++;
        }

        HudLayout.push(graphics, field);
        try {
            drawChips(graphics, mc, delta);
        } finally {
            HudLayout.pop(graphics, field);
        }
    }

    private static int countVisible(Minecraft mc) {
        int count = 0;
        for (MobEffectInstance instance : mc.player.getActiveEffects()) {
            if (!instance.showIcon()) continue;
            if (!IClientMobEffectExtensions.of(instance).isVisibleInGui(instance)) continue;

            count++;
        }
        return count;
    }

    private static float rowWidth(int shown) {
        int columns = Math.min(shown, PER_ROW);
        return columns * CHIP + (columns - 1) * CHIP_GAP;
    }

    private static float stackHeight(int shown) {
        int rows = (shown + PER_ROW - 1) / PER_ROW;
        return (rows - 1) * ROW_HEIGHT + CHIP + LABEL_BLOCK;
    }

    private static void drawChips(GuiGraphics graphics, Minecraft mc, float delta) {
        Iterator<Map.Entry<MobEffect, Chip>> iterator = chips.entrySet().iterator();
        while (iterator.hasNext()) {
            Chip chip = iterator.next().getValue();
            float alpha = chip.alpha.to(chip.present ? 1.0f : 0.0f, delta);
            if (!chip.present && alpha <= 0.012f) {
                iterator.remove();
                UiSound.chip(false);
                continue;
            }
            renderChip(graphics, mc, chip, chip.x.to(chip.targetX, delta), chip.y.to(chip.targetY, delta), alpha);
        }
    }

    public static void preview(GuiGraphics graphics, HudBox box, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        HudLayout.sample(HudSlot.EFFECTS, rowWidth(SAMPLES.length), stackHeight(SAMPLES.length));
        float right = box.x() + box.width();
        for (int index = 0; index < SAMPLES.length; index++) {
            sample.instance = SAMPLES[index];
            renderChip(graphics, mc, sample, right - CHIP - index * (CHIP + CHIP_GAP), box.y(), alpha);
        }
    }

    private static void renderChip(GuiGraphics graphics, Minecraft mc, Chip chip, float x, float y, float alpha) {
        MobEffectInstance instance = chip.instance;
        if (instance == null) return;

        int duration = instance.getDuration();
        float blink = duration > 0 && duration <= 100 ? UiAnim.pulse(520.0f, 0.35f, 1.0f) : 1.0f;
        float visible = alpha * blink;
        float appear = UiAnim.easeOut(alpha);

        UiVital.card(graphics, x, y, CHIP, CHIP, UiGlassStyle.radiusCell(), visible, (1.0f - appear) * 0.6f);

        int iconX = (int) x + 1;
        int iconY = (int) y + 1;

        if (!IClientMobEffectExtensions.of(instance).renderGuiIcon(instance, mc.gui, graphics, iconX, iconY, 0.0f, visible)) {
            TextureAtlasSprite sprite = mc.getMobEffectTextures().get(instance.getEffect());
            RenderSystem.enableBlend();
            graphics.setColor(1.0f, 1.0f, 1.0f, visible);
            graphics.blit(iconX, iconY, 0, 18, 18, sprite);
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        if (duration > 0) {
            UiRender.labelCentered(graphics, mc.font, MobEffectUtil.formatDuration(instance, 1.0f),
                    x + CHIP / 2.0f, y + CHIP + 1.0f, 0.7f, UiTheme.alpha(HudInk.text(), alpha));
        }
    }

    private static final class Chip {
        private final Smooth alpha = new Smooth(0.0f, 9.0f);
        private final Smooth x = new Smooth(13.0f);
        private final Smooth y = new Smooth(13.0f);
        private MobEffectInstance instance;
        private boolean present;
        private float targetX;
        private float targetY;
    }
}
