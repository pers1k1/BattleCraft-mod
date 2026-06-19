package com.persiki84.knockdown.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class KnockdownConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue BLEED_TIME_SECONDS;
    public static final ForgeConfigSpec.IntValue REVIVE_TIME_SECONDS;
    public static final ForgeConfigSpec.IntValue INJECTOR_TIME_SECONDS;
    public static final ForgeConfigSpec.IntValue COOLDOWN_TIME_SECONDS;

    static {
        BUILDER.push("Knockdown Settings (Values in Seconds)");

        BLEED_TIME_SECONDS = BUILDER.comment("Время истечения кровью (сек)")
                .defineInRange("bleedTime", 45, 5, 600);

        REVIVE_TIME_SECONDS = BUILDER.comment("Время поднятия игрока рукой (сек)")
                .defineInRange("reviveTime", 5, 1, 60);

        INJECTOR_TIME_SECONDS = BUILDER.comment("Время использования шприца (сек)")
                .defineInRange("injectorTime", 12, 1, 60);

        COOLDOWN_TIME_SECONDS = BUILDER.comment("Время усталости после поднятия (сек) - сколько нельзя снова нокнуться")
                .defineInRange("cooldownTime", 300, 0, 3600);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
