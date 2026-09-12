package com.persiki84.knockdown.client;

import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.knockdown.cap.KnockdownCapability;
import com.persiki84.knockdown.cap.KnockdownProvider;
import com.persiki84.knockdown.config.KnockdownConfig;
import com.persiki84.knockdown.item.ModItems;
import com.persiki84.shared.client.ui.KeyLabel;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Toggle;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import com.persiki84.battlecraft.client.ClientModules;
import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.battlecraft.client.hud.SharpHud;
import com.persiki84.battlecraft.modules.ModuleId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class KnockdownHud {
    private static final float BAR_WIDTH = 120.0f;
    private static final float BAR_HEIGHT = UiMetrics.BAR_HEIGHT;
    private static final float LABEL_SCALE = 0.85f;
    private static final float PROMPT_SCALE = 0.8f;
    private static final float CARD_PADDING = UiMetrics.PAD;
    private static final float LABEL_ROOM = 12.0f;
    private static final int SAMPLE_SECONDS = 24;
    private static final float SAMPLE_PROGRESS = 0.62f;
    private static final float CARD_GAP = UiMetrics.GAP;
    private static final float PROMPT_PADDING = UiMetrics.PAD_WIDE;
    private static final float PROMPT_STEP = UiMetrics.MARGIN_WIDE;
    private static final float SECOND_GAP = UiMetrics.MARGIN_WIDE;

    private static final Smooth bleedProgress = new Smooth(12.0f);
    private static final Smooth actionProgress = new Smooth(14.0f);
    private static final Smooth targetProgress = new Smooth(14.0f);
    private static final Toggle bleedToggle = new Toggle(7.0f, 150L);
    private static final Toggle actionToggle = new Toggle(9.0f, 150L);
    private static final Toggle targetToggle = new Toggle(9.0f, 150L);

    private static int heldBleedSeconds;
    private static Component heldActionLabel;
    private static int heldRevivePercent;
    private static boolean heldTargetReviving;

    private KnockdownHud() {}

    public static final SharpHud.Layer LAYER = graphics -> {
        Minecraft mc = Minecraft.getInstance();
        float scale = UiScale.push(graphics);
        try {
            render(graphics, mc.getWindow().getGuiScaledWidth() / scale,
                    mc.getWindow().getGuiScaledHeight() / scale);
        } finally {
            UiScale.pop(graphics);
        }
    };

    private static void render(GuiGraphics graphics, float screenWidth, float screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!ClientModules.allows(ModuleId.KNOCKDOWN)) return;
        if (!HudLayout.visible(HudSlot.KNOCKDOWN)) return;

        float delta = UiFrame.delta();
        KnockdownCapability own = mc.player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).orElse(null);
        float alpha = bleedToggle.update(own != null && own.isKnocked(), delta);

        if (alpha > 0.01f && own != null) {
            targetToggle.update(false, delta);
            renderSelf(graphics, mc, own, screenWidth, screenHeight, alpha, delta);
            return;
        }

        bleedProgress.snap(0.0f);
        actionProgress.snap(0.0f);
        actionToggle.update(false, delta);
        renderTarget(graphics, mc, screenWidth, screenHeight, delta);
    }

    private static void renderSelf(GuiGraphics graphics, Minecraft mc, KnockdownCapability cap,
                                   float screenWidth, float screenHeight, float alpha, float delta) {
        trackBleeding(cap, delta);

        float labelScale = UiRender.crisp(graphics, LABEL_SCALE);
        Component bleeding = Component.translatable("knockdown.hud.bleeding", heldBleedSeconds);
        float width = cardWidth(mc, bleeding, labelScale);
        float height = cardHeight(mc, labelScale);
        HudBox box = HudLayout.place(HudSlot.KNOCKDOWN, width, height, screenWidth, screenHeight);

        HudLayout.push(graphics, box);
        try {
            float pulse = UiAnim.pulse(900.0f, 0.55f, 1.0f);
            card(graphics, mc, bleeding, bleedProgress.get(),
                    UiTheme.mix(UiAccent.color(), UiPalette.alert(), 0.65f),
                    box.x(), box.y(), width, labelScale, alpha, alpha * pulse);
            renderAction(graphics, mc, cap, box, labelScale, alpha, delta);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    private static void trackBleeding(KnockdownCapability cap, float delta) {
        if (!bleedToggle.live()) return;

        int maxTimer = Math.max(1, KnockdownConfig.BLEED_TIME_SECONDS.get() * 20);
        bleedProgress.to(cap.getDeathTimer() / (float) maxTimer, delta);
        heldBleedSeconds = cap.getDeathTimer() / 20;
    }

    private static void renderAction(GuiGraphics graphics, Minecraft mc, KnockdownCapability cap, HudBox box,
                                     float labelScale, float alpha, float delta) {
        Component label = actionLabel(cap);
        float shown = actionToggle.update(label != null, delta);
        if (label != null) {
            heldActionLabel = label;
        }

        if (shown > 0.01f && heldActionLabel != null) {
            float value = actionToggle.live() ? actionProgress.to(actionValue(cap), delta) : actionProgress.get();
            float width = cardWidth(mc, heldActionLabel, labelScale);
            float height = cardHeight(mc, labelScale);
            card(graphics, mc, heldActionLabel, value, UiAccent.color(),
                    box.localCenterX() - width / 2.0f, box.y() - SECOND_GAP - height, width, labelScale, shown, shown);
            return;
        }

        actionProgress.snap(0.0f);
        renderPrompts(graphics, mc, box.localCenterX(), box.y() - SECOND_GAP, alpha);
    }

    private static Component actionLabel(KnockdownCapability cap) {
        if (cap.getReviveProgress() > 0) {
            return Component.translatable(cap.isSelfReviving()
                    ? "knockdown.hud.injector_using"
                    : "knockdown.hud.being_revived");
        }
        if (cap.getSurrenderProgress() > 0) return Component.translatable("knockdown.action.surrender");
        return null;
    }

    private static float actionValue(KnockdownCapability cap) {
        if (cap.getReviveProgress() > 0) return cap.getReviveProgress() / 100.0f;
        if (cap.getSurrenderProgress() > 0) return cap.getSurrenderProgress() / 100.0f;
        return 0.0f;
    }

    private static void renderPrompts(GuiGraphics graphics, Minecraft mc, float centerX, float bottom, float alpha) {
        float scale = UiRender.crisp(graphics, PROMPT_SCALE);
        float height = promptHeight(mc, scale);

        Component surrender = Component.translatable("knockdown.prompt.surrender",
                KeyLabel.of(KeyInit.SURRENDER_KEY));
        prompt(graphics, mc, surrender, centerX, bottom - height, scale, height, alpha);

        if (!hasInjector(mc)) return;

        Component revive = Component.translatable("knockdown.prompt.revive_injector",
                KeyLabel.of(KeyInit.REVIVE_KEY));
        prompt(graphics, mc, revive, centerX, bottom - height - PROMPT_STEP, scale, height, alpha);
    }

    private static void renderTarget(GuiGraphics graphics, Minecraft mc, float screenWidth, float screenHeight,
                                     float delta) {
        Player found = knockedTarget(mc);
        float alpha = targetToggle.update(found != null, delta);
        if (alpha <= 0.01f) {
            targetProgress.snap(0.0f);
            heldTargetReviving = false;
            return;
        }

        if (found != null) {
            trackTarget(found, delta);
        }
        if (heldTargetReviving) {
            renderTargetCard(graphics, mc, screenWidth, screenHeight, alpha);
            return;
        }
        renderTargetPrompt(graphics, mc, screenWidth, screenHeight, alpha);
    }

    private static Player knockedTarget(Minecraft mc) {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.ENTITY) return null;
        if (!(((EntityHitResult) mc.hitResult).getEntity() instanceof Player hit)) return null;

        boolean knocked = hit.getCapability(KnockdownProvider.KNOCKDOWN_CAP)
                .map(KnockdownCapability::isKnocked).orElse(false);
        return knocked ? hit : null;
    }

    private static void trackTarget(Player found, float delta) {
        KnockdownCapability cap = found.getCapability(KnockdownProvider.KNOCKDOWN_CAP).orElse(null);
        heldTargetReviving = cap != null && cap.getReviveProgress() > 0 && !cap.isSelfReviving();
        if (!heldTargetReviving) {
            targetProgress.snap(0.0f);
            return;
        }

        heldRevivePercent = (int) cap.getReviveProgress();
        targetProgress.to(cap.getReviveProgress() / 100.0f, delta);
    }

    private static void renderTargetCard(GuiGraphics graphics, Minecraft mc, float screenWidth,
                                         float screenHeight, float alpha) {
        float labelScale = UiRender.crisp(graphics, LABEL_SCALE);
        Component label = Component.translatable("knockdown.hud.revive_progress", heldRevivePercent);
        float width = cardWidth(mc, label, labelScale);
        HudBox box = HudLayout.place(HudSlot.KNOCKDOWN, width, cardHeight(mc, labelScale),
                screenWidth, screenHeight);

        HudLayout.push(graphics, box);
        try {
            card(graphics, mc, label, targetProgress.get(), UiAccent.color(),
                    box.x(), box.y(), width, labelScale, alpha, alpha);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    private static void renderTargetPrompt(GuiGraphics graphics, Minecraft mc, float screenWidth,
                                           float screenHeight, float alpha) {
        float scale = UiRender.crisp(graphics, PROMPT_SCALE);
        Component text = Component.translatable("knockdown.prompt.revive", KeyLabel.of(KeyInit.REVIVE_KEY));
        float height = promptHeight(mc, scale);
        HudBox box = HudLayout.place(HudSlot.KNOCKDOWN, promptWidth(mc, text, scale), height,
                screenWidth, screenHeight);

        HudLayout.push(graphics, box);
        try {
            prompt(graphics, mc, text, box.localCenterX(), box.y(), scale, height, alpha);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    public static void preview(GuiGraphics graphics, HudBox box, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        float labelScale = UiRender.crisp(graphics, LABEL_SCALE);
        Component bleeding = Component.translatable("knockdown.hud.bleeding", SAMPLE_SECONDS);
        float width = cardWidth(mc, bleeding, labelScale);

        HudLayout.sample(HudSlot.KNOCKDOWN, width, cardHeight(mc, labelScale));
        card(graphics, mc, bleeding, SAMPLE_PROGRESS,
                UiTheme.mix(UiAccent.color(), UiPalette.alert(), 0.65f),
                box.x(), box.y(), width, labelScale, alpha, alpha);
    }

    private static float cardWidth(Minecraft mc, Component label, float labelScale) {
        return barWidth(mc, label, labelScale) + CARD_PADDING * 2.0f;
    }

    private static float barWidth(Minecraft mc, Component label, float labelScale) {
        return Math.max(BAR_WIDTH, UiRender.width(mc.font, label) * labelScale + LABEL_ROOM);
    }

    private static float cardHeight(Minecraft mc, float labelScale) {
        return mc.font.lineHeight * labelScale + BAR_HEIGHT + CARD_PADDING * 2.0f + CARD_GAP;
    }

    private static void card(GuiGraphics graphics, Minecraft mc, Component label, float value, int color,
                             float cardX, float cardY, float cardWidth, float labelScale,
                             float alpha, float fillAlpha) {
        float cardHeight = cardHeight(mc, labelScale);
        UiVital.card(graphics, cardX, cardY, cardWidth, cardHeight, UiMetrics.radius(cardHeight), alpha);
        UiRender.textCentered(graphics, mc.font, label, cardX + cardWidth / 2.0f, cardY + CARD_PADDING,
                labelScale, UiTheme.alpha(HudInk.text(), alpha), false);

        float barX = cardX + CARD_PADDING;
        float barY = cardY + cardHeight - CARD_PADDING - BAR_HEIGHT;
        float span = cardWidth - CARD_PADDING * 2.0f;
        UiGlass.sunken(graphics, barX, barY, span, BAR_HEIGHT, BAR_HEIGHT / 2.0f, alpha);
        UiGlass.progress(graphics, barX, barY, span, BAR_HEIGHT, value, color, fillAlpha);
    }

    private static void prompt(GuiGraphics graphics, Minecraft mc, Component text, float centerX, float y,
                               float scale, float height, float alpha) {
        float width = promptWidth(mc, text, scale);
        UiVital.card(graphics, centerX - width / 2.0f, y, width, height, UiMetrics.radius(height), alpha * 0.92f);
        UiRender.textCentered(graphics, mc.font, text, centerX, UiRender.centerY(y, height, scale), scale,
                UiTheme.alpha(HudInk.text(), alpha), false);
    }

    private static float promptWidth(Minecraft mc, Component text, float scale) {
        return UiRender.width(mc.font, text) * scale + PROMPT_PADDING * 2.0f;
    }

    private static float promptHeight(Minecraft mc, float scale) {
        return UiMetrics.snap(mc.font.lineHeight * scale + UiMetrics.GAP_WIDE);
    }

    private static boolean hasInjector(Minecraft mc) {
        for (ItemStack stack : mc.player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == ModItems.INJECTOR.get()) {
                return true;
            }
        }
        return mc.player.getOffhandItem().getItem() == ModItems.INJECTOR.get();
    }
}
