package com.persiki84.battlecraft.rules;

import com.persiki84.battlecraft.BattleCraftConfig;

public record StaminaCosts(int sprintDashPercent, int armedSprintDashPercent,
                           int jumpPermille, int armedJumpPermille) {

    public static StaminaCosts of(BattleCraftConfig config) {
        return new StaminaCosts(config.sprintStaminaDashPercent, config.armedSprintStaminaDashPercent,
                config.jumpStaminaPermille, config.armedJumpStaminaPermille);
    }
}
