package com.persiki84.airdrop.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.persiki84.airdrop.cache.LootCaches;
import com.persiki84.airdrop.config.AirDropConfig;
import com.persiki84.airdrop.entity.AirDropEntity;
import com.persiki84.airdrop.loot.LootTables;
import com.persiki84.airdrop.scheduler.AirDropScheduler;
import com.persiki84.battlecraft.BattleCraftCommands;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.function.IntConsumer;

import static com.persiki84.airdrop.config.AirDropLimits.*;

public final class AirDropCommands {

    private AirDropCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("airdrop")
                .requires(source -> source.hasPermission(2))
                .executes(context -> BattleCraftCommands.openMenu(context, ModuleMenuStates.AIRDROP))
                .then(Commands.literal("editor").executes(context ->
                        BattleCraftCommands.openMenu(context, ModuleMenuStates.LOOT)))
                .then(Commands.literal("info").executes(AirDropInfo::info))
                .then(Commands.literal("kill_all").executes(AirDropCommands::killAll))
                .then(Commands.literal("reload").executes(AirDropCommands::reload))
                .then(Commands.literal("now").executes(AirDropCommands::dropNow))
                .then(toggleBranch())
                .then(spawnBranch())
                .then(configBranch())
                .then(LootCommands.build(buildContext))
                .then(CacheCommands.build()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> toggleBranch() {
        AirDropConfig.Server config = AirDropConfig.SERVER;
        return Commands.literal("toggle")
                .then(flag("mod", config.modEnabled, "airdrop.toggle.mod"))
                .then(flag("spawn", config.autoSpawnEnabled, "airdrop.toggle.spawn"))
                .then(flag("match_only", config.matchOnly, "airdrop.toggle.match_only"))
                .then(flag("clear_on_end", config.clearOnMatchEnd, "airdrop.toggle.clear_on_end"))
                .then(flag("announce", config.announceCoords, "airdrop.toggle.announce"))
                .then(flag("world_center", config.centerAtWorldSpawn, "airdrop.toggle.world_center"))
                .then(flag("cache_clear", config.cacheClearOnMatchEnd, "airdrop.toggle.cache_clear"))
                .then(flag("cache_lock", config.cacheLockOutsideMatch, "airdrop.toggle.cache_lock"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> flag(String name, ForgeConfigSpec.BooleanValue value,
                                                                   String key) {
        return Commands.literal(name).then(Commands.argument("enabled", BoolArgumentType.bool())
                .executes(context -> {
                    boolean wanted = BoolArgumentType.getBool(context, "enabled");
                    AirDropConfig.save(value, wanted);
                    return report(context, key + (wanted ? ".on" : ".off"), wanted ? ChatFormatting.GREEN : ChatFormatting.RED);
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spawnBranch() {
        return Commands.literal("spawn")
                .executes(context -> spawnManual(context, BlockPos.containing(context.getSource().getPosition())))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> spawnManual(context, BlockPosArgument.getLoadedBlockPos(context, "pos"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> configBranch() {
        AirDropConfig.Server config = AirDropConfig.SERVER;
        return Commands.literal("config")
                .then(centerBranch())
                .then(Commands.literal("table").then(Commands.argument("table", StringArgumentType.word())
                        .suggests(LootCommands.TABLES).executes(AirDropCommands::setTable)))
                .then(number("radius", RADIUS_MIN, RADIUS_MAX, "airdrop.config.radius_set",
                        value -> AirDropConfig.save(config.spawnRadius, value)))
                .then(number("interval", INTERVAL_MIN, INTERVAL_MAX, "airdrop.config.interval_set",
                        value -> AirDropConfig.save(config.intervalSeconds, value)))
                .then(number("chance", 0, PERCENT, "airdrop.config.chance_set",
                        value -> AirDropConfig.save(config.intervalSpawnChance, value / (double) PERCENT)))
                .then(number("height", HEIGHT_MIN, HEIGHT_MAX, "airdrop.config.height_set",
                        value -> AirDropConfig.save(config.maxSpawnY, value)))
                .then(number("flight_time", FLIGHT_MIN, FLIGHT_MAX, "airdrop.config.flight_time_set",
                        value -> AirDropConfig.save(config.flyingAnimTicks, value * TICKS_PER_SECOND)))
                .then(number("open_delay", 0, OPEN_DELAY_MAX, "airdrop.config.open_delay_set",
                        value -> AirDropConfig.save(config.autoOpenDelayTicks, value * TICKS_PER_SECOND)))
                .then(number("despawn_empty", DESPAWN_MIN, DESPAWN_MAX, "airdrop.config.despawn_empty_set",
                        value -> AirDropConfig.save(config.despawnEmptySeconds, value)))
                .then(number("despawn_filled", DESPAWN_MIN, DESPAWN_MAX, "airdrop.config.despawn_filled_set",
                        value -> AirDropConfig.save(config.despawnFilledSeconds, value)))
                .then(number("warn_time", 0, WARN_MAX, "airdrop.config.warn_time_set",
                        value -> AirDropConfig.save(config.notificationSecondsBeforeDespawn, value)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> centerBranch() {
        return Commands.literal("center")
                .then(Commands.literal("here").executes(context -> {
                    BlockPos pos = BlockPos.containing(context.getSource().getPosition());
                    return moveCenter(context, pos.getX(), pos.getZ());
                }))
                .then(Commands.argument("x", IntegerArgumentType.integer(-COORDINATE, COORDINATE))
                        .then(Commands.argument("z", IntegerArgumentType.integer(-COORDINATE, COORDINATE))
                                .executes(context -> moveCenter(context,
                                        IntegerArgumentType.getInteger(context, "x"),
                                        IntegerArgumentType.getInteger(context, "z")))));
    }

    // WHY: центр по умолчанию берётся от точки спавна мира, и заданный руками центр ни на что не
    // WHY: влиял, пока флаг стоял: установка центра обязана этот флаг снимать
    private static int moveCenter(CommandContext<CommandSourceStack> context, int x, int z) {
        AirDropConfig.save(AirDropConfig.SERVER.centerX, (double) x);
        AirDropConfig.save(AirDropConfig.SERVER.centerZ, (double) z);
        AirDropConfig.save(AirDropConfig.SERVER.centerAtWorldSpawn, false);
        return report(context, "airdrop.config.center_set", ChatFormatting.GREEN, x, z);
    }

    private static int setTable(CommandContext<CommandSourceStack> context) {
        String table = StringArgumentType.getString(context, "table");
        if (!LootTables.exists(table)) return LootCommands.fail(context, "airdrop.loot.no_table", table);

        AirDropConfig.save(AirDropConfig.SERVER.lootTable, table);
        return report(context, "airdrop.config.table_set", ChatFormatting.AQUA, table);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> number(String name, int minimum, int maximum,
                                                                     String key, IntConsumer apply) {
        return Commands.literal(name)
                .then(Commands.argument("value", IntegerArgumentType.integer(minimum, maximum))
                        .executes(context -> {
                            int value = IntegerArgumentType.getInteger(context, "value");
                            apply.accept(value);
                            return report(context, key, ChatFormatting.AQUA, value);
                        }));
    }

    private static int killAll(CommandContext<CommandSourceStack> context) {
        int removed = AirDropEntity.discardAll(context.getSource().getServer());
        return report(context, "airdrop.kill_all.success", ChatFormatting.RED, removed);
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        LootTables.reload(FMLPaths.CONFIGDIR.get());
        LootCaches.markDirty();
        return report(context, "commands.airdrop.loot_reloaded", ChatFormatting.GREEN);
    }

    private static int dropNow(CommandContext<CommandSourceStack> context) {
        if (!AirDropConfig.active()) return LootCommands.fail(context, "airdrop.command.mod_disabled");

        BlockPos landing = AirDropScheduler.spawn(context.getSource().getLevel(), null);
        if (landing == null) return LootCommands.fail(context, "airdrop.now.failed");
        return report(context, "airdrop.now.success", ChatFormatting.GREEN, landing.getX(), landing.getY(), landing.getZ());
    }

    private static int spawnManual(CommandContext<CommandSourceStack> context, BlockPos wanted) {
        if (!AirDropConfig.active()) return LootCommands.fail(context, "airdrop.command.mod_disabled");

        BlockPos landing = AirDropScheduler.spawn(context.getSource().getLevel(), wanted);
        if (landing == null) return LootCommands.fail(context, "airdrop.now.failed");
        return report(context, "airdrop.now.success", ChatFormatting.GREEN, landing.getX(), landing.getY(), landing.getZ());
    }

    private static int report(CommandContext<CommandSourceStack> context, String key, ChatFormatting color,
                              Object... args) {
        return LootCommands.report(context, key, color, args);
    }
}
