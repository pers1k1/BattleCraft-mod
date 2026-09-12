package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class BossBarHud {
    private static final int BAR_WIDTH = 182;
    private static final float BAR_HEIGHT = UiMetrics.BAR_HEIGHT;
    private static final float PANEL_PADDING = UiMetrics.PAD;
    private static final float LABEL_SCALE = 1.0f;
    private static final float LABEL_TRACKING = 0.3f;
    private static final float LABEL_HEIGHT = 9.0f;
    private static final float LABEL_GAP = UiMetrics.GAP;
    private static final float SLIDE = UiMetrics.PAD_WIDE;
    private static final int ROW_INCREMENT = 36;

    private static final Map<UUID, Bar> bars = new HashMap<>();
    private static final Smooth blockHeight = new Smooth(0.0f, 10.0f);
    private static float frameBottom;

    private BossBarHud() {}

    public static float bottom() {
        return blockHeight.get();
    }

    public static void beginFrame(float delta) {
        blockHeight.to(frameBottom, delta);
        frameBottom = 0.0f;

        for (Bar bar : bars.values()) {
            bar.seen = false;
        }
    }

    @SubscribeEvent
    public static void onBossBarRender(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (!HudConfig.bossBars()) return;

        Minecraft mc = Minecraft.getInstance();
        float scale = UiScale.factor();

        event.setCanceled(true);
        event.setIncrement(Math.round(ROW_INCREMENT * scale));

        if (mc.options.hideGui) return;

        UUID id = event.getBossEvent().getId();
        Bar bar = bars.computeIfAbsent(id, key -> new Bar());
        bar.seen = true;
        bar.name = event.getBossEvent().getName();
        bar.target = event.getBossEvent().getProgress();
        bar.alert = event.getBossEvent().getColor() == BossEvent.BossBarColor.RED;
        bar.top = event.getY() / scale;

        draw(event.getGuiGraphics(), mc, bar, scale, UiFrame.delta(), true);
    }

    @SubscribeEvent
    public static void onGuiPost(RenderGuiEvent.Post event) {
        if (bars.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        float delta = UiFrame.delta();
        float scale = UiScale.factor();

        Iterator<Bar> iterator = bars.values().iterator();
        while (iterator.hasNext()) {
            Bar bar = iterator.next();
            if (bar.seen) continue;
            if (bar.alpha.get() <= 0.01f || bar.name == null || mc.options.hideGui) {
                iterator.remove();
                continue;
            }
            draw(event.getGuiGraphics(), mc, bar, scale, delta, false);
        }
    }

    private static void draw(GuiGraphics graphics, Minecraft mc, Bar bar, float scale, float delta, boolean live) {
        float alpha = bar.alpha.to(live ? 1.0f : 0.0f, delta);
        if (alpha <= 0.01f) return;

        float value = live ? bar.progress.to(bar.target, delta) : bar.progress.get();
        float centerX = mc.getWindow().getGuiScaledWidth() / scale / 2.0f;
        float top = bar.top - (1.0f - UiAnim.easeOut(alpha)) * SLIDE;
        float content = Math.max(BAR_WIDTH, UiRender.width(mc.font, bar.name) * LABEL_SCALE + LABEL_TRACKING * 4.0f);
        float panelHeight = PANEL_PADDING * 2.0f + LABEL_HEIGHT + LABEL_GAP + BAR_HEIGHT;

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0f);
        paint(graphics, mc, bar, centerX, top, content, panelHeight, alpha, value);
        graphics.pose().popPose();

        if (live) {
            frameBottom = Math.max(frameBottom, (bar.top + panelHeight + 5.0f) * scale);
        }
    }

    private static void paint(GuiGraphics graphics, Minecraft mc, Bar bar, float centerX, float top,
                              float content, float panelHeight, float alpha, float value) {
        float pulse = bar.alert ? UiAnim.pulse(1300.0f, 0.72f, 1.0f) : 1.0f;
        int accent = bar.alert ? UiTheme.mix(UiPalette.alertDim(), UiPalette.alert(), pulse) : UiAccent.color();
        float panelWidth = content + PANEL_PADDING * 2.0f;
        float panelX = centerX - panelWidth / 2.0f;

        if (bar.alert) {
            UiVital.cardTinted(graphics, panelX, top, panelWidth, panelHeight, UiMetrics.radius(panelHeight), alpha,
                    0.12f * pulse, UiTheme.withAlpha(UiPalette.alert(), 0.16f + 0.08f * pulse));
        } else {
            UiVital.card(graphics, panelX, top, panelWidth, panelHeight, UiMetrics.radius(panelHeight), alpha);
        }

        UiRender.textTracked(graphics, mc.font, bar.name, centerX,
                UiRender.centerY(top + PANEL_PADDING, LABEL_HEIGHT, LABEL_SCALE), LABEL_SCALE, LABEL_TRACKING,
                UiTheme.alpha(HudInk.text(), alpha));

        float barX = centerX - content / 2.0f;
        float barY = top + PANEL_PADDING + LABEL_HEIGHT + LABEL_GAP;
        UiGlass.sunken(graphics, barX, barY, content, BAR_HEIGHT, BAR_HEIGHT / 2.0f, alpha);
        UiGlass.progress(graphics, barX, barY, content, BAR_HEIGHT, value, accent, alpha);
    }

    private static final class Bar {
        private final Smooth alpha = new Smooth(0.0f, 9.0f);
        private final Smooth progress = new Smooth(9.0f);
        private Component name;
        private float target;
        private float top;
        private boolean alert;
        private boolean seen;
    }
}
