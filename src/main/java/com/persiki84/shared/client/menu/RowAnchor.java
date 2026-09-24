package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.gui.GuiGraphics;

public final class RowAnchor {
    private static final float CLIP_MARGIN = 10.0f;
    private static final int POINTER_AWAY = Integer.MIN_VALUE / 2;

    private GlidingRow.Lane lane;
    private int anchorY;
    private int shiftedY;
    private float residue;
    private float bandTop;
    private float bandBottom;
    private boolean banded;

    public void set(int y, GlidingRow.Lane chosen) {
        anchorY = y;
        shiftedY = y;
        residue = 0.0f;
        lane = chosen;
    }

    public boolean placed() {
        return lane != null;
    }

    // WHY: виджет хранит Y целым в единицах GUI, а это 2-4 пикселя экрана; хвост затухания
    // WHY: прокрутки идёт долями единицы и по целому Y превращается в редкие скачки на целый шаг,
    // WHY: поэтому дробь доезжает сдвигом позы при отрисовке, а клики остаются на целом Y
    public void follow(ScrollLanes lanes) {
        bandTop = lanes.top(lane);
        bandBottom = lanes.bottom(lane);
        banded = bandBottom > bandTop;
        float exact = anchorY + lanes.offset(lane);
        shiftedY = Math.round(exact);
        residue = exact - shiftedY;
    }

    public int shifted() {
        return shiftedY;
    }

    public float residue() {
        return residue;
    }

    public boolean gone(int y, int height) {
        float top = y + residue;
        return banded && (top + height <= bandTop || top >= bandBottom);
    }

    public boolean covers(double mouseY) {
        return !banded || (mouseY >= bandTop && mouseY < bandBottom);
    }

    public int pointer(int mouseY) {
        return covers(mouseY) ? mouseY : POINTER_AWAY;
    }

    public void draw(GuiGraphics graphics, int x, int width, int y, int height, Runnable body) {
        boolean clipped = clip(graphics, x, width, y + residue, height);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, residue, 0.0f);
        try {
            body.run();
        } finally {
            graphics.pose().popPose();
            if (clipped) graphics.disableScissor();
        }
    }

    private boolean clip(GuiGraphics graphics, int x, int width, float top, int height) {
        if (!banded || (top >= bandTop && top + height <= bandBottom)) return false;

        UiRender.clip(graphics, x - CLIP_MARGIN, bandTop, width + CLIP_MARGIN * 2.0f, bandBottom - bandTop);
        return true;
    }
}
