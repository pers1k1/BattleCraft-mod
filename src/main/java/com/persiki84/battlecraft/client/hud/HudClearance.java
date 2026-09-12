package com.persiki84.battlecraft.client.hud;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiFrame;
import net.minecraft.client.Minecraft;

public final class HudClearance {
    private static final float BASE = 38.0f;

    private static final float VEHICLE = 74.0f;
    private static final float BARE = 14.0f;
    private static final float SPEED = 9.0f;

    private static final Smooth bottom = new Smooth(BASE, SPEED);

    private HudClearance() {}

    public static float bottom() {
        return bottom.to(target(), UiFrame.delta());
    }

    private static float target() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return BASE;
        if (SuperbBridge.riding(minecraft.player)) return vehicle(true);
        return HudState.hotbarWanted() ? BASE : BARE;
    }

    public static float lift() {
        return Math.max(0.0f, bottom() - BASE);
    }

    public static float vehicle(boolean rightSide) {
        float measured = ForeignHud.clearance(rightSide);
        return measured > 0.0f ? Math.max(BASE, measured) : VEHICLE;
    }
}
