package com.persiki84.killreward;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<String> REWARD_ITEM;
    public static final ForgeConfigSpec.ConfigValue<Integer> REWARD_AMOUNT;
    public static final ForgeConfigSpec.ConfigValue<Boolean> REWARD_TEAM_KILLS;
    public static final ForgeConfigSpec.ConfigValue<Boolean> MOD_ENABLED;

    static {
        BUILDER.push("Kill Reward Settings");

        REWARD_ITEM = BUILDER
                .comment("Предмет, выдаваемый за убийство")
                .define("rewardItem", "minecraft:diamond");

        REWARD_AMOUNT = BUILDER
                .comment("Количество предметов за убийство")
                .defineInRange("rewardAmount", 1, 1, 64);

        REWARD_TEAM_KILLS = BUILDER
                .comment("Выдавать награду за убийство союзников")
                .define("rewardTeamKills", false);

        MOD_ENABLED = BUILDER
                .comment("Включен ли мод")
                .define("modEnabled", true);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    public static void loadConfig() {
        KillRewardMod.rewardItem = REWARD_ITEM.get();
        KillRewardMod.rewardAmount = REWARD_AMOUNT.get();
        KillRewardMod.rewardTeamKills = REWARD_TEAM_KILLS.get();
        KillRewardMod.modEnabled = MOD_ENABLED.get();
    }
}
