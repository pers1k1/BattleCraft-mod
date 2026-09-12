package com.persiki84.shared.client.ui;

import java.util.HashMap;
import java.util.Map;

public final class TopStack {
    private static final float SPEED = 15.0f;

    private static final Map<String, Smooth> anchors = new HashMap<>();

    private TopStack() {}

    public static float at(String id, float target, float delta) {
        Smooth anchor = anchors.get(id);
        if (anchor == null) {
            anchor = new Smooth(target, SPEED);
            anchors.put(id, anchor);
        }
        return anchor.to(target, delta);
    }

    public static void forget() {
        anchors.clear();
    }
}
