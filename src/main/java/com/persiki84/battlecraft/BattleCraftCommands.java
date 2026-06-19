package com.persiki84.battlecraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class BattleCraftCommands {

    private static final SuggestionProvider<CommandSourceStack> SCOREBOARD_TEAM_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getServer().getScoreboard().getTeamNames(), builder);
    private static final SuggestionProvider<CommandSourceStack> SURRENDER_CMD_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(BattleCraftManager.getInstance().getConfig().surrenderCommands, builder);
    private static final SuggestionProvider<CommandSourceStack> START_CMD_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(BattleCraftManager.getInstance().getConfig().startCommands, builder);
    private static final SuggestionProvider<CommandSourceStack> STOP_CMD_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(BattleCraftManager.getInstance().getConfig().stopCommands, builder);
    private static final SuggestionProvider<CommandSourceStack> COMMAND_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getServer().getCommands().getDispatcher().getRoot().getChildren().stream().map(com.mojang.brigadier.tree.CommandNode::getName), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("battlecraft")
                .then(Commands.literal("select_team")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests(SCOREBOARD_TEAM_SUGGESTIONS)
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String teamName = StringArgumentType.getString(ctx, "team");
                                    BattleCraftManager.getInstance().selectTeam(player, teamName);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("ready")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            BattleCraftManager.getInstance().toggleReady(player);
                            return 1;
                        })
                )
                .then(Commands.literal("surrender")
                        .then(Commands.literal("start")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    BattleCraftManager.getInstance().startSurrenderVote(player);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("yes")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    BattleCraftManager.getInstance().castVote(player, true);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("no")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    BattleCraftManager.getInstance().castVote(player, false);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("info")
                                .executes(ctx -> {
                                    BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Surrender Commands:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                                    if (config.surrenderCommands.isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal(" - None").withStyle(ChatFormatting.GRAY), false);
                                    } else {
                                        for (String s : config.surrenderCommands) {
                                            ctx.getSource().sendSuccess(() -> Component.literal(" - " + s).withStyle(ChatFormatting.WHITE), false);
                                        }
                                    }
                                    return 1;
                                })
                        )
                        .then(Commands.literal("addcommand")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                        .suggests(COMMAND_SUGGESTIONS)
                                        .executes(ctx -> {
                                            String cmd = StringArgumentType.getString(ctx, "command");
                                            BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                            config.surrenderCommands.add(cmd);
                                            config.save();
                                            ctx.getSource().sendSuccess(() -> Component.translatable("battlecraft.command.added", "surrender").withStyle(ChatFormatting.GREEN), true);
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("removecommand")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                        .suggests(SURRENDER_CMD_SUGGESTIONS)
                                        .executes(ctx -> {
                                            String cmd = StringArgumentType.getString(ctx, "command");
                                            BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                            if (config.surrenderCommands.remove(cmd)) {
                                                config.save();
                                                ctx.getSource().sendSuccess(() -> Component.translatable("battlecraft.command.removed").withStyle(ChatFormatting.GREEN), true);
                                            } else {
                                                ctx.getSource().sendFailure(Component.translatable("battlecraft.command.not_found"));
                                            }
                                            return 1;
                                        })
                                )
                        )
                )
                .then(Commands.literal("config")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.literal("lobbyTimeLimit")
                                        .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    int val = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value");
                                                    BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                                    config.lobbyTimeLimit = val;
                                                    config.save();
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("battlecraft.config.set", "lobbyTimeLimit", val).withStyle(ChatFormatting.GREEN), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("surrenderVoteTimeout")
                                        .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    int val = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value");
                                                    BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                                    config.surrenderVoteTimeout = val;
                                                    config.save();
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("battlecraft.config.set", "surrenderVoteTimeout", val).withStyle(ChatFormatting.GREEN), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("voteCooldown")
                                        .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    int val = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value");
                                                    BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                                    config.voteCooldown = val;
                                                    config.save();
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("battlecraft.config.set", "voteCooldown", val).withStyle(ChatFormatting.GREEN), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("surrenderMinTime")
                                        .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    int val = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value");
                                                    BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                                    config.surrenderMinTime = val;
                                                    config.save();
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("battlecraft.config.set", "surrenderMinTime", val).withStyle(ChatFormatting.GREEN), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("minPlayersToStart")
                                        .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    int val = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value");
                                                    BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                                    config.minPlayersToStart = val;
                                                    config.save();
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("battlecraft.config.set", "minPlayersToStart", val).withStyle(ChatFormatting.GREEN), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("lobbyFastStartTime")
                                        .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    int val = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value");
                                                    BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                                    config.lobbyFastStartTime = val;
                                                    config.save();
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("battlecraft.config.set", "lobbyFastStartTime", val).withStyle(ChatFormatting.GREEN), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("lobbyX")
                                        .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    int val = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value");
                                                    BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                                    config.lobbyX = val;
                                                    config.save();
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("battlecraft.config.lobby_set", config.lobbyX, config.lobbyY, config.lobbyZ).withStyle(ChatFormatting.GREEN), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("lobbyY")
                                        .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    int val = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value");
                                                    BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                                    config.lobbyY = val;
                                                    config.save();
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("battlecraft.config.lobby_set", config.lobbyX, config.lobbyY, config.lobbyZ).withStyle(ChatFormatting.GREEN), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("lobbyZ")
                                        .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    int val = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value");
                                                    BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                                    config.lobbyZ = val;
                                                    config.save();
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("battlecraft.config.lobby_set", config.lobbyX, config.lobbyY, config.lobbyZ).withStyle(ChatFormatting.GREEN), true);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("info")
                                .executes(ctx -> {
                                    BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("battlecraft.info.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                                    
                                    ctx.getSource().sendSuccess(() -> Component.literal("lobbyTimeLimit: " + config.lobbyTimeLimit).withStyle(ChatFormatting.WHITE), false);
                                    ctx.getSource().sendSuccess(() -> Component.literal("lobbyFastStartTime: " + config.lobbyFastStartTime).withStyle(ChatFormatting.WHITE), false);
                                    ctx.getSource().sendSuccess(() -> Component.literal("surrenderVoteTimeout: " + config.surrenderVoteTimeout).withStyle(ChatFormatting.WHITE), false);
                                    ctx.getSource().sendSuccess(() -> Component.literal("voteCooldown: " + config.voteCooldown).withStyle(ChatFormatting.WHITE), false);
                                    ctx.getSource().sendSuccess(() -> Component.literal("surrenderMinTime: " + config.surrenderMinTime).withStyle(ChatFormatting.WHITE), false);
                                    ctx.getSource().sendSuccess(() -> Component.literal("minPlayersToStart: " + config.minPlayersToStart).withStyle(ChatFormatting.WHITE), false);

                                    ctx.getSource().sendSuccess(() -> Component.literal("Start Commands:").withStyle(ChatFormatting.YELLOW), false);
                                    if (config.startCommands.isEmpty()) ctx.getSource().sendSuccess(() -> Component.literal(" - None").withStyle(ChatFormatting.GRAY), false);
                                    for (String s : config.startCommands) ctx.getSource().sendSuccess(() -> Component.literal(" - " + s).withStyle(ChatFormatting.WHITE), false);

                                    ctx.getSource().sendSuccess(() -> Component.literal("Stop Commands:").withStyle(ChatFormatting.YELLOW), false);
                                    if (config.stopCommands.isEmpty()) ctx.getSource().sendSuccess(() -> Component.literal(" - None").withStyle(ChatFormatting.GRAY), false);
                                    for (String s : config.stopCommands) ctx.getSource().sendSuccess(() -> Component.literal(" - " + s).withStyle(ChatFormatting.WHITE), false);

                                    ctx.getSource().sendSuccess(() -> Component.literal("Surrender Commands:").withStyle(ChatFormatting.YELLOW), false);
                                    if (config.surrenderCommands.isEmpty()) ctx.getSource().sendSuccess(() -> Component.literal(" - None").withStyle(ChatFormatting.GRAY), false);
                                    for (String s : config.surrenderCommands) ctx.getSource().sendSuccess(() -> Component.literal(" - " + s).withStyle(ChatFormatting.WHITE), false);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("toggle")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            boolean disabled = !BattleCraftManager.getInstance().isSoftDisabled();
                            BattleCraftManager.getInstance().setSoftDisabled(disabled);
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    disabled ? "battlecraft.status.disabled_msg" : "battlecraft.status.enabled_msg"
                            ), true);
                            return 1;
                        })
                )

                .then(Commands.literal("force")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("start")
                                .executes(ctx -> {
                                    BattleCraftManager.getInstance().forceStart(ctx.getSource().getServer(), ctx.getSource());
                                    return 1;
                                })
                                .then(Commands.literal("info")
                                        .executes(ctx -> {
                                            BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                            ctx.getSource().sendSuccess(() -> Component.literal("Start Commands:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                                            if (config.startCommands.isEmpty()) ctx.getSource().sendSuccess(() -> Component.literal(" - None").withStyle(ChatFormatting.GRAY), false);
                                            for (String s : config.startCommands) ctx.getSource().sendSuccess(() -> Component.literal(" - " + s).withStyle(ChatFormatting.WHITE), false);
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("addcommand")
                                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                                .suggests(COMMAND_SUGGESTIONS)
                                                .executes(ctx -> {
                                                    String cmd = StringArgumentType.getString(ctx, "command");
                                                    BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                                    config.startCommands.add(cmd);
                                                    config.save();
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("battlecraft.command.added", "start").withStyle(ChatFormatting.GREEN), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("removecommand")
                                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                                .suggests(START_CMD_SUGGESTIONS)
                                                .executes(ctx -> {
                                                    String cmd = StringArgumentType.getString(ctx, "command");
                                                    BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                                    if (config.startCommands.remove(cmd)) {
                                                        config.save();
                                                        ctx.getSource().sendSuccess(() -> Component.translatable("battlecraft.command.removed").withStyle(ChatFormatting.GREEN), true);
                                                    } else {
                                                        ctx.getSource().sendFailure(Component.translatable("battlecraft.command.not_found"));
                                                    }
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("stop")
                                .executes(ctx -> {
                                    BattleCraftManager.getInstance().stopMatch(ctx.getSource().getServer(), null);
                                    return 1;
                                })
                                .then(Commands.literal("info")
                                        .executes(ctx -> {
                                            BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                            ctx.getSource().sendSuccess(() -> Component.literal("Stop Commands:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                                            if (config.stopCommands.isEmpty()) ctx.getSource().sendSuccess(() -> Component.literal(" - None").withStyle(ChatFormatting.GRAY), false);
                                            for (String s : config.stopCommands) ctx.getSource().sendSuccess(() -> Component.literal(" - " + s).withStyle(ChatFormatting.WHITE), false);
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("addcommand")
                                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                                .suggests(COMMAND_SUGGESTIONS)
                                                .executes(ctx -> {
                                                    String cmd = StringArgumentType.getString(ctx, "command");
                                                    BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                                    config.stopCommands.add(cmd);
                                                    config.save();
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("battlecraft.command.added", "stop").withStyle(ChatFormatting.GREEN), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("removecommand")
                                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                                .suggests(STOP_CMD_SUGGESTIONS)
                                                .executes(ctx -> {
                                                    String cmd = StringArgumentType.getString(ctx, "command");
                                                    BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
                                                    if (config.stopCommands.remove(cmd)) {
                                                        config.save();
                                                        ctx.getSource().sendSuccess(() -> Component.translatable("battlecraft.command.removed").withStyle(ChatFormatting.GREEN), true);
                                                    } else {
                                                        ctx.getSource().sendFailure(Component.translatable("battlecraft.command.not_found"));
                                                    }
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
        );
    }
}
