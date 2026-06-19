package com.persiki84.airdrop.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

public class AirDropConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        final var pair = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER_SPEC = pair.getRight();
        SERVER = pair.getLeft();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
    }

    public static class Server {
        public final ForgeConfigSpec.BooleanValue modEnabled;
        public final ForgeConfigSpec.BooleanValue autoSpawnEnabled;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> allowedDimensions;
        public final ForgeConfigSpec.DoubleValue centerX;
        public final ForgeConfigSpec.DoubleValue centerZ;
        public final ForgeConfigSpec.IntValue spawnRadius;
        public final ForgeConfigSpec.IntValue intervalSeconds;
        public final ForgeConfigSpec.DoubleValue intervalSpawnChance;
        public final ForgeConfigSpec.IntValue maxSpawnY;
        public final ForgeConfigSpec.IntValue flyingAnimTicks;
        public final ForgeConfigSpec.IntValue autoOpenDelayTicks;

        public final ForgeConfigSpec.IntValue despawnEmptySeconds;
        public final ForgeConfigSpec.IntValue despawnFilledSeconds;
        public final ForgeConfigSpec.IntValue notificationSecondsBeforeDespawn;

        public Server(ForgeConfigSpec.Builder builder) {
            builder.push("general");

            modEnabled = builder
                    .comment("Enable or disable the entire mod")
                    .define("modEnabled", true);

            autoSpawnEnabled = builder
                    .comment("Enable automatic spawning by timer. Default: false")
                    .define("autoSpawnEnabled", false);

            allowedDimensions = builder
                    .comment("Allowed dimensions for auto-spawn")
                    .defineList("allowedDimensions", List.of("minecraft:overworld"), o -> o instanceof String);

            centerX = builder.comment("Center X for random spawn").defineInRange("centerX", 0.0, -30000000.0, 30000000.0);
            centerZ = builder.comment("Center Z for random spawn").defineInRange("centerZ", 0.0, -30000000.0, 30000000.0);
            spawnRadius = builder.comment("Radius around center").defineInRange("spawnRadius", 5000, 10, 10000000);

            intervalSeconds = builder.comment("Auto-spawn interval (seconds)").defineInRange("intervalSeconds", 3600, 10, 864000);
            intervalSpawnChance = builder.comment("Chance (0.0-1.0)").defineInRange("intervalSpawnChance", 1.0, 0.0, 1.0);

            maxSpawnY = builder.defineInRange("maxSpawnY", 350, -64, 2000);

            flyingAnimTicks = builder.defineInRange("flyingAnimTicks", 600, 0, 10000);
            autoOpenDelayTicks = builder.defineInRange("autoOpenDelayTicks", 100, 0, 10000);

            builder.pop();

            builder.push("despawn");

            despawnEmptySeconds = builder.comment("Time to remove EMPTY airdrop (seconds). Default: 60")
                    .defineInRange("despawnEmptySeconds", 60, 1, 86400);

            despawnFilledSeconds = builder.comment("Time to remove FILLED airdrop (seconds). Default: 300 (5 min)")
                    .defineInRange("despawnFilledSeconds", 300, 1, 86400);

            notificationSecondsBeforeDespawn = builder.comment("Warn in chat X seconds before despawn (only if filled). Default: 60")
                    .defineInRange("notificationSecondsBeforeDespawn", 60, 1, 86400);

            builder.pop();
        }
    }
}
