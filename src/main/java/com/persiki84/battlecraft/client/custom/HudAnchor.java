package com.persiki84.battlecraft.client.custom;

import java.util.Locale;

public enum HudAnchor {
    TOP_LEFT(0.0f, 0.0f),
    TOP_CENTER(0.5f, 0.0f),
    TOP_RIGHT(1.0f, 0.0f),
    MIDDLE_LEFT(0.0f, 0.5f),
    CENTER(0.5f, 0.5f),
    MIDDLE_RIGHT(1.0f, 0.5f),
    BOTTOM_LEFT(0.0f, 1.0f),
    BOTTOM_CENTER(0.5f, 1.0f),
    BOTTOM_RIGHT(1.0f, 1.0f);

    private final float horizontal;
    private final float vertical;

    HudAnchor(float horizontal, float vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public boolean bottom() {
        return vertical > 0.75f;
    }

    public boolean top() {
        return vertical < 0.25f;
    }

    public boolean right() {
        return horizontal > 0.75f;
    }

    public boolean left() {
        return horizontal < 0.25f;
    }

    public float originX(float screenWidth, float width, float margin) {
        if (left()) return margin;
        if (right()) return screenWidth - margin - width;
        return (screenWidth - width) / 2.0f;
    }

    public float originY(float screenHeight, float height, float margin) {
        if (top()) return margin;
        if (bottom()) return screenHeight - margin - height;
        return (screenHeight - height) / 2.0f;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
    public static HudAnchor byId(String id) {
        if (id == null) return null;
        for (HudAnchor anchor : values()) {
            if (anchor.id().equalsIgnoreCase(id.trim())) return anchor;
        }
        return null;
    }

    public static HudAnchor nearest(float centerX, float centerY, float screenWidth, float screenHeight) {
        float fractionX = screenWidth <= 0.0f ? 0.5f : centerX / screenWidth;
        float fractionY = screenHeight <= 0.0f ? 0.5f : centerY / screenHeight;
        HudAnchor best = CENTER;
        float closest = Float.MAX_VALUE;
        for (HudAnchor anchor : values()) {
            float dx = anchor.horizontal - fractionX;
            float dy = anchor.vertical - fractionY;
            float distance = dx * dx + dy * dy;
            if (distance < closest) {
                closest = distance;
                best = anchor;
            }
        }
        return best;
    }
}
