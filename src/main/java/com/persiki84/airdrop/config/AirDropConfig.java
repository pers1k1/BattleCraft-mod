package com.persiki84.airdrop.config;

import com.persiki84.airdrop.loot.LootTables;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

import static com.persiki84.airdrop.config.AirDropLimits.*;

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

    public static <T> void save(ForgeConfigSpec.ConfigValue<T> value, T wanted) {
        value.set(wanted);
        value.save();
    }

    public static boolean active() {
        return SERVER_SPEC.isLoaded() && SERVER.modEnabled.get();
    }

    public static String airdropTable() {
        String wanted = SERVER.lootTable.get();
        return LootTables.exists(wanted) ? wanted : LootTables.AIRDROP;
    }

    public static class Server {
        private static final String CENTER_MODE = "Take the spawn center from the world spawn point instead of centerX/centerZ";
        private static final String START_HEIGHT = "Height the drop starts from, raised when the ground is higher";
        private static final String FLIGHT = "Time the drop falls from its start height to the ground (ticks)";
        private static final String MATCH_ONLY = "Spawn drops by timer only while a match is running";
        private static final String CLEAR_DROPS = "Remove every airdrop when a match ends";
        private static final String ANNOUNCE = "Tell players the landing coordinates";
        private static final String TABLE = "Loot table the airdrop is filled from (config/airdrop_loot/<name>.json)";
        private static final String WARN = "Warn in chat X seconds before a filled drop despawns, 0 to stay silent";
        private static final String CACHE_CLEAR = "Empty loot containers when a match ends";
        private static final String CACHE_LOCK = "Players cannot open loot containers outside a match";

        public final ForgeConfigSpec.BooleanValue modEnabled;
        public final ForgeConfigSpec.BooleanValue autoSpawnEnabled;
        public final ForgeConfigSpec.BooleanValue matchOnly;
        public final ForgeConfigSpec.BooleanValue clearOnMatchEnd;
        public final ForgeConfigSpec.BooleanValue announceCoords;
        public final ForgeConfigSpec.ConfigValue<String> lootTable;
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
        public final ForgeConfigSpec.BooleanValue cacheClearOnMatchEnd;
        public final ForgeConfigSpec.BooleanValue cacheLockOutsideMatch;

        public Server(ForgeConfigSpec.Builder builder) {
            builder.push("general");
            modEnabled = flag(builder, "Enable or disable the entire mod", "modEnabled", true);
            autoSpawnEnabled = flag(builder, "Enable automatic spawning by timer", "autoSpawnEnabled", false);
            matchOnly = flag(builder, MATCH_ONLY, "matchOnly", true);
            clearOnMatchEnd = flag(builder, CLEAR_DROPS, "clearOnMatchEnd", true);
            announceCoords = flag(builder, ANNOUNCE, "announceCoords", true);
            lootTable = builder.comment(TABLE).define("lootTable", LootTables.AIRDROP);
            allowedDimensions = dimensions(builder);
            centerAtWorldSpawn = flag(builder, CENTER_MODE, "centerAtWorldSpawn", true);
            centerX = coordinate(builder, "Center X for random spawn", "centerX");
            centerZ = coordinate(builder, "Center Z for random spawn", "centerZ");
            spawnRadius = range(builder, "Radius around center", "spawnRadius", 5000, RADIUS_MIN, RADIUS_MAX);
            intervalSeconds = range(builder, "Auto-spawn interval (seconds)", "intervalSeconds", 3600,
                    INTERVAL_MIN, INTERVAL_MAX);
            intervalSpawnChance = builder.comment("Chance (0.0-1.0)").defineInRange("intervalSpawnChance", 1.0, 0.0, 1.0);
            maxSpawnY = range(builder, START_HEIGHT, "maxSpawnY", 350, HEIGHT_MIN, HEIGHT_MAX);
            flyingAnimTicks = ticks(builder, FLIGHT, "flyingAnimTicks", 1200, FLIGHT_MIN, FLIGHT_MAX);
            autoOpenDelayTicks = ticks(builder, "Delay before a landed drop opens (ticks)", "autoOpenDelayTicks",
                    100, 0, OPEN_DELAY_MAX);
            builder.pop().push("despawn");
            despawnEmptySeconds = range(builder, "Time to remove an emptied drop (seconds)", "despawnEmptySeconds",
                    60, DESPAWN_MIN, DESPAWN_MAX);
            despawnFilledSeconds = range(builder, "Time to remove a filled drop (seconds)", "despawnFilledSeconds",
                    300, DESPAWN_MIN, DESPAWN_MAX);
            notificationSecondsBeforeDespawn = range(builder, WARN, "notificationSecondsBeforeDespawn", 60, 0, WARN_MAX);
            builder.pop().push("caches");
            cacheClearOnMatchEnd = flag(builder, CACHE_CLEAR, "clearOnMatchEnd", true);
            cacheLockOutsideMatch = flag(builder, CACHE_LOCK, "lockOutsideMatch", true);
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
            return builder.comment(comment).defineInRange(key, 0.0, -COORDINATE, COORDINATE);
        }

        private static ForgeConfigSpec.IntValue range(ForgeConfigSpec.Builder builder, String comment, String key,
                                                      int fallback, int minimum, int maximum) {
            return builder.comment(comment).defineInRange(key, fallback, minimum, maximum);
        }

        private static ForgeConfigSpec.IntValue ticks(ForgeConfigSpec.Builder builder, String comment, String key,
                                                      int fallback, int minimumSeconds, int maximumSeconds) {
            return builder.comment(comment).defineInRange(key, fallback,
                    minimumSeconds * TICKS_PER_SECOND, maximumSeconds * TICKS_PER_SECOND);
        }
    }
}
