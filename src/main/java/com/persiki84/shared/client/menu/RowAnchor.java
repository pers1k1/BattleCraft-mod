package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.gui.GuiGraphics;

public final class RowAnchor {
    private static final float CLIP_MARGIN = 10.0f;
    private static final int POINTER_AWAY = Integer.MIN_VALUE / 2;

    private GlidingRow.Lane lane;
    private int anchorY;
    private int shiftedY;
    private float bandTop;
    private float bandBottom;
    private boolean banded;

    public void set(int y, GlidingRow.Lane chosen) {
        anchorY = y;
        shiftedY = y;
        lane = chosen;
    }

    public boolean placed() {
        return lane != null;
    }

    public void follow(ScrollLanes lanes) {
        bandTop = lanes.top(lane);
        bandBottom = lanes.bottom(lane);
        banded = bandBottom > bandTop;
        shiftedY = Math.round(anchorY + lanes.offset(lane));
    }

    public int shifted() {
        return shiftedY;
    }

    public boolean gone(int y, int height) {
        return banded && (y + height <= bandTop || y >= bandBottom);
    }

    public boolean covers(double mouseY) {
        return !banded || (mouseY >= bandTop && mouseY < bandBottom);
    }

    public int pointer(int mouseY) {
        return covers(mouseY) ? mouseY : POINTER_AWAY;
    }

    public boolean clip(GuiGraphics graphics, int x, int width, int y, int height) {
        if (!banded || (y >= bandTop && y + height <= bandBottom)) return false;

        UiRender.clip(graphics, x - CLIP_MARGIN, bandTop, width + CLIP_MARGIN * 2.0f, bandBottom - bandTop);
        return true;
    }
}
