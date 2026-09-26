package com.persiki84.airdrop.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.airdrop.cache.CacheContainers;
import com.persiki84.airdrop.cache.CacheTicker;
import com.persiki84.airdrop.cache.CacheTier;
import com.persiki84.airdrop.cache.LootCache;
import com.persiki84.airdrop.cache.LootCaches;
import com.persiki84.airdrop.cache.RefillMode;
import com.persiki84.airdrop.loot.LootTables;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public final class CacheCommands {
    private static final SuggestionProvider<CommandSourceStack> IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(ids(), builder);
    private static final SuggestionProvider<CommandSourceStack> TIERS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(CacheTier.values()).map(CacheTier::id), builder);
    private static final SuggestionProvider<CommandSourceStack> MODES = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(RefillMode.values()).map(RefillMode::id), builder);

    private CacheCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("cache")
                .then(Commands.literal("list").executes(CacheCommands::list))
                .then(Commands.literal("fill_all").executes(CacheCommands::fillAll))
                .then(Commands.literal("add").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> add(context, LootTables.AIRDROP))
                        .then(Commands.argument("table", StringArgumentType.word()).suggests(LootCommands.TABLES)
                                .executes(context -> add(context, StringArgumentType.getString(context, "table"))))))
                .then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(IDS)
                        .then(Commands.literal("remove").executes(CacheCommands::remove))
                        .then(Commands.literal("fill").executes(context -> fill(context, true)))
                        .then(Commands.literal("empty").executes(context -> fill(context, false)))
                        .then(Commands.literal("tp").executes(CacheCommands::teleport))
                        .then(tableBranch())
                        .then(tierBranch())
                        .then(refillBranch()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tableBranch() {
        return Commands.literal("table").then(Commands.argument("table", StringArgumentType.word())
                .suggests(LootCommands.TABLES).executes(context -> {
                    String table = StringArgumentType.getString(context, "table");
                    if (!LootTables.exists(table)) return fail(context, "airdrop.loot.no_table", table);
                    return change(context, cache -> cache.table(table), "airdrop.cache.table_set", table);
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tierBranch() {
        return Commands.literal("tier").then(Commands.argument("tier", StringArgumentType.word()).suggests(TIERS)
                .executes(context -> {
                    String written = StringArgumentType.getString(context, "tier");
                    CacheTier tier = CacheTier.parse(written);
                    if (tier == null) return fail(context, "airdrop.cache.bad_tier", written);
                    return change(context, cache -> cache.tier(tier), "airdrop.cache.tier_set",
                            Component.translatable(tier.label()));
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> refillBranch() {
        RequiredArgumentBuilder<CommandSourceStack, String> mode = Commands.argument("mode", StringArgumentType.word())
                .suggests(MODES)
                .executes(context -> refill(context, -1))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(LootCache.REFILL_MIN, LootCache.REFILL_MAX))
                        .executes(context -> refill(context, IntegerArgumentType.getInteger(context, "seconds"))));
        return Commands.literal("refill").then(mode);
    }

    private static int refill(CommandContext<CommandSourceStack> context, int seconds) {
        String written = StringArgumentType.getString(context, "mode");
        RefillMode mode = RefillMode.parse(written);
        if (mode == null) return fail(context, "airdrop.cache.bad_refill", written);
        return change(context, cache -> {
            cache.refill(mode);
            if (seconds > 0) cache.refillSeconds(seconds);
        }, "airdrop.cache.refill_set", Component.translatable(mode.label()));
    }

    private static int add(CommandContext<CommandSourceStack> context, String table) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        if (!LootTables.exists(table)) return fail(context, "airdrop.loot.no_table", table);
        if (!CacheContainers.accepts(level, pos)) return fail(context, "airdrop.cache.not_container");
        if (CacheContainers.covering(level, pos) != null) return fail(context, "airdrop.cache.already");
        if (LootCaches.size() >= LootCaches.LIMIT) return fail(context, "airdrop.cache.limit", LootCaches.LIMIT);

        LootCache cache = LootCaches.add(level, pos);
        cache.table(table);
        LootCaches.save();
        if (CacheTicker.running()) CacheTicker.fillNow(context.getSource().getServer(), cache);
        return report(context, "airdrop.cache.added", ChatFormatting.GREEN, cache.id(), pos.toShortString(), table);
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        LootCache cache = LootCaches.remove(IntegerArgumentType.getInteger(context, "id"));
        if (cache == null) return fail(context, "airdrop.cache.unknown");

        CacheTicker.emptyIfLoaded(context.getSource().getServer(), cache);
        LootCaches.save();
        return report(context, "airdrop.cache.removed", ChatFormatting.YELLOW, cache.id());
    }

    private static int fill(CommandContext<CommandSourceStack> context, boolean filling) {
        LootCache cache = LootCaches.get(IntegerArgumentType.getInteger(context, "id"));
        if (cache == null) return fail(context, "airdrop.cache.unknown");

        var server = context.getSource().getServer();
        boolean done = filling ? CacheTicker.fillNow(server, cache) : CacheTicker.emptyNow(server, cache);
        if (!done) return fail(context, "airdrop.cache.missing", cache.id());

        LootCaches.save();
        return report(context, filling ? "airdrop.cache.filled" : "airdrop.cache.emptied", ChatFormatting.GREEN, cache.id());
    }

    private static int fillAll(CommandContext<CommandSourceStack> context) {
        var server = context.getSource().getServer();
        int filled = 0;
        int queued = 0;
        for (LootCache cache : LootCaches.all()) {
            if (CacheTicker.queueIfUnloaded(server, cache)) {
                queued++;
            } else if (CacheTicker.fillNow(server, cache)) {
                filled++;
            }
        }
        LootCaches.save();
        if (queued == 0) return report(context, "airdrop.cache.filled_all", ChatFormatting.GREEN, filled, LootCaches.size());
        return report(context, "airdrop.cache.filled_all_queued", ChatFormatting.GREEN, filled, queued, LootCaches.size());
    }

    private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        LootCache cache = LootCaches.get(IntegerArgumentType.getInteger(context, "id"));
        if (cache == null) return fail(context, "airdrop.cache.unknown");

        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = CacheTicker.levelOf(context.getSource().getServer(), cache);
        if (level == null) return fail(context, "airdrop.cache.missing", cache.id());

        BlockPos pos = cache.pos();
        player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, player.getYRot(), player.getXRot());
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        List<LootCache> caches = LootCaches.all();
        if (caches.isEmpty()) return report(context, "airdrop.cache.none", ChatFormatting.GRAY);

        for (LootCache cache : caches) {
            context.getSource().sendSuccess(() -> Component.translatable("airdrop.cache.line", cache.id(),
                    cache.pos().toShortString(), cache.dimension(), cache.table(),
                    Component.translatable(cache.tier().label()), Component.translatable(cache.refill().label()))
                    .withStyle(cache.missing() ? ChatFormatting.RED : ChatFormatting.WHITE), false);
        }
        return caches.size();
    }

    private static int change(CommandContext<CommandSourceStack> context, Consumer<LootCache> edit,
                              String key, Object shown) {
        LootCache cache = LootCaches.get(IntegerArgumentType.getInteger(context, "id"));
        if (cache == null) return fail(context, "airdrop.cache.unknown");

        edit.accept(cache);
        LootCaches.save();
        return report(context, key, ChatFormatting.AQUA, cache.id(), shown);
    }

    private static List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (LootCache cache : LootCaches.view()) {
            ids.add(String.valueOf(cache.id()));
        }
        return ids;
    }

    private static int report(CommandContext<CommandSourceStack> context, String key, ChatFormatting color,
                              Object... args) {
        context.getSource().sendSuccess(() -> Component.translatable(key, args).withStyle(color), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> context, String key, Object... args) {
        context.getSource().sendFailure(Component.translatable(key, args));
        return 0;
    }
}
