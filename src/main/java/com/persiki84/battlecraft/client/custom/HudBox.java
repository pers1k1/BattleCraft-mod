package com.persiki84.battlecraft.client.custom;

public final class HudBox {
    private final HudSlot slot;
    private float x;
    private float y;
    private float width;
    private float height;
    private float scale = 1.0f;
    private float alpha = 1.0f;
    private boolean drawn;

    public HudBox(HudSlot slot) {
        this.slot = slot;
    }

    public HudSlot slot() {
        return slot;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float scale() {
        return scale;
    }

    public float alpha() {
        return alpha;
    }

    public boolean drawn() {
        return drawn;
    }

    public float centerX() {
        return x + width * scale / 2.0f;
    }
    public float localCenterX() {
        return x + width / 2.0f;
    }

    public boolean holds(double pointX, double pointY) {
        return drawn && pointX >= x && pointX <= x + width * scale
                && pointY >= y && pointY <= y + height * scale;
    }

    public void set(float x, float y, float width, float height, float scale, float alpha) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.alpha = alpha;
        this.drawn = true;
    }

    void moveY(float value) {
        this.y = value;
    }

    void clear() {
        drawn = false;
    }
}
