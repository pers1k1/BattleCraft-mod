package com.persiki84.sellmod.client;

import com.persiki84.battlecraft.client.ClientModules;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.battlecraft.client.hud.HudMeasure;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Toggle;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiTicker;
import com.persiki84.shared.client.ui.UiVital;
import com.persiki84.shared.economy.CurrencyMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

// WHY: карточка денег справа снизу одна на оба режима валюты: в «Предмете» сумма считается по
// WHY: инвентарю, в «Счёте» приходит с сервера, а значок берётся у CurrencyIcon. Смена режима не
// WHY: считается приходом денег: скачок суммы при ней это другой счёт, а не заработок. Счёт виден и
// WHY: пустым: это кошелёк игрока, а в «Предмете» пустая карточка значила бы лишь пустой инвентарь
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
    private static final float GAIN_SCALE = 1.1f;
    private static final float GAIN_RISE = 14.0f;
    private static final float GAIN_TOP = 10.0f;
    private static final float FLASH_GLOW = 0.8f;

    private static final UiTicker.Format MONEY = amount -> ClientSellData.format((int) amount);
    private static final UiTicker.Format PENDING = amount -> "+" + ClientSellData.format((int) amount);

    private static final Toggle visibility = new Toggle(9.0f, 150L);
    private static final Smooth gain = new Smooth(0.0f, 4.5f);
    private static final Smooth width = new Smooth(16.0f);
    private static final UiTicker balanceTicker = new UiTicker();
    private static final UiTicker pendingTicker = new UiTicker();
    private static final UiTicker sampleTicker = new UiTicker();

    private static boolean balanceShown;
    private static boolean pendingShown;
    private static Component gainText = Component.empty();
    private static int lastBalance = -1;
    private static CurrencyMode lastMode;
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

    private static void render(GuiGraphics graphics, Minecraft mc, LocalPlayer player, float screenWidth,
                               float screenHeight) {
        float delta = UiFrame.delta();
        float alpha = track(mc, player, delta);
        if (alpha <= 0.01f) return;

        float flash = gain.to(0.0f, delta);
        float textWidth = balanceShown
                ? HudMeasure.steady(graphics, balanceTicker, mc.font, VALUE_SCALE, HudSlot.CURRENCY) : 0.0f;
        float pendingWidth = pendingShown
                ? HudMeasure.steady(graphics, pendingTicker, mc.font, PENDING_SCALE, HudSlot.CURRENCY) : 0.0f;
        float between = textWidth > 0.0f && pendingWidth > 0.0f ? PENDING_GAP : 0.0f;
        float boxWidth = width.to(PADDING * 2.0f + ICON + ICON_GAP + textWidth + between + pendingWidth, delta);

        float slide = (1.0f - UiAnim.easeOut(alpha)) * SLIDE_TRAVEL;
        HudBox box = HudLayout.place(HudSlot.CURRENCY, boxWidth, HEIGHT, screenWidth, screenHeight);
        float x = box.x() + HudLayout.slideX(HudSlot.CURRENCY, slide);
        float shown = alpha * box.alpha();

        HudLayout.push(graphics, box);
        try {
            paintCard(graphics, mc, x, box.y(), boxWidth, textWidth + between, shown, flash,
                    balanceShown ? balanceTicker : null);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    private static void paintCard(GuiGraphics graphics, Minecraft mc, float x, float y, float boxWidth,
                                  float textSpan, float shown, float flash, UiTicker balance) {
        UiVital.card(graphics, x, y, boxWidth, HEIGHT, UiMetrics.radius(HEIGHT), shown, flash * FLASH_GLOW);
        CurrencyIcon.draw(graphics, x + PADDING, y + (HEIGHT - ICON) / 2.0f, ICON, accent(), shown);
        drawAmounts(graphics, mc, x + PADDING + ICON + ICON_GAP, y, textSpan, shown, flash, balance);
        renderGain(graphics, mc, x + boxWidth / 2.0f, y, shown);
    }

    // WHY: образец в редакторе стоит своим тикером: общий с живой карточкой морфил бы образцовую сумму
    // WHY: в настоящую на выходе из редактора
    public static void preview(GuiGraphics graphics, HudBox box, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        UiTicker balance = balanceShown ? balanceTicker : sampleTicker;
        if (!balanceShown) sampleTicker.snap(SAMPLE_BALANCE);
        float textWidth = balance.steadyWidth(graphics, mc.font, VALUE_SCALE);
        float span = PADDING * 2.0f + ICON + ICON_GAP + textWidth;
        HudLayout.sample(HudSlot.CURRENCY, span, HEIGHT);
        boolean pending = pendingShown;
        pendingShown = false;
        try {
            paintCard(graphics, mc, box.x(), box.y(), span, textWidth, alpha, 0.0f, balance);
        } finally {
            pendingShown = pending;
        }
    }

    private static float track(Minecraft mc, LocalPlayer player, float delta) {
        int balanceNow = ClientSellData.known() ? ClientSellData.balance(player) : 0;
        int pendingNow = ClientSellData.known() ? ClientSellData.pending(player) : 0;
        boolean wanted = ClientSellData.known() && !(mc.screen instanceof ChatScreen)
                && (ClientSellData.accounts() || balanceNow > 0 || pendingNow > 0);
        float alpha = visibility.update(wanted, delta);

        if (ClientSellData.mode() != lastMode) {
            lastMode = ClientSellData.mode();
            lastBalance = -1;
            balanceTicker.reset();
            pendingTicker.reset();
        }
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
        int earned = heldBalance - lastBalance;
        gainText = Component.literal(ClientSellData.accounts() ? MoneyText.signed(earned) : "+" + earned);
        gainAt = System.currentTimeMillis();
        gain.snap(1.0f);
    }

    private static void relabel() {
        balanceShown = heldBalance > 0 || ClientSellData.accounts();
        pendingShown = heldPending > 0;
        if (balanceShown) balanceTicker.set(heldBalance, MONEY);
        if (pendingShown) pendingTicker.set(heldPending, PENDING);
    }

    private static void forget() {
        heldBalance = 0;
        heldPending = 0;
        gainText = Component.empty();
        gain.snap(0.0f);
        relabel();
    }

    private static int accent() {
        return HudLayout.tint(HudSlot.CURRENCY, UiAccent.color());
    }

    private static void drawAmounts(GuiGraphics graphics, Minecraft mc, float textX, float y,
                                    float pendingOffset, float alpha, float flash, UiTicker balance) {
        if (balance != null) {
            balance.draw(graphics, mc.font, textX, UiRender.centerY(y, HEIGHT, VALUE_SCALE), VALUE_SCALE,
                    UiTheme.mix(HudInk.text(), accent(), flash), alpha);
        }
        if (pendingShown) {
            pendingTicker.draw(graphics, mc.font, textX + pendingOffset, UiRender.centerY(y, HEIGHT, PENDING_SCALE),
                    PENDING_SCALE, HudInk.textFaint(), alpha);
        }
    }

    private static void renderGain(GuiGraphics graphics, Minecraft mc, float centerX, float panelY, float alpha) {
        long elapsed = System.currentTimeMillis() - gainAt;
        if (gainAt <= 0L || elapsed > GAIN_FLASH_MS || gainText.getContents() == ComponentContents.EMPTY) return;

        float progress = elapsed / (float) GAIN_FLASH_MS;
        float fade = UiAnim.clamp01(1.0f - progress * progress) * alpha;
        float drift = UiAnim.easeOut(progress) * GAIN_RISE;

        UiRender.labelCentered(graphics, mc.font, gainText, centerX, panelY - GAIN_TOP - drift, GAIN_SCALE,
                UiTheme.alpha(accent(), fade));
    }
}
