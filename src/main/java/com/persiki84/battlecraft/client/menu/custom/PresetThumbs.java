package com.persiki84.battlecraft.client.menu.custom;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.persiki84.battlecraft.client.custom.GlassKey;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiBackdrop;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiQuality;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.HashMap;
import java.util.Map;

// WHY: стекло миниатюры зависит не только от ручек пресета, но и от снимка подложки, а он один на кадр
// WHY: и собран размытием выбранного пресета (у «Простого» его нет вовсе). Поэтому каждая миниатюра
// WHY: собирается один раз в свою текстуру полным стилем своего пресета: подложка из шаров на сплошном
// WHY: фоне, своя пирамида размытия, своя пластина. Дальше в кадре она только выводится картинкой и не
// WHY: стоит ничего. Пересборка идёт при смене карточки, акцента и размера в пикселях, не больше
// WHY: BUILDS_PER_FRAME за кадр: пирамида со своим числом уровней пересоздаёт текстуры
final class PresetThumbs {
    private static final int BUILDS_PER_FRAME = 2;
    private static final float GROUND_RADIUS = 0.16f;
    private static final int[] VIEWPORT = new int[4];

    private final Map<String, Thumb> thumbs = new HashMap<>();
    private TextureTarget scratch;
    private int built;
    private long frame = -1L;

    private static final class Thumb {
        private TextureTarget target;
        private PresetCard card;
        private int accent;
        private int width;
        private int height;
    }

    // WHY: отдаёт текстуру готовой миниатюры или ноль, пока её нет; собирает не больше двух за кадр
    int texture(GuiGraphics graphics, PresetCard card, float width, float height) {
        Thumb thumb = thumbs.computeIfAbsent(card.key(), unused -> new Thumb());
        int pixelsWide = pixels(width);
        int pixelsTall = pixels(height);
        int accent = UiAccent.color();
        if (fresh(thumb, card, accent, pixelsWide, pixelsTall)) return thumb.target.getColorTextureId();
        if (!budget()) return thumb.target == null ? 0 : thumb.target.getColorTextureId();

        build(graphics, thumb, card, width, height, pixelsWide, pixelsTall);
        thumb.card = card;
        thumb.accent = accent;
        return thumb.target == null ? 0 : thumb.target.getColorTextureId();
    }

    void release() {
        for (Thumb thumb : thumbs.values()) {
            if (thumb.target != null) thumb.target.destroyBuffers();
        }
        thumbs.clear();
        if (scratch != null) scratch.destroyBuffers();
        scratch = null;
    }

    private static boolean fresh(Thumb thumb, PresetCard card, int accent, int width, int height) {
        return thumb.target != null && thumb.card == card && thumb.accent == accent
                && thumb.width == width && thumb.height == height;
    }

    private boolean budget() {
        long now = UiFrame.frame();
        if (now != frame) {
            frame = now;
            built = 0;
        }
        if (built >= BUILDS_PER_FRAME) return false;
        built++;
        return true;
    }

    private static int pixels(float units) {
        return Math.max(1, Math.round(units * (float) Minecraft.getInstance().getWindow().getGuiScale()));
    }

    private void build(GuiGraphics graphics, Thumb thumb, PresetCard card, float width, float height,
                       int pixelsWide, int pixelsTall) {
        graphics.flush();
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, VIEWPORT);
        boolean lent = UiBackdrop.lend();
        float presence = UiGlassStyle.presence();
        UiGlassStyle.restorePresence(1.0f);
        try {
            paintInto(graphics, card, width, height);
            keep(thumb, pixelsWide, pixelsTall);
        } finally {
            UiGlassStyle.restorePresence(presence);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GlStateManager._viewport(VIEWPORT[0], VIEWPORT[1], VIEWPORT[2], VIEWPORT[3]);
            UiBackdrop.retake();
            UiBackdrop.giveBack(lent);
        }
    }

    private void paintInto(GuiGraphics graphics, PresetCard card, float width, float height) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (scratch == null || scratch.width != main.width || scratch.height != main.height) {
            if (scratch != null) scratch.destroyBuffers();
            scratch = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            scratch.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        }
        scratch.clear(Minecraft.ON_OSX);
        scratch.bindWrite(true);
        graphics.pose().pushPose();
        graphics.pose().setIdentity();
        try {
            paintGround(graphics, card, width, height);
            PresetStyle.wear(graphics, card, true);
            try {
                UiBackdrop.retake();
                paintPlate(graphics, card, width, height);
                graphics.flush();
            } finally {
                PresetStyle.shed(graphics, true);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void paintGround(GuiGraphics graphics, PresetCard card, float width, float height) {
        float radius = Math.min(width, height) * GROUND_RADIUS;
        UiRender.panel(graphics, 0.0f, 0.0f, width, height, radius, UiTheme.withAlpha(UiPalette.panelDeep(), 1.0f));
        PresetGlyph.paintGround(graphics, card, 0.0f, 0.0f, width, height, 1.0f);
        graphics.flush();
    }

    private static void paintPlate(GuiGraphics graphics, PresetCard card, float width, float height) {
        PresetGlyph.Plate plate = PresetGlyph.plate(card, 0.0f, 0.0f, width, height);
        if (card.base().mode() == UiQuality.Mode.PLAIN) {
            UiRender.panel(graphics, plate.x(), plate.y(), plate.width(), plate.height(), plate.radius(),
                    UiTheme.alpha(UiTheme.PANEL, card.value(GlassKey.SURFACE_ALPHA)));
            return;
        }
        UiGlass.panel(graphics, plate.x(), plate.y(), plate.width(), plate.height(), plate.radius(), 1.0f,
                plate.lift());
    }

    private void keep(Thumb thumb, int pixelsWide, int pixelsTall) {
        if (thumb.target == null || thumb.width != pixelsWide || thumb.height != pixelsTall) {
            if (thumb.target != null) thumb.target.destroyBuffers();
            thumb.target = new TextureTarget(pixelsWide, pixelsTall, false, Minecraft.ON_OSX);
            thumb.target.setFilterMode(GL11.GL_LINEAR);
            thumb.width = pixelsWide;
            thumb.height = pixelsTall;
        }
        int top = scratch.height;
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, scratch.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, thumb.target.frameBufferId);
        GL30.glBlitFramebuffer(0, top - pixelsTall, pixelsWide, top, 0, 0, pixelsWide, pixelsTall,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }
}
