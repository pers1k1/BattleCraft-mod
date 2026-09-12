package com.persiki84.airdrop.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.persiki84.battlecraft.BattleCraftCommands;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.airdrop.config.AirDropConfig;
import com.persiki84.airdrop.entity.AirDropEntity;
import com.persiki84.airdrop.entity.ModEntities;
import com.persiki84.airdrop.loot.AirDropLootManager;
import com.persiki84.airdrop.scheduler.AirDropScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class AirDropCommands {

    private AirDropCommands() {}

    private static final ResourceLocation GLOBAL_TABLE = new ResourceLocation("airdrop", "global");
    private static final int MIN_MANUAL_DROP_HEIGHT = 10;
    private static final int MAX_COORDINATE = 30000000;
    private static final int TICKS_PER_SECOND = 20;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("airdrop")
                .requires(source -> source.hasPermission(2))
                .executes(context -> BattleCraftCommands.openMenu(context, ModuleMenuStates.AIRDROP))
                .then(Commands.literal("info").executes(AirDropCommands::info))
                .then(Commands.literal("kill_all").executes(AirDropCommands::killAll))
                .then(Commands.literal("reload").executes(AirDropCommands::reload))
                .then(Commands.literal("now").executes(AirDropCommands::dropNow))
                .then(toggleBranch())
                .then(spawnBranch())
                .then(configBranch())
                .then(AirDropLootCommands.build(GLOBAL_TABLE, buildContext)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> toggleBranch() {
        return Commands.literal("toggle")
                .then(Commands.literal("mod").then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> toggle(context, AirDropConfig.SERVER.modEnabled,
                                "airdrop.toggle.mod_enabled", "airdrop.toggle.mod_disabled"))))
                .then(Commands.literal("spawn").then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> toggle(context, AirDropConfig.SERVER.autoSpawnEnabled,
                                "airdrop.toggle.spawn_enabled", "airdrop.toggle.spawn_disabled"))));
    }

    private static int toggle(CommandContext<CommandSourceStack> context,
                              ForgeConfigSpec.ConfigValue<Boolean> value, String enabledKey, String disabledKey) {
        boolean wanted = BoolArgumentType.getBool(context, "enabled");
        value.set(wanted);
        value.save();

        context.getSource().sendSuccess(() -> Component.translatable(wanted ? enabledKey : disabledKey)
                .withStyle(wanted ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spawnBranch() {
        return Commands.literal("spawn")
                .executes(context -> spawnManual(context.getSource(), null))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> spawnManual(context.getSource(),
                                BlockPosArgument.getLoadedBlockPos(context, "pos"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> configBranch() {
        return Commands.literal("config")
                .then(centerBranch())
                .then(seconds("radius", 10, 30000, "airdrop.config.radius_set",
                        blocks -> save(AirDropConfig.SERVER.spawnRadius, blocks)))
                .then(seconds("interval", 5, 86400, "airdrop.config.interval_set",
                        value -> save(AirDropConfig.SERVER.intervalSeconds, value)))
                .then(percent("chance", "airdrop.config.chance_set"))
                .then(seconds("flight_time", 0, 300, "airdrop.config.flight_time_set",
                        value -> save(AirDropConfig.SERVER.flyingAnimTicks, value * TICKS_PER_SECOND)))
                .then(seconds("open_delay", 0, 300, "airdrop.config.open_delay_set",
                        value -> save(AirDropConfig.SERVER.autoOpenDelayTicks, value * TICKS_PER_SECOND)))
                .then(seconds("despawn_empty", 0, 86400, "airdrop.config.despawn_empty_set",
                        value -> save(AirDropConfig.SERVER.despawnEmptySeconds, value)))
                .then(seconds("despawn_filled", 0, 86400, "airdrop.config.despawn_filled_set",
                        value -> save(AirDropConfig.SERVER.despawnFilledSeconds, value)))
                .then(seconds("warn_time", 0, 3600, "airdrop.config.warn_time_set",
                        value -> save(AirDropConfig.SERVER.notificationSecondsBeforeDespawn, value)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> centerBranch() {
        return Commands.literal("center")
                .then(Commands.literal("here").executes(context -> {
                    BlockPos pos = BlockPos.containing(context.getSource().getPosition());
                    return moveCenter(context, pos.getX(), pos.getZ());
                }))
                .then(Commands.argument("x", IntegerArgumentType.integer(-MAX_COORDINATE, MAX_COORDINATE))
                        .then(Commands.argument("z", IntegerArgumentType.integer(-MAX_COORDINATE, MAX_COORDINATE))
                                .executes(context -> moveCenter(context,
                                        IntegerArgumentType.getInteger(context, "x"),
                                        IntegerArgumentType.getInteger(context, "z")))));
    }

    private static int moveCenter(CommandContext<CommandSourceStack> context, int x, int z) {
        save(AirDropConfig.SERVER.centerX, (double) x);
        save(AirDropConfig.SERVER.centerZ, (double) z);
        context.getSource().sendSuccess(() -> Component.translatable("airdrop.config.center_set", x, z)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> seconds(String name, int minimum, int maximum,
                                                                      String key, IntConsumer apply) {
        return Commands.literal(name)
                .then(Commands.argument("value", IntegerArgumentType.integer(minimum, maximum))
                        .executes(context -> {
                            int value = IntegerArgumentType.getInteger(context, "value");
                            apply.accept(value);
                            context.getSource().sendSuccess(() -> Component.translatable(key, value)
                                    .withStyle(ChatFormatting.AQUA), true);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> percent(String name, String key) {
        return Commands.literal(name)
                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                        .executes(context -> {
                            int value = IntegerArgumentType.getInteger(context, "value");
                            save(AirDropConfig.SERVER.intervalSpawnChance, value / 100.0);
                            context.getSource().sendSuccess(() -> Component.translatable(key, value)
                                    .withStyle(ChatFormatting.AQUA), true);
                            return 1;
                        }));
    }

    private static <T> void save(ForgeConfigSpec.ConfigValue<T> config, T value) {
        config.set(value);
        config.save();
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.translatable("airdrop.info.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        reportState(source);
        reportZone(source);
        return 1;
    }

    private static void reportState(CommandSourceStack source) {
        boolean modEnabled = AirDropConfig.SERVER.modEnabled.get();
        boolean spawnEnabled = AirDropConfig.SERVER.autoSpawnEnabled.get();

        source.sendSuccess(() -> Component.translatable("airdrop.info.mod_status")
                .append(Component.translatable(modEnabled ? "airdrop.info.enabled" : "airdrop.info.disabled")
                        .withStyle(modEnabled ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        source.sendSuccess(() -> Component.translatable("airdrop.info.auto_spawn")
                .append(Component.translatable(spawnEnabled ? "airdrop.info.active" : "airdrop.info.inactive")
                        .withStyle(spawnEnabled ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
    }

    private static void reportZone(CommandSourceStack source) {
        int x = (int) (double) AirDropConfig.SERVER.centerX.get();
        int z = (int) (double) AirDropConfig.SERVER.centerZ.get();
        int radius = AirDropConfig.SERVER.spawnRadius.get();
        int interval = AirDropConfig.SERVER.intervalSeconds.get();
        int chance = (int) (AirDropConfig.SERVER.intervalSpawnChance.get() * 100);
        int flight = AirDropConfig.SERVER.flyingAnimTicks.get() / TICKS_PER_SECOND;
        int loot = AirDropLootManager.getTable(GLOBAL_TABLE).size();

        source.sendSuccess(() -> Component.translatable("airdrop.info.zone").withStyle(ChatFormatting.YELLOW)
                .append(Component.translatable("airdrop.info.zone_detail", x, z, radius)
                        .withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.translatable("airdrop.info.timer").withStyle(ChatFormatting.AQUA)
                .append(Component.translatable("airdrop.info.timer_detail", interval, chance)
                        .withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.translatable("airdrop.info.flight_time").withStyle(ChatFormatting.AQUA)
                .append(Component.translatable("airdrop.info.flight_time_value", flight)
                        .withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.translatable("airdrop.info.loot_items")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(String.valueOf(loot)).withStyle(ChatFormatting.WHITE)), false);
    }

    private static int killAll(CommandContext<CommandSourceStack> context) {
        int removed = 0;
        for (ServerLevel level : context.getSource().getServer().getAllLevels()) {
            removed += discardDrops(level);
        }

        int total = removed;
        context.getSource().sendSuccess(() -> Component.translatable("airdrop.kill_all.success", total)
                .withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private static int discardDrops(ServerLevel level) {
        List<Entity> drops = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof AirDropEntity) drops.add(entity);
        }
        for (Entity drop : drops) {
            drop.discard();
        }
        return drops.size();
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        AirDropLootManager.reload(FMLPaths.CONFIGDIR.get());
        context.getSource().sendSuccess(() -> Component.translatable("commands.airdrop.loot_reloaded")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int dropNow(CommandContext<CommandSourceStack> context) {
        if (!AirDropConfig.SERVER.modEnabled.get()) {
            context.getSource().sendFailure(Component.translatable("airdrop.command.mod_disabled"));
            return 0;
        }

        BlockPos landing = AirDropScheduler.spawn(context.getSource().getLevel(), null);
        if (landing == null) {
            context.getSource().sendFailure(Component.translatable("airdrop.now.failed"));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable("airdrop.now.success")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.translatable("airdrop.now.landing_pos", landing.getX(), landing.getY(),
                        landing.getZ()).withStyle(ChatFormatting.GOLD)), true);
        return 1;
    }

    private static int spawnManual(CommandSourceStack source, BlockPos wanted) {
        if (!AirDropConfig.SERVER.modEnabled.get()) {
            source.sendFailure(Component.translatable("airdrop.command.mod_disabled"));
            return 0;
        }

        ServerLevel level = source.getLevel();
        double baseX = wanted != null ? wanted.getX() + 0.5 : source.getPosition().x;
        double baseZ = wanted != null ? wanted.getZ() + 0.5 : source.getPosition().z;

        level.addFreshEntity(createDrop(level, baseX, baseZ));
        source.sendSuccess(() -> Component.translatable("airdrop.spawn.success").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // WHY: ручной спавн ставил ящик ровно на maxSpawnY, поэтому на рельефе выше этой отметки он
    // WHY: появлялся внутри горы; планировщик рядом уже поднимает точку старта над землёй
    private static AirDropEntity createDrop(ServerLevel level, double baseX, double baseZ) {
        BlockPos landingPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos((int) baseX, 0, (int) baseZ));
        int spawnY = Math.max(AirDropConfig.SERVER.maxSpawnY.get(),
                landingPos.getY() + (int) MIN_MANUAL_DROP_HEIGHT);

        double distance = Math.max(MIN_MANUAL_DROP_HEIGHT, spawnY - landingPos.getY());
        int descentTicks = AirDropScheduler.descentTicks(AirDropConfig.SERVER.flyingAnimTicks.get());

        AirDropEntity entity = new AirDropEntity(ModEntities.AIRDROP.get(), level);
        entity.setPos((int) baseX + 0.5, spawnY, (int) baseZ + 0.5);
        entity.setYRot(level.random.nextFloat() * 360f);
        entity.setFallSpeed(AirDropScheduler.descentSpeed(distance, descentTicks));
        entity.setFlyingAnimTicks(descentTicks);

        AirDropLootManager.fillInventory(level.random, entity.getInventory(), GLOBAL_TABLE);
        return entity;
    }
}
