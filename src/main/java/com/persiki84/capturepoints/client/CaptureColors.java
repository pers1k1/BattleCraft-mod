package com.persiki84.capturepoints.client;

import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.shared.client.ui.UiAccent;

public final class CaptureColors {

    private CaptureColors() {}

    public static int label() {
        return HudInk.text();
    }

    public static int progress() {
        return UiAccent.color();
    }

    public static int neutralDot() {
        return UiAccent.color();
    }

    public static int marker() {
        return UiAccent.color();
    }
}
