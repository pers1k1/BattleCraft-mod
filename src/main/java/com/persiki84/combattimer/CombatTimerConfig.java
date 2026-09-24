package com.persiki84.combattimer;

import net.minecraftforge.common.ForgeConfigSpec;

public final class CombatTimerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue DURATION;
    public static final ForgeConfigSpec.BooleanValue KILL_ON_LOGOUT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("combat");
        DURATION = builder.comment("Длительность боевого режима после удара, секунды")
                .defineInRange("duration", 30, 5, 300);
        KILL_ON_LOGOUT = builder.comment("Убивать игрока, вышедшего во время боя")
                .define("killOnLogout", true);
        builder.pop();
        SPEC = builder.build();
    }

    private CombatTimerConfig() {}
}
