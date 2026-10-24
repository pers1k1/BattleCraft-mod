package com.persiki84.battlecraft.client.roster;

import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiTicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

// WHY: строка состояния под шапкой: слева сколько игроков на сервере, справа здоровье сервера
// WHY: (TPS по стене и средняя длина тика) и свой пинг. Все числа живые, тикером, как у острова
final class RosterStatus {
    private static final Component ONLINE = Component.translatable("battlecraft.roster.online");
    private static final Component RATE = Component.translatable("battlecraft.roster.tps");
    private static final Component SPENT = Component.translatable("battlecraft.roster.tick");
    private static final Component LATENCY = Component.translatable("battlecraft.roster.ping");
    private static final Component MILLIS = Component.translatable("battlecraft.roster.millis");
    private static final String UNKNOWN = "-";
    private static final UiTicker.Format TENTHS = RosterStatus::tenths;

    private static final float LABEL_SCALE = 0.7f;
    private static final float VALUE_SCALE = 0.85f;
    private static final float INSET = 7.0f;
    private static final float PAIR_GAP = 3.0f;
    private static final float CHIP_GAP = 10.0f;
    private static final float DOT_RADIUS = 2.2f;
    private static final float WELL_ALPHA = 0.72f;
    private static final float LATENCY_PACE = 0.5f;
    private static final int SPENT_LIMIT = 99999;

    private final UiTicker online = new UiTicker();
    private final UiTicker rate = new UiTicker();
    private final UiTicker spent = new UiTicker();
    private final UiTicker latency = new UiTicker().throttle(LATENCY_PACE);
    private boolean pulseShown;
    private int rateTone;
    private int spentTone;
    private int latencyTone;

    private static String tenths(long value) {
        return value / 10L + "." + value % 10L;
    }

    public void draw(GuiGraphics graphics, Font font, float x, float y, float width, float alpha) {
        refresh();
        float height = RosterLayout.STATUS_HEIGHT;
        UiGlass.sunken(graphics, x, y, width, height, height / 2.0f, WELL_ALPHA * alpha);

        float labelY = UiRender.centerY(y, height, LABEL_SCALE);
        float valueY = UiRender.centerY(y, height, VALUE_SCALE);
        float left = x + INSET + pair(graphics, font, ONLINE, online, x + INSET, labelY, valueY,
                HudInk.text(), alpha);
        float right = x + width - INSET;
        right = chip(graphics, font, LATENCY, latency, right, labelY, valueY, latencyTone, alpha) - CHIP_GAP;
        right = chip(graphics, font, SPENT, spent, right, labelY, valueY, spentTone, alpha) - CHIP_GAP;
        float rateLeft = right - chipWidth(graphics, font, RATE, rate, false);
        if (rateLeft - DOT_RADIUS * 2.0f - PAIR_GAP < left) return;
        UiRender.dot(graphics, rateLeft - DOT_RADIUS - PAIR_GAP, y + height / 2.0f, DOT_RADIUS,
                UiTheme.alpha(dotTone(), alpha));
        pair(graphics, font, RATE, rate, rateLeft, labelY, valueY, rateTone, alpha);
    }

    private void refresh() {
        online.set(RosterBook.online());
        boolean known = ServerPulseClient.known();
        float ticksPerSecond = ServerPulseClient.rate();
        float millisPerTick = ServerPulseClient.spent();
        showPulse(known, Math.round(ticksPerSecond * 10.0f), Math.min(SPENT_LIMIT, Math.round(millisPerTick * 10.0f)));
        rateTone = known ? RosterTone.rate(ticksPerSecond) : HudInk.textFaint();
        spentTone = known ? RosterTone.spent(millisPerTick) : HudInk.textFaint();
        int ownLatency = ownLatency();
        latency.set(Math.max(0, ownLatency));
        latencyTone = ownLatency < 0 ? HudInk.textFaint() : RosterTone.latency(ownLatency);
    }

    // WHY: переход с заменителя «-» на число и обратно встаёт сразу, а не перекатывается по буквам:
    // WHY: морфится только смена числа внутри одного состояния
    private void showPulse(boolean known, long shownRate, long shownSpent) {
        boolean flipped = known != pulseShown;
        pulseShown = known;
        if (flipped) {
            snap(rate, known, shownRate);
            snap(spent, known, shownSpent);
            return;
        }
        if (known) {
            rate.set(shownRate, TENTHS);
            spent.set(shownSpent, TENTHS);
        } else {
            rate.set(UNKNOWN);
            spent.set(UNKNOWN);
        }
    }

    private static void snap(UiTicker ticker, boolean known, long value) {
        if (known) {
            ticker.snap(value, TENTHS);
        } else {
            ticker.snap(UNKNOWN);
        }
    }

    private int dotTone() {
        if (!ServerPulseClient.known()) return HudInk.textFaint();
        return rateTone == HudInk.text() ? UiAccent.color() : rateTone;
    }

    private static int ownLatency() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) return -1;
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
        return info == null ? -1 : info.getLatency();
    }

    private static float pair(GuiGraphics graphics, Font font, Component label, UiTicker value, float x,
                              float labelY, float valueY, int tone, float alpha) {
        UiRender.labelScaled(graphics, font, label, x, labelY, LABEL_SCALE,
                UiTheme.alpha(HudInk.textFaint(), alpha));
        float valueX = x + UiRender.measure(graphics, font, label, LABEL_SCALE) + PAIR_GAP;
        value.draw(graphics, font, valueX, valueY, VALUE_SCALE, tone, alpha);
        return valueX - x + value.steadyWidth(graphics, font, VALUE_SCALE);
    }

    private static float chip(GuiGraphics graphics, Font font, Component label, UiTicker value, float right,
                              float labelY, float valueY, int tone, float alpha) {
        float left = right - chipWidth(graphics, font, label, value, true);
        float used = pair(graphics, font, label, value, left, labelY, valueY, tone, alpha);
        UiRender.labelScaled(graphics, font, MILLIS, left + used + PAIR_GAP, labelY, LABEL_SCALE,
                UiTheme.alpha(HudInk.textFaint(), alpha));
        return left;
    }

    private static float chipWidth(GuiGraphics graphics, Font font, Component label, UiTicker value,
                                   boolean unit) {
        float width = UiRender.measure(graphics, font, label, LABEL_SCALE) + PAIR_GAP
                + value.steadyWidth(graphics, font, VALUE_SCALE);
        if (!unit) return width;
        return width + PAIR_GAP + UiRender.measure(graphics, font, MILLIS, LABEL_SCALE);
    }
}
