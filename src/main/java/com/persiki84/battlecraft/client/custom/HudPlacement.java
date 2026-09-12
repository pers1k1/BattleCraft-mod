package com.persiki84.battlecraft.client.custom;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiOklab;
import com.persiki84.shared.client.ui.UiWash;

public final class HudPlacement {
    private static final float TINT_SECONDS = 0.34f;
    private static final float TINT_SPEED = 12.0f;
    public static final float SCALE_MIN = 0.4f;
    public static final float SCALE_MAX = 2.5f;
    public static final float DIM_MAX = 0.85f;
    public static final float CORNER_MIN = 0.0f;
    public static final float CORNER_MAX = 2.5f;

    private final HudSlot slot;
    private final UiWash wash = new UiWash(TINT_SECONDS);
    private final Smooth presence = new Smooth(0.0f, TINT_SPEED);
    private HudAnchor anchor;
    private float offsetX;
    private float offsetY;
    private float scale = 1.0f;
    private float alpha = 1.0f;
    private boolean visible = true;
    private Integer tint;
    private HudDock dock;
    private float dim;
    private float corner = 1.0f;

    public HudPlacement(HudSlot slot) {
        this.slot = slot;
        this.anchor = slot.fallback();
        this.dock = slot.leaning();
    }

    public HudAnchor anchor() {
        return anchor;
    }

    public void anchor(HudAnchor value) {
        anchor = value == null ? slot.fallback() : value;
    }

    public float offsetX() {
        return offsetX;
    }

    public float offsetY() {
        return offsetY;
    }

    public void offset(float x, float y) {
        offsetX = x;
        offsetY = y;
    }

    public float scale() {
        return scale;
    }

    public void scale(float value) {
        scale = Math.max(SCALE_MIN, Math.min(SCALE_MAX, value));
    }

    public float alpha() {
        return alpha;
    }

    public void alpha(float value) {
        alpha = Math.max(0.0f, Math.min(1.0f, value));
    }

    public boolean visible() {
        return visible;
    }

    public void visible(boolean value) {
        visible = value;
    }

    public Integer tint() {
        return tint;
    }

    public void tint(Integer argb) {
        tint = argb;
    }

    // WHY: запасной цвет у элемента свой в каждом месте отрисовки (у острова их два за кадр), поэтому
    // WHY: переезжает только сама окраска, а снятие и назначение идут отдельной долей
    public int tinted(int fallback) {
        float share = presence.get();
        if (share <= 0.002f) return fallback;

        return UiOklab.sweep(fallback, wash.get(), share, 0.0f);
    }

    public void washTint(float delta) {
        if (tint != null) wash.aim(tint);
        presence.to(tint == null ? 0.0f : 1.0f, delta);
        wash.advance(delta);
    }

    public HudDock dock() {
        return dock;
    }

    public void dock(HudDock value) {
        dock = value != null && value.target() == slot ? null : value;
    }

    public float dim() {
        return dim;
    }

    public void dim(float value) {
        dim = Math.max(0.0f, Math.min(DIM_MAX, value));
    }

    public float corner() {
        return corner;
    }

    public void corner(float value) {
        corner = Math.max(CORNER_MIN, Math.min(CORNER_MAX, value));
    }

    public boolean leaning() {
        return dock == slot.leaning();
    }

    public boolean plain() {
        return anchor == slot.fallback() && offsetX == 0.0f && offsetY == 0.0f
                && scale == 1.0f && alpha == 1.0f && visible && tint == null && leaning()
                && dim == 0.0f && corner == 1.0f;
    }

    public void reset() {
        resetPosition();
        scale = 1.0f;
        alpha = 1.0f;
        visible = true;
        tint = null;
        dim = 0.0f;
        corner = 1.0f;
    }

    public void resetPosition() {
        anchor = slot.fallback();
        offsetX = 0.0f;
        offsetY = 0.0f;
        dock = slot.leaning();
    }
}
