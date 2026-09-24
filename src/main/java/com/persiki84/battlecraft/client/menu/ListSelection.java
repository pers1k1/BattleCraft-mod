package com.persiki84.battlecraft.client.menu;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;

import java.util.Map;
import java.util.WeakHashMap;

// WHY: ванильный список рисует выбор белой рамкой прямо по строке, и она прыгала кадром; здесь
// WHY: плашка акцента переезжает к новой строке, а прокрутка двигает её вместе со строкой
public final class ListSelection {
    private static final float SPEED = 16.0f;
    private static final float FADE_SPEED = 12.0f;
    private static final float REST_ALPHA = 0.16f;
    private static final float FOCUS_ALPHA = 0.26f;
    private static final float BAR_WIDTH = 2.0f;
    private static final float BAR_INSET = 3.0f;
    private static final float BAR_ALPHA = 0.95f;
    private static final float OUTSET = 2.0f;
    private static final long STALE_FRAMES = 2L;

    private static final Map<AbstractSelectionList<?>, Glide> glides = new WeakHashMap<>();

    private static final class Glide {
        private final Smooth top = new Smooth(0.0f, SPEED);
        private final Smooth shown = new Smooth(0.0f, FADE_SPEED);
        private Object entry;
        private float entryTop;
        private long frame = -STALE_FRAMES - 1L;
    }

    private ListSelection() {}

    public static boolean dressed() {
        return ScreenTabs.dressed();
    }

    public static void paint(GuiGraphics graphics, AbstractSelectionList<?> list, Object entry,
                             int left, int right, int rowTop, int rowHeight, boolean focused) {
        Glide glide = glides.computeIfAbsent(list, ignored -> new Glide());
        float top = rowTop - OUTSET;
        follow(glide, entry, top);

        float delta = UiFrame.delta();
        float y = glide.top.to(top, delta);
        float alpha = glide.shown.to(1.0f, delta);
        float height = rowHeight + OUTSET * 2.0f;
        float plate = focused ? FOCUS_ALPHA : REST_ALPHA;

        UiRender.panel(graphics, left, y, right - left, height, UiMetrics.radius(height),
                UiTheme.alpha(UiAccent.color(), plate * alpha));
        UiRender.panel(graphics, left + BAR_INSET, y + BAR_INSET, BAR_WIDTH, height - BAR_INSET * 2.0f,
                BAR_WIDTH / 2.0f, UiTheme.alpha(UiAccent.color(), BAR_ALPHA * alpha));
    }

    // WHY: та же строка сдвигается прокруткой, и плашка идёт с ней вплотную; новая строка
    // WHY: или список, не рисовавший выбор несколько кадров, ставят плашку сразу на место
    private static void follow(Glide glide, Object entry, float top) {
        long frame = UiFrame.frame();
        boolean stale = frame - glide.frame > STALE_FRAMES;
        glide.frame = frame;

        if (stale) {
            glide.top.snap(top);
            if (glide.entry != entry) glide.shown.snap(0.0f);
        } else if (glide.entry == entry) {
            glide.top.snap(glide.top.get() + top - glide.entryTop);
        }
        glide.entry = entry;
        glide.entryTop = top;
    }
}
