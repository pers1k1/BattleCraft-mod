package com.persiki84.battlecraft.client.menu.custom;

import com.mojang.blaze3d.systems.RenderSystem;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.custom.PaletteKey;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.shared.client.menu.ScrollHint;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

// WHY: центр студии кастомизации это живой образец того, что правится справа: у стекла панель с
// WHY: кнопкой и тостом, у цветов мира метки зон, у острова сам остров, у элементов HUD их плашки.
// WHY: Смена узла проявляет новый образец снизу на место, а щелчок по цвету в образце открывает его
// WHY: палитру: цвет правится там, где он виден
final class CustomPreview {
    private static final float APPEAR_SPEED = 8.0f;
    private static final float APPEAR_RISE = 8.0f;
    private static final float SAMPLE_WIDTH = 210.0f;
    private static final float CAPTION_SCALE = 0.78f;
    private static final float CAPTION_ROW = 16.0f;
    private static final float EXTENT_GUESS = 200.0f;
    private static final float SCROLL_STEP = 21.0f;
    private static final float SCROLL_SPEED = 14.0f;
    private static final float HINT_INSET = 5.0f;
    private static final PreviewFocus[] FOCI = PreviewFocus.values();

    private final PreviewPane pane = new PreviewPane();
    private final PreviewWorld world = new PreviewWorld();
    private final PreviewDesk desk = new PreviewDesk();
    private final PreviewHud hud = new PreviewHud();
    private final PreviewHits hits = new PreviewHits();
    private final Smooth appear = new Smooth(0.0f, APPEAR_SPEED);
    private final Smooth glide = new Smooth(0.0f, SCROLL_SPEED);
    private final ScrollHint above = new ScrollHint();
    private final ScrollHint below = new ScrollHint();
    private final float[] extents = new float[FOCI.length];
    private final float[] tint = new float[4];
    private PreviewFocus shown;
    private float clipTop;
    private float clipBottom;
    private float areaLeft;
    private float areaRight;
    private float scroll;
    private float scrollLimit;

    CustomPreview() {
        Arrays.fill(extents, EXTENT_GUESS);
    }

    void render(GuiGraphics graphics, PreviewFocus focus, Component caption, float left, float top, float width,
                float height, int mouseX, int mouseY) {
        if (focus != shown) {
            shown = focus;
            appear.snap(0.0f);
            hud.forget();
            scroll = 0.0f;
            glide.snap(0.0f);
        }
        float delta = UiFrame.delta();
        pane.advance(delta);
        float grown = UiAnim.easeOut(appear.to(1.0f, delta));
        UiRender.textTrackedFit(graphics, Minecraft.getInstance().font, caption, left + width / 2.0f,
                top + UiMetrics.PAD, CAPTION_ROW, width - UiMetrics.PAD_WIDE * 2.0f, CAPTION_SCALE, 0.4f,
                UiTheme.alpha(UiAccent.textDim(), grown), false);
        clipTop = top + CAPTION_ROW + UiMetrics.PAD;
        clipBottom = top + height - 1.0f;
        areaLeft = left;
        areaRight = left + width;
        UiRender.clip(graphics, left + 1.0f, clipTop, width - 2.0f, clipBottom - clipTop);
        try {
            paintFaded(graphics, focus, left, clipTop, width, height - CAPTION_ROW - UiMetrics.PAD, grown,
                    mouseX, mouseY);
        } finally {
            graphics.disableScissor();
        }
        float centerX = left + width / 2.0f;
        above.render(graphics, centerX, clipTop + HINT_INSET, false, scroll > 0.0f);
        below.render(graphics, centerX, clipBottom - HINT_INSET, true, scroll < scrollLimit);
    }

    // WHY: образец выше окна (плашки всех элементов HUD при крупном интерфейсе) листается колесом
    // WHY: целыми рядами плашек: иначе нижние ряды нельзя было бы ни увидеть, ни переключить щелчком
    void scroll(double mouseX, double mouseY, double amount) {
        if (scrollLimit <= 0.0f || mouseX < areaLeft || mouseX >= areaRight || !visible(mouseY)) return;
        scroll = Math.max(0.0f, Math.min(scrollLimit, scroll - (float) Math.signum(amount) * SCROLL_STEP));
    }

    // WHY: новый образец проявляется альфой и поднимается снизу на место; модулятор цвета уважают и
    // WHY: стекло, и наши шейдеры текста, а батч сбрасывается до смены модулятора и после образца
    private void paintFaded(GuiGraphics graphics, PreviewFocus focus, float left, float top, float width,
                            float height, float grown, int mouseX, int mouseY) {
        boolean fading = grown < 0.999f;
        if (fading) fade(graphics, grown);
        float extent = extents[focus.ordinal()];
        float y = place(top, height, extent);
        float lag = scroll - glide.to(scroll, UiFrame.delta());
        UiRestFrame.shift(graphics, 0.0f, (1.0f - grown) * APPEAR_RISE + lag);
        try {
            float sampleWidth = Math.min(SAMPLE_WIDTH, width - UiMetrics.PAD_WIDE * 2.0f);
            float x = left + (width - sampleWidth) / 2.0f;
            hits.clear();
            float bottom = paintFocus(graphics, focus, x, y, sampleWidth, mouseX, mouseY);
            extents[focus.ordinal()] = Math.max(1.0f, bottom - y);
            hits.paint(graphics, mouseX, mouseY, grown);
        } finally {
            UiRestFrame.pop(graphics);
            if (fading) unfade(graphics);
        }
    }

    // WHY: место прокрутки целое, чтобы образец в покое стоял на сетке пикселей; переезд к нему идёт
    // WHY: сдвигом, который гаснет к нулю
    private float place(float top, float height, float extent) {
        scrollLimit = Math.max(0.0f, Math.round(extent + UiMetrics.PAD * 2.0f - height));
        scroll = Math.min(scroll, scrollLimit);
        if (scrollLimit <= 0.0f) return top + Math.max(UiMetrics.PAD, (height - extent) / 2.0f);
        return top + UiMetrics.PAD - scroll;
    }

    private void fade(GuiGraphics graphics, float alpha) {
        graphics.flush();
        float[] current = RenderSystem.getShaderColor();
        System.arraycopy(current, 0, tint, 0, tint.length);
        RenderSystem.setShaderColor(tint[0], tint[1], tint[2], tint[3] * alpha);
    }

    private void unfade(GuiGraphics graphics) {
        graphics.flush();
        RenderSystem.setShaderColor(tint[0], tint[1], tint[2], tint[3]);
    }

    private float paintFocus(GuiGraphics graphics, PreviewFocus focus, float x, float y, float width,
                             int mouseX, int mouseY) {
        float centerX = x + width / 2.0f;
        return switch (focus) {
            case GLASS -> pane.glass(graphics, x, y, width, hits);
            case DEPTH -> pane.depth(graphics, x, y, width, hits);
            case TEXT, GENERAL -> pane.text(graphics, x, y, width, hits);
            case WORLD -> world.zones(graphics, x, y, width, hits);
            case DAMAGE -> world.damage(graphics, x, y, width, hits);
            case DESKTOP -> desk.paint(graphics, x, y, width);
            case ISLAND -> hud.sample(graphics, HudSlot.ISLAND, HudConfig.island(), centerX, y, width);
            case VOICE -> hud.sample(graphics, HudSlot.VOICE, true, centerX, y, width);
            case RADIO -> hud.sample(graphics, HudSlot.RADIO, HudConfig.radioHud(), centerX, y, width);
            case HUD -> hud.chips(graphics, x, y, width, mouseX, mouseY);
            case PRESETS -> y;
        };
    }

    // WHY: образец длиннее окна обрезан его кромкой, и место за ней не принимает щелчок: иначе
    // WHY: невидимая плашка переключала бы элемент из-под пустого места
    private boolean visible(double mouseY) {
        return mouseY >= clipTop && mouseY < clipBottom;
    }

    PaletteKey colorAt(double mouseX, double mouseY) {
        return visible(mouseY) ? hits.at(mouseX, mouseY) : null;
    }

    HudSlot slotAt(PreviewFocus focus, double mouseX, double mouseY) {
        return focus == PreviewFocus.HUD && visible(mouseY) ? hud.slotAt(mouseX, mouseY) : null;
    }
}
