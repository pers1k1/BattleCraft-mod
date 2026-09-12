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
        private static final double COORDINATE_LIMIT = 30000000.0;
        private static final int SECONDS_LIMIT = 86400;

        private static final String CENTER_MODE = "Take the spawn center from the world spawn point instead of centerX/centerZ";
        private static final String START_HEIGHT = "Height the drop starts from, raised when the ground is higher";
        private static final String DESPAWN_EMPTY = "Time to remove EMPTY airdrop (seconds). Default: 60";
        private static final String DESPAWN_FILLED = "Time to remove FILLED airdrop (seconds). Default: 300 (5 min)";
        private static final String DESPAWN_WARN = "Warn in chat X seconds before despawn (only if filled). Default: 60";

        public final ForgeConfigSpec.BooleanValue modEnabled;
        public final ForgeConfigSpec.BooleanValue autoSpawnEnabled;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> allowedDimensions;
        public final ForgeConfigSpec.BooleanValue centerAtWorldSpawn;
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
            modEnabled = flag(builder, "Enable or disable the entire mod", "modEnabled", true);
            autoSpawnEnabled = flag(builder, "Enable automatic spawning by timer", "autoSpawnEnabled", false);
            allowedDimensions = dimensions(builder);
            centerAtWorldSpawn = flag(builder, CENTER_MODE, "centerAtWorldSpawn", true);
            centerX = coordinate(builder, "Center X for random spawn", "centerX");
            centerZ = coordinate(builder, "Center Z for random spawn", "centerZ");
            spawnRadius = builder.comment("Radius around center").defineInRange("spawnRadius", 5000, 10, 10000000);
            intervalSeconds = builder.comment("Auto-spawn interval (seconds)").defineInRange("intervalSeconds", 3600, 10, 864000);
            intervalSpawnChance = builder.comment("Chance (0.0-1.0)").defineInRange("intervalSpawnChance", 1.0, 0.0, 1.0);
            maxSpawnY = builder.comment(START_HEIGHT).defineInRange("maxSpawnY", 350, -64, 2000);
            flyingAnimTicks = builder.defineInRange("flyingAnimTicks", 600, 0, 10000);
            autoOpenDelayTicks = builder.defineInRange("autoOpenDelayTicks", 100, 0, 10000);
            builder.pop();

            builder.push("despawn");
            despawnEmptySeconds = seconds(builder, DESPAWN_EMPTY, "despawnEmptySeconds", 60);
            despawnFilledSeconds = seconds(builder, DESPAWN_FILLED, "despawnFilledSeconds", 300);
            notificationSecondsBeforeDespawn = seconds(builder, DESPAWN_WARN, "notificationSecondsBeforeDespawn", 60);
            builder.pop();
        }

        private static ForgeConfigSpec.BooleanValue flag(ForgeConfigSpec.Builder builder, String comment,
                                                         String key, boolean fallback) {
            return builder.comment(comment).define(key, fallback);
        }

        private static ForgeConfigSpec.ConfigValue<List<? extends String>> dimensions(ForgeConfigSpec.Builder builder) {
            return builder.comment("Allowed dimensions for auto-spawn")
                    .defineList("allowedDimensions", List.of("minecraft:overworld"), o -> o instanceof String);
        }

        private static ForgeConfigSpec.DoubleValue coordinate(ForgeConfigSpec.Builder builder, String comment, String key) {
            return builder.comment(comment).defineInRange(key, 0.0, -COORDINATE_LIMIT, COORDINATE_LIMIT);
        }

        private static ForgeConfigSpec.IntValue seconds(ForgeConfigSpec.Builder builder, String comment,
                                                        String key, int fallback) {
            return builder.comment(comment).defineInRange(key, fallback, 1, SECONDS_LIMIT);
        }
    }
}
