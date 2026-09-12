package com.persiki84.capturepoints.capture;

import com.persiki84.battlecraft.BattleCraftManager;

public final class MatchState {
    private MatchState() {}

    public static boolean running() {
        BattleCraftManager match = BattleCraftManager.getInstance();
        return match.isSoftDisabled() || match.getPhase() == BattleCraftManager.GamePhase.ACTIVE;
    }
}
