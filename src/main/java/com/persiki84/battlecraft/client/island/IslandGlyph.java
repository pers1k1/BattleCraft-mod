package com.persiki84.battlecraft.client.island;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.media.MediaWatch;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiCrisp;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

public final class IslandGlyph {
    private static final int BARS = MediaWatch.BANDS;
    private static final float GAP_SHARE = 0.75f;
    private static final float REST_SHARE = 0.09f;
    private static final float BAR_RISE = 18.0f;
    private static final int[] BAND_AT = {5, 4, 2, 1, 3, 0};
    private static final float[] BAR_FALL = {11.0f, 10.0f, 8.5f, 7.5f, 9.0f, 6.5f};

    private static final float[] reach = new float[BARS];
    private static long stamp = -1L;

    private IslandGlyph() {}

    // WHY: полоска красится переходом от своей верхней точки к нижней, и обе берутся по её месту
    // WHY: в ряду: ряд целиком повторяет раскладку цветов обложки. Остановок ровно две - заливка
    // WHY: идёт веером от центра фигуры, и промежуточные растекались от боков к середине чёрными
    // WHY: клиньями, то есть крестом поперёк полоски
    public static void visualizer(GuiGraphics graphics, float centerX, float centerY, float width, float height,
                                  float fade) {
        float bar = width / (BARS + GAP_SHARE * (BARS - 1));
        float gap = bar * GAP_SHARE;
        float left = centerX - width / 2.0f;
        boolean sharp = UiCrisp.ready();
        if (sharp) graphics.flush();

        for (int index = 0; index < BARS; index++) {
            float share = REST_SHARE + (1.0f - REST_SHARE) * reach[index];
            float tall = Math.max(bar, height * share);
            float spread = tall / (2.0f * height);
            int top = UiTheme.alpha(IslandTone.barAt(index, 0.5f - spread), fade);
            int bottom = UiTheme.alpha(IslandTone.barAt(index, 0.5f + spread), fade);
            paint(graphics, sharp, left + index * (bar + gap), centerY - tall / 2.0f, bar, tall, top, bottom);
        }
    }

    private static void paint(GuiGraphics graphics, boolean sharp, float x, float y, float width, float height,
                              int top, int bottom) {
        if (sharp) {
            UiCrisp.panelShaded(graphics, x, y, width, height, width / 2.0f, top, bottom);
            return;
        }
        UiRender.panelShaded(graphics, x, y, width, height, width / 2.0f, top, bottom);
    }

    // WHY: каждая полоска это своя полоса частот из моста, поэтому им не нужны выдуманные синусоиды:
    // WHY: бас, середина и верх и так живут по-своему. Порядок не по возрастанию частоты: бас
    // WHY: последний, нижняя середина в центре. Подъём быстрый, но не мгновенный: покадровый разбор
    // WHY: записи iOS даёт на подъёме те же скорости, что и на спаде, только вдвое короче
    // WHY: ход отвязан от отрисовки: остров зовёт его каждый кадр, чтобы полоски не замирали на
    // WHY: время морфинга и не прыгали с устаревших значений, когда визуализатор снова виден
    public static void pulse(float energy) {
        long frame = UiFrame.frame();
        if (frame == stamp) return;

        stamp = frame;
        float delta = UiFrame.delta();
        float gain = HudConfig.visualizerGain() * energy;
        float speed = HudConfig.visualizerSpeed();
        float rise = HudConfig.visualizerAttack();
        for (int index = 0; index < BARS; index++) {
            float target = UiAnim.clamp01(MediaWatch.band(BAND_AT[index]) * gain);
            float pace = target > reach[index] ? BAR_RISE * rise : BAR_FALL[index] * speed;
            reach[index] = UiAnim.approach(reach[index], target, pace, delta);
        }
    }
}
