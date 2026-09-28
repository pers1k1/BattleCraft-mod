package com.persiki84.shared.client.ui;

final class UiHushSpot {
    float centreX;
    float centreY;
    float halfWidth;
    float halfHeight;
    float radius;
    float reach;
    float strength;
    float cover;
    int target;

    static UiHushSpot[] pool(int size) {
        UiHushSpot[] spots = new UiHushSpot[size];
        for (int index = 0; index < size; index++) {
            spots[index] = new UiHushSpot();
        }
        return spots;
    }

    void copy(UiHushSpot other) {
        centreX = other.centreX;
        centreY = other.centreY;
        halfWidth = other.halfWidth;
        halfHeight = other.halfHeight;
        radius = other.radius;
        reach = other.reach;
        strength = other.strength;
        cover = other.cover;
        target = other.target;
    }

    void clear() {
        centreX = 0.0f;
        centreY = 0.0f;
        halfWidth = 0.0f;
        halfHeight = 0.0f;
        radius = 0.0f;
        reach = 0.0f;
        strength = 0.0f;
        cover = 0.0f;
        target = 0;
    }

    boolean touches(UiHushSpot other) {
        float spanX = halfWidth + reach + other.halfWidth + other.reach;
        float spanY = halfHeight + reach + other.halfHeight + other.reach;
        return Math.abs(centreX - other.centreX) < spanX && Math.abs(centreY - other.centreY) < spanY;
    }
}
