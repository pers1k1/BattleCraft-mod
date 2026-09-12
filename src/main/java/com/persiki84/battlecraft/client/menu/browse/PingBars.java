package com.persiki84.battlecraft.client.menu.browse;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

// WHY: столбики вместо спрайта из icons.png: ванильная иконка нарисована под свой интерфейс
// WHY: и рядом со стеклом читается как чужая деталь
public final class PingBars {
    public static final float WIDTH = 13.0f;

    private static final int BARS = 5;
    private static final float BAR_WIDTH = 2.0f;
    private static final float BAR_GAP = 0.75f;
    private static final float BAR_STEP = 1.7f;
    private static final float BAR_BASE = 2.2f;
    private static final float RADIUS = 0.9f;
    private static final float IDLE_ALPHA = 0.22f;
    private static final float SWEEP_PERIOD = 900.0f;
    private static final long FAST = 150L;
    private static final long GOOD = 300L;
    private static final long FAIR = 600L;
    private static final long POOR = 1000L;

    private PingBars() {}

    public static int level(long ping) {
        if (ping < 0L) return 0;
        if (ping < FAST) return 5;
        if (ping < GOOD) return 4;
        if (ping < FAIR) return 3;
        if (ping < POOR) return 2;
        return 1;
    }

    public static void paint(GuiGraphics graphics, float rightX, float baseline, int level, boolean waiting) {
        int running = waiting ? sweep() : -1;
        float x = rightX - WIDTH;
        for (int index = 0; index < BARS; index++) {
            float height = BAR_BASE + index * BAR_STEP;
            boolean lit = waiting ? index == running : index < level;
            UiRender.panel(graphics, x, baseline - height, BAR_WIDTH, height, RADIUS, tone(lit));
            x += BAR_WIDTH + BAR_GAP;
        }
    }

    private static int sweep() {
        long step = (long) (System.currentTimeMillis() / (SWEEP_PERIOD / (BARS * 2 - 2)));
        int phase = (int) (step % (BARS * 2 - 2));
        return phase < BARS ? phase : BARS * 2 - 2 - phase;
    }

    private static int tone(boolean lit) {
        return lit ? UiAccent.color() : UiTheme.alpha(UiAccent.faint(), IDLE_ALPHA);
    }
}
