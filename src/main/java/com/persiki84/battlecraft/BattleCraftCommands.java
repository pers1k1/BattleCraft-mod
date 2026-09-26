package com.persiki84.battlecraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.persiki84.battlecraft.menu.BattleCraftMenuState;
import com.persiki84.battlecraft.modules.ModuleEvents;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import com.persiki84.battlecraft.rules.ConfigGuardCommand;
import com.persiki84.battlecraft.rules.GameRulesCommand;
import com.persiki84.battlecraft.rules.MarkerRangeCommand;
import com.persiki84.battlecraft.rules.GameRulesEvents;
import com.persiki84.itemmodifiers.AttributeHandler;
import com.persiki84.knockdown.events.ModEvents;
import com.persiki84.minimap.command.MapCommand;
import com.persiki84.shared.menu.MenuNetwork;
import com.persiki84.battlecraft.announce.AnnounceCommand;
import com.persiki84.zones.command.MarkCommand;
import com.persiki84.zones.command.ZoneCommand;
import com.persiki84.zones.shop.ShopCommand;
import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class BattleCraftCommands {

    private static final SuggestionProvider<CommandSourceStack> SCOREBOARD_TEAM_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getServer().getScoreboard().getTeamNames(), builder);
    private static final SuggestionProvider<CommandSourceStack> SURRENDER_CMD_SUGGESTIONS =
            suggestFrom(config -> config.surrenderCommands);
    private static final SuggestionProvider<CommandSourceStack> START_CMD_SUGGESTIONS =
            suggestFrom(config -> config.startCommands);
    private static final SuggestionProvider<CommandSourceStack> STOP_CMD_SUGGESTIONS =
            suggestFrom(config -> config.stopCommands);
    private static final SuggestionProvider<CommandSourceStack> COMMAND_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getServer().getCommands().getDispatcher()
                    .getRoot().getChildren().stream().map(CommandNode::getName), builder);

    private static SuggestionProvider<CommandSourceStack> suggestFrom(Function<BattleCraftConfig, List<String>> pick) {
        return (context, builder) -> SharedSuggestionProvider.suggest(pick.apply(config()), builder);
    }

    private static BattleCraftConfig config() {
        return BattleCraftManager.getInstance().getConfig();
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("battlecraft")
                .executes(context -> openMenu(context, BattleCraftMenuState.MENU_ID))
                .then(selectTeamBranch())
                .then(readyBranch())
                .then(surrenderBranch())
                .then(configBranch())
                .then(toggleBranch())
                .then(moduleBranch())
                .then(teamBranch())
                .then(forceBranch())
                .then(AnnounceCommand.build())
                .then(GameRulesCommand.build())
                .then(MarkerRangeCommand.build())
                .then(ConfigGuardCommand.build())
                .then(ZoneCommand.build())
                .then(MarkCommand.build())
                .then(MapCommand.build())
                .then(ShopCommand.build()));

        dispatcher.register(Commands.literal("bc")
                .executes(context -> openMenu(context, BattleCraftMenuState.MENU_ID))
                .redirect(root));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> selectTeamBranch() {
        return Commands.literal("select_team")
                .then(Commands.argument("team", StringArgumentType.word())
                        .suggests(SCOREBOARD_TEAM_SUGGESTIONS)
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            BattleCraftManager.getInstance()
                                    .selectTeam(player, StringArgumentType.getString(context, "team"));
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> readyBranch() {
        return Commands.literal("ready").executes(context -> {
            BattleCraftManager.getInstance().toggleReady(context.getSource().getPlayerOrException());
            return 1;
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> surrenderBranch() {
        return Commands.literal("surrender")
                .then(Commands.literal("start").executes(context -> {
                    BattleCraftManager.getInstance().startSurrenderVote(context.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("yes").executes(context -> castVote(context, true)))
                .then(Commands.literal("no").executes(context -> castVote(context, false)))
                .then(Commands.literal("info")
                        .requires(source -> source.hasPermission(2))
                        .executes(context ->
                                listCommands(context, "Surrender Commands:", config().surrenderCommands)))
                .then(addCommandBranch("surrender", config -> config.surrenderCommands))
                .then(removeCommandBranch(SURRENDER_CMD_SUGGESTIONS, config -> config.surrenderCommands));
    }

    private static int castVote(CommandContext<CommandSourceStack> context, boolean inFavour)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        BattleCraftManager.getInstance().castVote(context.getSource().getPlayerOrException(), inFavour);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> configBranch() {
        return Commands.literal("config")
                .requires(source -> source.hasPermission(2))
                .then(settingsBranch())
                .then(Commands.literal("lobbyhere").executes(BattleCraftCommands::lobbyHere))
                .then(Commands.literal("info").executes(BattleCraftCommands::describeConfig));
    }

    private static int lobbyHere(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (player.level().dimension() != Level.OVERWORLD) {
            context.getSource().sendFailure(Component.translatable("battlecraft.config.lobby_overworld_only"));
            return 0;
        }
        BattleCraftConfig config = config();

        config.lobbyX = player.getBlockX();
        config.lobbyY = player.getBlockY();
        config.lobbyZ = player.getBlockZ();
        config.lobbySet = true;
        config.save();
        return report(context, "battlecraft.config.lobby_set", config.lobbyX, config.lobbyY, config.lobbyZ);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> settingsBranch() {
        return Commands.literal("set")
                .then(setting("minPlayersToStart", 1, (config, value) -> config.minPlayersToStart = value))
                .then(setting("joinGraceSeconds", 0, (config, value) -> config.joinGraceSeconds = value))
                .then(setting("autoAssignSeconds", 5, (config, value) -> config.autoAssignSeconds = value))
                .then(setting("readyPercentToStart", 1, (config, value) -> config.readyPercentToStart = value))
                .then(setting("readyToggleCooldownSeconds", 0, (config, value) -> config.readyToggleCooldownSeconds = value))
                .then(setting("matchJoinChoiceSeconds", 0, (config, value) -> config.matchJoinChoiceSeconds = value))
                .then(setting("teamSwitchCooldownSeconds", 0, (config, value) -> config.teamSwitchCooldownSeconds = value))
                .then(setting("lobbyTimeLimit", 1, (config, value) -> config.lobbyTimeLimit = value))
                .then(setting("lobbyFastStartTime", 1, (config, value) -> config.lobbyFastStartTime = value))
                .then(setting("surrenderVoteTimeout", 1, (config, value) -> config.surrenderVoteTimeout = value))
                .then(setting("voteCooldown", 1, (config, value) -> config.voteCooldown = value))
                .then(setting("surrenderMinTime", 1, (config, value) -> config.surrenderMinTime = value))
                .then(setting("sprintStaminaDashPercent", 0, (config, value) -> config.sprintStaminaDashPercent = value))
                .then(setting("armedSprintStaminaDashPercent", 0, (config, value) -> config.armedSprintStaminaDashPercent = value))
                .then(setting("jumpStaminaPermille", 0, (config, value) -> config.jumpStaminaPermille = value))
                .then(setting("armedJumpStaminaPermille", 0, (config, value) -> config.armedJumpStaminaPermille = value))
                .then(setting("teamsNeeded", 1, (config, value) -> config.teamsNeeded = value))
                .then(Commands.literal("requireTeams")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(context -> applyRequireTeams(context,
                                        BoolArgumentType.getBool(context, "value")))))
                .then(lobbyCoordinate("lobbyX", (config, value) -> setLobbyX(config, value)))
                .then(lobbyCoordinate("lobbyY", (config, value) -> setLobbyY(config, value)))
                .then(lobbyCoordinate("lobbyZ", (config, value) -> setLobbyZ(config, value)));
    }

    private static void setLobbyX(BattleCraftConfig config, int value) {
        config.lobbyX = value;
        config.lobbySet = true;
    }

    private static void setLobbyY(BattleCraftConfig config, int value) {
        config.lobbyY = value;
        config.lobbySet = true;
    }

    private static void setLobbyZ(BattleCraftConfig config, int value) {
        config.lobbyZ = value;
        config.lobbySet = true;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> setting(String name, int minimum,
                                                                     BiConsumer<BattleCraftConfig, Integer> apply) {
        return Commands.literal(name)
                .then(Commands.argument("value", IntegerArgumentType.integer(minimum))
                        .executes(context -> {
                            int value = IntegerArgumentType.getInteger(context, "value");
                            apply.accept(config(), value);
                            config().save();
                            GameRulesEvents.syncToAll(context.getSource().getServer());
                            return report(context, "battlecraft.config.set", name, value);
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> lobbyCoordinate(String name,
                                                                             BiConsumer<BattleCraftConfig, Integer> apply) {
        return Commands.literal(name)
                .then(Commands.argument("value", IntegerArgumentType.integer())
                        .executes(context -> {
                            BattleCraftConfig config = config();
                            apply.accept(config, IntegerArgumentType.getInteger(context, "value"));
                            config.save();
                            return report(context, "battlecraft.config.lobby_set",
                                    config.lobbyX, config.lobbyY, config.lobbyZ);
                        }));
    }

    private static int describeConfig(CommandContext<CommandSourceStack> context) {
        BattleCraftConfig config = config();
        say(context, Component.translatable("battlecraft.info.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        line(context, "lobbyTimeLimit", config.lobbyTimeLimit);
        line(context, "lobbyFastStartTime", config.lobbyFastStartTime);
        line(context, "surrenderVoteTimeout", config.surrenderVoteTimeout);
        line(context, "voteCooldown", config.voteCooldown);
        line(context, "surrenderMinTime", config.surrenderMinTime);
        line(context, "minPlayersToStart", config.minPlayersToStart);

        listCommands(context, "Start Commands:", config.startCommands);
        listCommands(context, "Stop Commands:", config.stopCommands);
        listCommands(context, "Surrender Commands:", config.surrenderCommands);
        return 1;
    }

    private static void line(CommandContext<CommandSourceStack> context, String name, int value) {
        say(context, Component.literal(name + ": " + value).withStyle(ChatFormatting.WHITE));
    }

    private static int listCommands(CommandContext<CommandSourceStack> context, String header, List<String> entries) {
        say(context, Component.literal(header).withStyle(ChatFormatting.YELLOW));
        if (entries.isEmpty()) {
            say(context, Component.literal(" - None").withStyle(ChatFormatting.GRAY));
            return 1;
        }
        for (String entry : entries) {
            say(context, Component.literal(" - " + entry).withStyle(ChatFormatting.WHITE));
        }
        return 1;
    }

    private static int applyRequireTeams(CommandContext<CommandSourceStack> context, boolean required) {
        BattleCraftConfig config = config();
        config.requireTeams = required;
        config.save();

        context.getSource().sendSuccess(() -> Component.translatable(
                required ? "battlecraft.config.teams_required" : "battlecraft.config.teams_optional"), true);
        return 1;
    }

    private static int clearGrace(CommandContext<CommandSourceStack> context) {
        BattleCraftManager.getInstance().clearJoinGrace();
        context.getSource().sendSuccess(() -> Component.translatable("battlecraft.status.grace_cleared"), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> teamBranch() {
        return Commands.literal("team")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(BattleCraftCommands::addTeam)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(SCOREBOARD_TEAM_SUGGESTIONS)
                                .executes(BattleCraftCommands::removeTeam)));
    }

    private static int addTeam(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        Scoreboard scoreboard = context.getSource().getServer().getScoreboard();
        if (scoreboard.getPlayerTeam(name) != null) {
            context.getSource().sendFailure(Component.translatable("battlecraft.team.error.exists", name));
            return 0;
        }

        PlayerTeam team = scoreboard.addPlayerTeam(name);
        team.setDisplayName(Component.literal(name));
        context.getSource().sendSuccess(() -> Component.translatable("battlecraft.team.added", name), true);
        return 1;
    }

    private static int removeTeam(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        Scoreboard scoreboard = context.getSource().getServer().getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            context.getSource().sendFailure(Component.translatable("battlecraft.team.error.missing", name));
            return 0;
        }

        scoreboard.removePlayerTeam(team);
        context.getSource().sendSuccess(() -> Component.translatable("battlecraft.team.removed", name), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> moduleBranch() {
        LiteralArgumentBuilder<CommandSourceStack> branch = Commands.literal("module")
                .requires(source -> source.hasPermission(2))
                .executes(BattleCraftCommands::listModules);

        for (ModuleId module : ModuleId.values()) {
            branch = branch.then(Commands.literal(module.id())
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                            .executes(context -> switchModule(context, module,
                                    BoolArgumentType.getBool(context, "enabled")))));
        }
        return branch;
    }

    private static int switchModule(CommandContext<CommandSourceStack> context, ModuleId module, boolean enabled) {
        ModuleSwitches.set(module, enabled);
        ModuleEvents.syncToAll(context.getSource().getServer());
        settleSwitchedModule(context.getSource().getServer(), module, enabled);

        context.getSource().sendSuccess(() -> Component.translatable(
                enabled ? "battlecraft.module.switched_on" : "battlecraft.module.switched_off",
                Component.translatable(module.label())), true);
        return 1;
    }

    // WHY: модуль с живым состоянием на игроках не гаснет сам: ваниль сверяет модификаторы
    // WHY: атрибутов только на смене снаряжения, а сбитых тикает сам нокдаун, и выключенный он
    // WHY: оставлял их лежать с 1 HP навсегда
    private static void settleSwitchedModule(MinecraftServer server, ModuleId module, boolean enabled) {
        if (module == ModuleId.ITEM_MODIFIERS) AttributeHandler.reapplyAll(server);
        if (module == ModuleId.KNOCKDOWN && !enabled) ModEvents.reviveEveryone(server);
    }

    private static int listModules(CommandContext<CommandSourceStack> context) {
        say(context, Component.translatable("battlecraft.module.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        for (ModuleId module : ModuleId.values()) {
            boolean on = ModuleSwitches.allows(module);
            say(context, Component.translatable(module.label())
                    .append(": ")
                    .append(Component.translatable(on ? "battlecraft.menu.on" : "battlecraft.menu.off"))
                    .withStyle(on ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        }
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> toggleBranch() {
        return Commands.literal("toggle")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    boolean disabled = !BattleCraftManager.getInstance().isSoftDisabled();
                    BattleCraftManager.getInstance().setSoftDisabled(disabled);
                    context.getSource().sendSuccess(() -> Component.translatable(
                            disabled ? "battlecraft.status.disabled_msg" : "battlecraft.status.enabled_msg"), true);
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> forceBranch() {
        return Commands.literal("force")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("grace").executes(BattleCraftCommands::clearGrace))
                .then(Commands.literal("start")
                        .executes(context -> {
                            BattleCraftManager.getInstance()
                                    .forceStart(context.getSource().getServer(), context.getSource());
                            return 1;
                        })
                        .then(Commands.literal("info").executes(context ->
                                listCommands(context, "Start Commands:", config().startCommands)))
                        .then(addCommandBranch("start", config -> config.startCommands))
                        .then(removeCommandBranch(START_CMD_SUGGESTIONS, config -> config.startCommands)))
                .then(Commands.literal("stop")
                        .executes(context -> {
                            BattleCraftManager.getInstance().stopMatch(context.getSource().getServer(), null);
                            return 1;
                        })
                        .then(Commands.literal("info").executes(context ->
                                listCommands(context, "Stop Commands:", config().stopCommands)))
                        .then(addCommandBranch("stop", config -> config.stopCommands))
                        .then(removeCommandBranch(STOP_CMD_SUGGESTIONS, config -> config.stopCommands)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> addCommandBranch(
            String kind, Function<BattleCraftConfig, List<String>> pick) {
        return Commands.literal("addcommand")
                .requires(source -> source.hasPermission(4))
                .then(Commands.argument("command", StringArgumentType.greedyString())
                        .suggests(COMMAND_SUGGESTIONS)
                        .executes(context -> addCommand(context, kind, pick)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> removeCommandBranch(
            SuggestionProvider<CommandSourceStack> known, Function<BattleCraftConfig, List<String>> pick) {
        return Commands.literal("removecommand")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("command", StringArgumentType.greedyString())
                        .suggests(known)
                        .executes(context -> removeCommand(context, pick)));
    }

    private static int addCommand(CommandContext<CommandSourceStack> context, String kind,
                                  Function<BattleCraftConfig, List<String>> pick) {
        BattleCraftConfig config = config();
        pick.apply(config).add(StringArgumentType.getString(context, "command"));
        config.save();
        return report(context, "battlecraft.command.added", kind);
    }

    private static int removeCommand(CommandContext<CommandSourceStack> context,
                                     Function<BattleCraftConfig, List<String>> pick) {
        BattleCraftConfig config = config();
        if (!pick.apply(config).remove(StringArgumentType.getString(context, "command"))) {
            context.getSource().sendFailure(Component.translatable("battlecraft.command.not_found"));
            return 0;
        }
        config.save();
        return report(context, "battlecraft.command.removed");
    }

    public static int openMenu(CommandContext<CommandSourceStack> context, String menuId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return MenuNetwork.open(context.getSource().getPlayerOrException(), menuId) ? 1 : 0;
    }

    public static int openMenu(CommandContext<CommandSourceStack> context, String menuId, int tab)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return MenuNetwork.open(context.getSource().getPlayerOrException(), menuId, tab) ? 1 : 0;
    }

    private static void say(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendSuccess(() -> message, false);
    }

    private static int report(CommandContext<CommandSourceStack> context, String key, Object... args) {
        context.getSource().sendSuccess(() ->
                Component.translatable(key, args).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
