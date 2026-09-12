package com.persiki84.battlecraft.client.menu;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

public final class ScrollRestyle {
    private static final String SCROLLBAR_POSITION = "m_5756_";
    private static final String MAX_SCROLL = "m_93518_";
    private static final String MAX_POSITION = "m_5775_";
    private static final String TOP = "f_93390_";
    private static final String BOTTOM = "f_93391_";
    private static final String LEFT = "f_93393_";
    private static final String RIGHT = "f_93392_";
    private static final String ITEM_HEIGHT = "f_93387_";
    private static final String HEADER_HEIGHT = "f_93395_";
    private static final int ROW_INSET = 4;

    private static final float VANILLA_WIDTH = 6.0f;
    private static final float TRACK_WIDTH = 5.0f;
    private static final float THUMB_IDLE = 2.6f;
    private static final float THUMB_ACTIVE = 5.0f;
    private static final float MIN_THUMB = 26.0f;
    private static final float COVER_MARGIN = 1.4f;
    private static final float EDGE_FADE = 15.0f;
    private static final int COVER = 0xFF08080B;
    private static final int EDGE_SHADE = 0xB2000000;
    private static final long ACTIVE_MS = 1100L;

    private static final Map<AbstractSelectionList<?>, Bar> bars = new WeakHashMap<>();

    private static boolean probed;
    private static boolean available;
    private static Method scrollbarPosition;
    private static Method maxScroll;
    private static Method maxPosition;
    private static Field top;
    private static Field bottom;
    private static Field left;
    private static Field right;
    private static Field itemHeight;
    private static Field headerHeight;

    private ScrollRestyle() {}

    public static void configure(Screen screen) {
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof AbstractSelectionList<?> list) {
                list.setRenderBackground(false);
                list.setRenderTopAndBottom(false);
            }
        }
    }

    public static void paint(GuiGraphics graphics, Screen screen, float delta) {
        if (!bind()) return;

        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof AbstractSelectionList<?> list) {
                draw(graphics, list, delta);
            }
        }
    }

    private static void draw(GuiGraphics graphics, AbstractSelectionList<?> list, float delta) {
        try {
            int range = (int) maxScroll.invoke(list);
            Bar bar = bars.computeIfAbsent(list, key -> new Bar());
            float presence = bar.presence.to(range > 0 ? 1.0f : 0.0f, delta);
            if (presence <= 0.01f) return;

            float x = (int) scrollbarPosition.invoke(list);
            float y0 = top.getInt(list);
            float y1 = bottom.getInt(list);
            float height = y1 - y0;
            if (height <= 6.0f) return;

            edges(graphics, list, y0, y1, presence);

            float span = Math.max(1, (int) maxPosition.invoke(list));
            float thumbHeight = Math.max(MIN_THUMB, Math.min(height - 4.0f, height * height / span));
            float travel = range > 0 ? UiAnim.clamp01((float) list.getScrollAmount() / range) : 0.0f;

            if (Math.abs(travel - bar.lastTarget) > 0.0005f) {
                bar.lastTarget = travel;
                bar.movedAt = System.currentTimeMillis();
            }
            float eased = bar.offset.to(travel, delta);
            float focus = UiAnim.clamp01((ACTIVE_MS - (System.currentTimeMillis() - bar.movedAt)) / (float) ACTIVE_MS);

            rail(graphics, x, y0, height, thumbHeight, eased, presence, bar.wake.to(focus, delta));
        } catch (Throwable error) {
            available = false;
        }
    }

    private static void rail(GuiGraphics graphics, float x, float y0, float height, float thumbHeight,
                             float eased, float presence, float wake) {
        UiRender.panel(graphics, x - COVER_MARGIN, y0 - COVER_MARGIN,
                VANILLA_WIDTH + COVER_MARGIN * 2.0f, height + COVER_MARGIN * 2.0f, 3.6f,
                UiTheme.alpha(COVER, presence));

        float centerX = x + VANILLA_WIDTH / 2.0f;
        float trackWidth = TRACK_WIDTH * (0.55f + 0.45f * wake);
        UiGlass.sunken(graphics, centerX - trackWidth / 2.0f, y0, trackWidth, height,
                trackWidth / 2.0f, presence * (0.45f + 0.55f * wake));

        float thumbWidth = THUMB_IDLE + (THUMB_ACTIVE - THUMB_IDLE) * wake;
        float thumbY = y0 + (height - thumbHeight) * eased;
        UiGlass.inner(graphics, centerX - thumbWidth / 2.0f, thumbY, thumbWidth, thumbHeight,
                thumbWidth / 2.0f, presence, 0.25f + 0.7f * wake);
        UiRender.panel(graphics, centerX - thumbWidth / 2.0f + 0.9f, thumbY + 2.5f,
                Math.max(0.6f, thumbWidth - 1.8f), thumbHeight - 5.0f, thumbWidth / 2.0f,
                UiTheme.withAlpha(0xFFFFFF, (0.10f + 0.22f * wake) * presence));
    }

    private static void edges(GuiGraphics graphics, AbstractSelectionList<?> list, float y0, float y1, float presence)
            throws IllegalAccessException {
        float x0 = left.getInt(list);
        float width = right.getInt(list) - x0;
        if (width <= 0.0f) return;

        int shade = UiTheme.alpha(EDGE_SHADE, presence);
        int clear = UiTheme.withAlpha(0x000000, 0.0f);
        UiRender.gradient(graphics, x0, y0, width, EDGE_FADE, shade, clear);
        UiRender.gradient(graphics, x0, y1 - EDGE_FADE, width, EDGE_FADE, clear, shade);
    }

    private static boolean bind() {
        if (!probed) {
            probed = true;
            try {
                scrollbarPosition = ObfuscationReflectionHelper.findMethod(AbstractSelectionList.class, SCROLLBAR_POSITION);
                maxScroll = ObfuscationReflectionHelper.findMethod(AbstractSelectionList.class, MAX_SCROLL);
                maxPosition = ObfuscationReflectionHelper.findMethod(AbstractSelectionList.class, MAX_POSITION);
                top = ObfuscationReflectionHelper.findField(AbstractSelectionList.class, TOP);
                bottom = ObfuscationReflectionHelper.findField(AbstractSelectionList.class, BOTTOM);
                left = ObfuscationReflectionHelper.findField(AbstractSelectionList.class, LEFT);
                right = ObfuscationReflectionHelper.findField(AbstractSelectionList.class, RIGHT);
                itemHeight = ObfuscationReflectionHelper.findField(AbstractSelectionList.class, ITEM_HEIGHT);
                headerHeight = ObfuscationReflectionHelper.findField(AbstractSelectionList.class, HEADER_HEIGHT);
                available = true;
            } catch (Throwable error) {
                available = false;
            }
        }
        return available;
    }

    public static boolean rowVisible(AbstractSelectionList<?> list, int index) {
        if (index < 0 || !bind()) return true;
        try {
            int y0 = top.getInt(list);
            int y1 = bottom.getInt(list);
            int step = itemHeight.getInt(list);
            int rowTop = y0 + ROW_INSET - (int) list.getScrollAmount() + index * step + headerHeight.getInt(list);
            return rowTop + step >= y0 && rowTop <= y1;
        } catch (Throwable error) {
            available = false;
            return true;
        }
    }

    public record Bounds(float top, float bottom, float left, float right) {}

    public static Bounds boundsOf(AbstractSelectionList<?> list) {
        if (!bind()) return null;
        try {
            return new Bounds(top.getInt(list), bottom.getInt(list), left.getInt(list), right.getInt(list));
        } catch (Throwable error) {
            available = false;
            return null;
        }
    }

    private static final class Bar {
        private final Smooth offset = new Smooth(18.0f);
        private final Smooth presence = new Smooth(0.0f, 10.0f);
        private final Smooth wake = new Smooth(0.0f, 9.0f);
        private float lastTarget = -1.0f;
        private long movedAt;
    }
}
