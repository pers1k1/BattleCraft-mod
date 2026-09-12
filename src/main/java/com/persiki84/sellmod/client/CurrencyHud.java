package com.persiki84.sellmod.client;

import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.mojang.blaze3d.systems.RenderSystem;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Toggle;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import com.persiki84.battlecraft.client.ClientModules;
import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.battlecraft.modules.ModuleId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public final class CurrencyHud {
    private static final float HEIGHT = 22.0f;
    private static final float PADDING = 6.0f;
    private static final float ICON = 14.0f;
    private static final float ICON_GAP = 5.0f;
    private static final String SAMPLE_BALANCE = "1 250";
    private static final float VALUE_SCALE = 1.0f;
    private static final float PENDING_SCALE = 0.8f;
    private static final float PENDING_GAP = 6.0f;
    private static final float SLIDE_TRAVEL = 20.0f;
    private static final long GAIN_FLASH_MS = 1200L;

    private static final Toggle visibility = new Toggle(9.0f, 150L);
    private static final Smooth gain = new Smooth(0.0f, 4.5f);
    private static final Smooth width = new Smooth(16.0f);

    private static ItemStack icon = ItemStack.EMPTY;
    private static String balanceText = "";
    private static String pendingText = "";
    private static String gainText = "";
    private static int shownBalance = -1;
    private static int shownPending = -1;
    private static int lastBalance = -1;
    private static int heldBalance;
    private static int heldPending;
    private static long gainAt;

    private CurrencyHud() {}

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) return;
        if (!ClientModules.allows(ModuleId.SELL) || !HudLayout.visible(HudSlot.CURRENCY)) return;

        float scale = UiScale.push(graphics);
        try {
            render(graphics, mc, mc.player, screenWidth / scale, screenHeight / scale);
        } finally {
            UiScale.pop(graphics);
        }
    };

    private static void render(GuiGraphics graphics, Minecraft mc, LocalPlayer player, float screenWidth, float screenHeight) {
        float delta = UiFrame.delta();
        float alpha = track(mc, player, delta);
        if (alpha <= 0.01f) return;

        float flash = gain.to(0.0f, delta);
        float textWidth = balanceText.isEmpty() ? 0.0f : UiRender.width(mc.font, balanceText) * VALUE_SCALE;
        float pendingWidth = pendingText.isEmpty() ? 0.0f : UiRender.width(mc.font, pendingText) * PENDING_SCALE;
        float between = textWidth > 0.0f && pendingWidth > 0.0f ? PENDING_GAP : 0.0f;
        float boxWidth = width.to(PADDING * 2.0f + ICON + ICON_GAP + textWidth + between + pendingWidth, delta);

        float slide = (1.0f - UiAnim.easeOut(alpha)) * SLIDE_TRAVEL;
        HudBox box = HudLayout.place(HudSlot.CURRENCY, boxWidth, HEIGHT, screenWidth, screenHeight);
        float x = box.x() + HudLayout.slideX(HudSlot.CURRENCY, slide);
        float y = box.y();
        float shown = alpha * box.alpha();

        HudLayout.push(graphics, box);
        try {
            paintCard(graphics, mc, x, y, boxWidth, textWidth + between, shown, flash);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    private static void paintCard(GuiGraphics graphics, Minecraft mc, float x, float y, float boxWidth,
                                  float textSpan, float shown, float flash) {
        UiVital.card(graphics, x, y, boxWidth, HEIGHT, UiMetrics.radius(HEIGHT), shown, flash * 0.8f);
        drawIcon(graphics, x + PADDING, y + (HEIGHT - ICON) / 2.0f, shown);
        drawAmounts(graphics, mc, x + PADDING + ICON + ICON_GAP, y, textSpan, shown, flash);
        renderGain(graphics, mc, x + boxWidth / 2.0f, y, shown);
    }

    public static void preview(GuiGraphics graphics, HudBox box, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        String balance = balanceText;
        if (balance.isEmpty()) balanceText = SAMPLE_BALANCE;
        try {
            float textWidth = UiRender.width(mc.font, balanceText) * VALUE_SCALE;
            float span = PADDING * 2.0f + ICON + ICON_GAP + textWidth;
            HudLayout.sample(HudSlot.CURRENCY, span, HEIGHT);
            paintCard(graphics, mc, box.x(), box.y(), span, textWidth, alpha, 0.0f);
        } finally {
            balanceText = balance;
        }
    }

    private static float track(Minecraft mc, LocalPlayer player, float delta) {
        int balanceNow = ClientSellData.known() ? ClientSellData.balance(player) : 0;
        int pendingNow = ClientSellData.known() ? ClientSellData.pending(player) : 0;
        boolean wanted = ClientSellData.known() && !(mc.screen instanceof ChatScreen)
                && (balanceNow > 0 || pendingNow > 0);
        float alpha = visibility.update(wanted, delta);

        if (visibility.live()) {
            heldBalance = balanceNow;
            heldPending = pendingNow;
            catchGain();
            relabel();
        }
        lastBalance = balanceNow;

        if (alpha <= 0.01f && visibility.cleared()) {
            forget();
        }
        return alpha;
    }

    private static void catchGain() {
        if (lastBalance < 0 || heldBalance <= lastBalance) return;
        gainText = "+" + (heldBalance - lastBalance);
        gainAt = System.currentTimeMillis();
        gain.snap(1.0f);
    }

    private static void relabel() {
        if (heldBalance != shownBalance) {
            shownBalance = heldBalance;
            balanceText = heldBalance > 0 ? ClientSellData.format(heldBalance) : "";
        }
        if (heldPending != shownPending) {
            shownPending = heldPending;
            pendingText = heldPending > 0 ? "+" + ClientSellData.format(heldPending) : "";
        }
        Item currency = ClientSellData.currency();
        if (!icon.is(currency)) {
            icon = new ItemStack(currency);
        }
    }

    private static void forget() {
        heldBalance = 0;
        heldPending = 0;
        gainText = "";
        gain.snap(0.0f);
        relabel();
    }

    private static void drawIcon(GuiGraphics graphics, float x, float y, float alpha) {
        if (icon.isEmpty()) return;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(ICON / 16.0f, ICON / 16.0f, 1.0f);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        graphics.renderItem(icon, 0, 0);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.pose().popPose();
    }

    private static void drawAmounts(GuiGraphics graphics, Minecraft mc, float textX, float y,
                                    float pendingOffset, float alpha, float flash) {
        if (!balanceText.isEmpty()) {
            UiRender.labelScaled(graphics, mc.font, balanceText, textX,
                    UiRender.centerY(y, HEIGHT, VALUE_SCALE), VALUE_SCALE,
                    UiTheme.alpha(UiTheme.mix(HudInk.text(), HudLayout.tint(HudSlot.CURRENCY, UiAccent.color()), flash), alpha));
        }
        if (!pendingText.isEmpty()) {
            UiRender.labelScaled(graphics, mc.font, pendingText, textX + pendingOffset,
                    UiRender.centerY(y, HEIGHT, PENDING_SCALE), PENDING_SCALE,
                    UiTheme.alpha(HudInk.textFaint(), alpha));
        }
    }

    private static void renderGain(GuiGraphics graphics, Minecraft mc, float centerX, float panelY, float alpha) {
        long elapsed = System.currentTimeMillis() - gainAt;
        if (gainAt <= 0L || elapsed > GAIN_FLASH_MS || gainText.isEmpty()) return;

        float progress = elapsed / (float) GAIN_FLASH_MS;
        float fade = UiAnim.clamp01(1.0f - progress * progress) * alpha;
        float drift = UiAnim.easeOut(progress) * 14.0f;

        UiRender.labelCentered(graphics, mc.font, gainText, centerX, panelY - 10.0f - drift, 1.1f,
                UiTheme.alpha(HudLayout.tint(HudSlot.CURRENCY, UiAccent.color()), fade));
    }
}
