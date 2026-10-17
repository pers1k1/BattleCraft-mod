package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class MenuFeedback {
    private static final long LIFE_MS = 3600L;
    private static final float FADE_SPEED = 14.0f;
    private static final float SCALE = 0.85f;
    private static final float HEIGHT = 18.0f;
    private static final float PAD = 12.0f;
    private static final float SLIDE = 5.0f;
    private static final float MAX_WIDTH = 380.0f;

    private static final Smooth shown = new Smooth(0.0f, FADE_SPEED);
    private static final TickerLine line = new TickerLine(0.5f);

    private static Component message;
    private static boolean alerting;
    private static long shownAt;

    private MenuFeedback() {}

    public static void show(Component text, boolean alert) {
        if (!sameMessage(message, text)) line.forget();
        message = text;
        alerting = alert;
        shownAt = System.currentTimeMillis();
    }

    // WHY: тот же отклик с новым числом (сумма, остаток, счёт) перекатывается по цифрам; другой
    // WHY: отклик встаёт целиком: перекат букв чужой фразы читается как сбой, а не как смена числа
    private static boolean sameMessage(Component was, Component next) {
        if (was == null) return false;
        if (was.getContents() instanceof TranslatableContents old && next.getContents() instanceof TranslatableContents fresh) {
            return old.getKey().equals(fresh.getKey());
        }
        return false;
    }

    public static void clear() {
        message = null;
        shownAt = 0L;
    }

    private static boolean fresh() {
        return message != null && System.currentTimeMillis() - shownAt < LIFE_MS;
    }

    public static void render(GuiGraphics graphics, float centerX, float top) {
        float alpha = shown.to(fresh() ? 1.0f : 0.0f, UiFrame.delta());
        if (alpha <= 0.01f || message == null) return;

        UiRestFrame.shift(graphics, 0.0f, (1.0f - UiAnim.easeOut(alpha)) * SLIDE);
        try {
            paint(graphics, centerX, top, alpha);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    // WHY: подъём строки заявлен ходом от её места, а не прибавлен к Y: иначе надпись шла ступенями
    // WHY: по пикселю против стекла и вставала на место последним рывком в хвосте подъёма
    private static void paint(GuiGraphics graphics, float centerX, float y, float alpha) {
        Minecraft minecraft = Minecraft.getInstance();
        float scale = UiRender.crisp(graphics, SCALE);
        float width = Math.min(MAX_WIDTH, UiRender.measure(graphics, minecraft.font, message, scale) + PAD * 2.0f);
        UiGlass.panel(graphics, centerX - width / 2.0f, y, width, HEIGHT, UiMetrics.radius(HEIGHT), alpha);
        line.box(graphics, minecraft.font, message, centerX - width / 2.0f + PAD, y, HEIGHT, width - PAD * 2.0f, scale,
                tint(alpha));
    }

    private static int tint(float alpha) {
        return UiTheme.alpha(alerting ? UiPalette.alert() : UiAccent.color(), alpha);
    }
}
