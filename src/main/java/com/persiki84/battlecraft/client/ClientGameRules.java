package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.BattleCraftConfig;
import com.persiki84.battlecraft.rules.GameRule;
import com.persiki84.battlecraft.rules.StaminaCosts;

public final class ClientGameRules {
    private static final BattleCraftConfig DEFAULTS = new BattleCraftConfig();

    private static int allowed = defaults();
    private static StaminaCosts stamina = StaminaCosts.of(DEFAULTS);

    private ClientGameRules() {}

    public static void accept(int mask, StaminaCosts costs) {
        allowed = mask;
        stamina = costs;
        ClientGuard.forget();
    }

    public static void forget() {
        allowed = defaults();
        stamina = StaminaCosts.of(DEFAULTS);
        ClientGuard.forget();
    }

    public static StaminaCosts stamina() {
        return stamina;
    }

    public static boolean allows(GameRule rule) {
        return (allowed & 1 << rule.ordinal()) != 0;
    }

    private static int defaults() {
        int mask = 0;
        for (GameRule rule : GameRule.values()) {
            if (rule.enabledByDefault()) mask |= 1 << rule.ordinal();
        }
        return mask;
    }
}
