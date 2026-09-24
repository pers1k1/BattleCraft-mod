package com.persiki84.capturepoints.capture;

import com.persiki84.battlecraft.BattleCraftManager;

public final class MatchState {
    private MatchState() {}

    public static boolean running() {
        return BattleCraftManager.getInstance().matchRunning();
    }
}
