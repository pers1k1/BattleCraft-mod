package com.persiki84.battlecraft.rules;

import com.persiki84.shared.FlagStore;

public final class GameRules {
    private static final FlagStore<GameRule> STORE = new FlagStore<>("battlecraft-rules.json",
            GameRule.values(), GameRule::id, GameRule::enabledByDefault);

    private GameRules() {}

    public static boolean allows(GameRule rule) {
        return STORE.allows(rule);
    }

    public static void set(GameRule rule, boolean enabled) {
        STORE.set(rule, enabled);
    }

    public static void reset() {
        for (GameRule rule : GameRule.values()) {
            STORE.set(rule, rule.enabledByDefault());
        }
    }

    public static void load() {
        STORE.load();
    }
}
