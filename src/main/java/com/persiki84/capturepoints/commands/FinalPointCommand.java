package com.persiki84.capturepoints.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.battlecraft.BattleCraftCommands;
import com.persiki84.capturepoints.capture.FinalCapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.event.BlockProtectionHandler;
import com.persiki84.capturepoints.menu.CapturePointMenuState;
import com.persiki84.capturepoints.util.TeamUtil;
import com.persiki84.shared.zone.ZoneArea;
import com.persiki84.shared.zone.ZoneShape;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.List;

public class FinalPointCommand {
    private static final SuggestionProvider<CommandSourceStack> FINAL_POINT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(CapturePointManager.getAllFinalPoints().stream().map(FinalCapturePoint::getName), builder);

    private static final SuggestionProvider<CommandSourceStack> SCOREBOARD_TEAM_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getServer().getScoreboard().getTeamNames(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("finalpoint")
                .requires(source -> source.hasPermission(2))
                .executes(source -> BattleCraftCommands.openMenu(source, CapturePointMenuState.MENU_ID));

        addLifecycleBranches(root);
        addGeometryBranches(root);
        addTimingBranches(root);
        addCommandBlockBranches(root);
        addToggleBranches(root);
        PointBonusCommands.addBranches(root, FINAL_POINT_SUGGESTIONS, CapturePointManager::getFinalPoint, context);
        CaptureTuningCommands.addBranches(root, FINAL_POINT_SUGGESTIONS, CapturePointManager::getFinalPoint);

        dispatcher.register(root);
    }

    private static void addLifecycleBranches(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(createBranch())
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(FINAL_POINT_SUGGESTIONS)
                                .executes(FinalPointCommand::removePoint)))
                .then(Commands.literal("list")
                        .executes(FinalPointCommand::listPoints))
                .then(Commands.literal("resetall")
                        .executes(FinalPointCommand::resetAllFinalPoints))
                .then(Commands.literal("setowner")
                        .then(Commands.argument("pointName", StringArgumentType.string())
                                .suggests(FINAL_POINT_SUGGESTIONS)
                                .then(Commands.argument("teamName", StringArgumentType.string())
                                        .suggests(SCOREBOARD_TEAM_SUGGESTIONS)
                                        .executes(FinalPointCommand::setOwner))))
                .then(Commands.literal("clearowner")
                        .then(Commands.argument("pointName", StringArgumentType.string())
                                .suggests(FINAL_POINT_SUGGESTIONS)
                                .executes(FinalPointCommand::clearOwner)));
    }

    private static void addGeometryBranches(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(radiusBranch())
                .then(shapeBranch())
                .then(heightBranch())
                .then(resetHeightBranch());
    }

    private static void addTimingBranches(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("setcapturetime")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(FINAL_POINT_SUGGESTIONS)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, PointCommands.MAX_SECONDS))
                                        .executes(FinalPointCommand::setCaptureTime))))
                .then(Commands.literal("setcooldown")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(FINAL_POINT_SUGGESTIONS)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, PointCommands.MAX_SECONDS))
                                        .executes(FinalPointCommand::setCooldown))));
    }

    private static void addCommandBlockBranches(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("addcommandblock")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(FINAL_POINT_SUGGESTIONS)
                                .then(Commands.argument("position", BlockPosArgument.blockPos())
                                        .executes(FinalPointCommand::addCommandBlock))))
                .then(Commands.literal("removecommandblock")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(FINAL_POINT_SUGGESTIONS)
                                .then(Commands.argument("position", BlockPosArgument.blockPos())
                                        .executes(FinalPointCommand::removeCommandBlock))))
                .then(Commands.literal("listcommandblocks")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(FINAL_POINT_SUGGESTIONS)
                                .executes(FinalPointCommand::listCommandBlocks)))
                .then(Commands.literal("clearcommandblocks")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(FINAL_POINT_SUGGESTIONS)
                                .executes(FinalPointCommand::clearCommandBlocks)));
    }

    private static void addToggleBranches(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("protection")
                        .then(Commands.literal("enable")
                                .executes(FinalPointCommand::enableProtection))
                        .then(Commands.literal("disable")
                                .executes(FinalPointCommand::disableProtection))
                        .then(Commands.literal("status")
                                .executes(PointCommands::protectionStatus)))
                .then(Commands.literal("serverviewmarkers")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(FinalPointCommand::setServerMarkers)))
                .then(Commands.literal("markers")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(PointCommands::setLocalMarkers)))
                .then(Commands.literal("openeronly")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(FinalPointCommand::setOpenerOnly)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createBranch() {
        return Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.string())
                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                                        .then(Commands.argument("captureTimeSeconds", IntegerArgumentType.integer(1, PointCommands.MAX_SECONDS))
                                                .then(Commands.argument("cooldownSeconds", IntegerArgumentType.integer(0, PointCommands.MAX_SECONDS))
                                                        .executes(FinalPointCommand::createPoint)
                                                        .then(Commands.argument(ShapeArguments.ARGUMENT_NAME, StringArgumentType.word())
                                                                .suggests(ShapeArguments.SUGGESTIONS)
                                                                .executes(FinalPointCommand::createPoint)))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> radiusBranch() {
        return Commands.literal("setradius")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(FINAL_POINT_SUGGESTIONS)
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                                .executes(FinalPointCommand::setRadius)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shapeBranch() {
        return Commands.literal("setshape")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(FINAL_POINT_SUGGESTIONS)
                        .then(Commands.argument(ShapeArguments.ARGUMENT_NAME, StringArgumentType.word())
                                .suggests(ShapeArguments.SUGGESTIONS)
                                .executes(FinalPointCommand::setShape)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> heightBranch() {
        return Commands.literal("setheight")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(FINAL_POINT_SUGGESTIONS)
                        .then(Commands.argument("up", IntegerArgumentType.integer(0))
                                .then(Commands.argument("down", IntegerArgumentType.integer(0))
                                        .executes(FinalPointCommand::setHeight))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> resetHeightBranch() {
        return Commands.literal("resetheight")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(FINAL_POINT_SUGGESTIONS)
                        .executes(FinalPointCommand::resetHeight));
    }

    private static int createPoint(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        if (nameTaken(context, name)) return 0;

        ZoneShape shape = ShapeArguments.readOrDefault(context, ZoneShape.CIRCLE);
        if (shape == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.unknown_shape").withStyle(ChatFormatting.RED));
            return 0;
        }

        BlockPos pos = BlockPosArgument.getBlockPos(context, "position");
        int radius = IntegerArgumentType.getInteger(context, "radius");
        int captureTimeSeconds = IntegerArgumentType.getInteger(context, "captureTimeSeconds");
        int cooldownSeconds = IntegerArgumentType.getInteger(context, "cooldownSeconds");

        ZoneArea area = new ZoneArea(shape, pos, radius, ZoneArea.DEFAULT_HEIGHT, ZoneArea.DEFAULT_HEIGHT);
        FinalCapturePoint point = new FinalCapturePoint(name, area, captureTimeSeconds * 20, cooldownSeconds * 20);
        point.setDimension(context.getSource().getLevel().dimension());
        CapturePointManager.addFinalPoint(point);

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_point_created",
                        name, captureTimeSeconds, cooldownSeconds
                ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static boolean nameTaken(CommandContext<CommandSourceStack> context, String name) {
        if (CapturePointManager.getFinalPoint(name) == null && CapturePointManager.getCapturePoint(name) == null) {
            return false;
        }

        context.getSource().sendFailure(
                Component.translatable("capturepoints.error.final_point_exists", name).withStyle(ChatFormatting.RED));
        return true;
    }

    private static int removePoint(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");

        if (CapturePointManager.getFinalPoint(name) == null) {
            context.getSource().sendFailure(
                    Component.translatable("capturepoints.error.final_point_not_found", name).withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        CapturePointManager.removeFinalPoint(name);
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_point_removed", name).withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private static int listPoints(CommandContext<CommandSourceStack> context) {
        var points = CapturePointManager.getAllFinalPoints();

        if (points.isEmpty()) {
            context.getSource().sendSuccess(() ->
                    Component.translatable("capturepoints.info.no_final_points").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.info.final_point_list_header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        for (FinalCapturePoint point : points) {
            BlockPos pos = point.getPosition();
            String owner = point.getOwnerTeam() != null ? point.getOwnerTeam() : "нет";
            int cmdBlocks = point.getCommandBlockPositions().size();

            String pointName = point.getName();
            context.getSource().sendSuccess(() ->
                    Component.empty()
                            .append(Component.literal(pointName).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                            .append(Component.literal(String.format(": [%d, %d, %d] %s %.1f, Owner: ", pos.getX(), pos.getY(), pos.getZ(), point.getShape().id(), point.getSize())).withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(owner).withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(", Command blocks: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(String.valueOf(cmdBlocks)).withStyle(ChatFormatting.AQUA)),
                    false
            );
        }

        return 1;
    }

    private static int clearOwner(CommandContext<CommandSourceStack> context) {
        String pointName = StringArgumentType.getString(context, "pointName");
        FinalCapturePoint point = CapturePointManager.getFinalPoint(pointName);

        if (point == null) {
            context.getSource().sendFailure(
                    Component.translatable("capturepoints.error.final_point_not_found", pointName).withStyle(ChatFormatting.RED));
            return 0;
        }

        point.clearOwner();
        CapturePointManager.cancelCaptureForPoint(pointName);
        CapturePointManager.persist();
        CapturePointManager.syncFinalPoints();
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.owner_cleared",
                        Component.literal(pointName).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setOwner(CommandContext<CommandSourceStack> context) {
        String pointName = StringArgumentType.getString(context, "pointName");
        String teamName = StringArgumentType.getString(context, "teamName");

        FinalCapturePoint point = CapturePointManager.getFinalPoint(pointName);
        if (point == null) {
            context.getSource().sendFailure(
                    Component.translatable("capturepoints.error.final_point_not_found", pointName).withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        MinecraftServer server = context.getSource().getServer();
        var scoreboard = server.getScoreboard();
        var team = scoreboard.getPlayerTeam(teamName);

        if (team == null) {
            context.getSource().sendFailure(
                    Component.translatable("capturepoints.error.team_not_found", teamName).withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        point.setOwnerTeam(teamName);
        CapturePointManager.cancelCaptureForPoint(pointName);
        CapturePointManager.persist();
        CapturePointManager.syncFinalPoints();

        announceOwner(context, server, pointName, teamName);
        return 1;
    }

    private static void announceOwner(CommandContext<CommandSourceStack> context, MinecraftServer server, String pointName, String teamName) {
        ChatFormatting teamColor = TeamUtil.getTeamColor(server, teamName);

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_owner_set",
                        Component.literal(pointName).withStyle(ChatFormatting.YELLOW),
                        Component.literal(teamName).withStyle(teamColor)
                ).withStyle(ChatFormatting.GREEN), true);

        if (server == null) return;

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("capturepoints.info.final_owner_broadcast",
                        Component.literal(pointName).withStyle(ChatFormatting.AQUA),
                        Component.literal(teamName).withStyle(teamColor)
                ).withStyle(ChatFormatting.YELLOW),
                false
        );
    }

    private static int addCommandBlock(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        BlockPos pos = BlockPosArgument.getBlockPos(context, "position");

        FinalCapturePoint point = CapturePointManager.getFinalPoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.final_point_not_found_short").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.addCommandBlock(pos);
        CapturePointManager.persist();

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.cmd_block_added",
                        pos.getX(), pos.getY(), pos.getZ()
                ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeCommandBlock(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        BlockPos pos = BlockPosArgument.getBlockPos(context, "position");

        FinalCapturePoint point = CapturePointManager.getFinalPoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.final_point_not_found_short").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.removeCommandBlock(pos);
        CapturePointManager.persist();

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.cmd_block_removed",
                        pos.getX(), pos.getY(), pos.getZ()
                ).withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private static int listCommandBlocks(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");

        FinalCapturePoint point = CapturePointManager.getFinalPoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.final_point_not_found_short").withStyle(ChatFormatting.RED));
            return 0;
        }

        var blocks = point.getCommandBlockPositions();
        if (blocks.isEmpty()) {
            context.getSource().sendSuccess(() ->
                    Component.translatable("capturepoints.info.no_cmd_blocks").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.info.cmd_block_list_header",
                        Component.literal(name).withStyle(ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        sendCommandBlockEntries(context, blocks);
        return 1;
    }

    private static void sendCommandBlockEntries(CommandContext<CommandSourceStack> context, List<BlockPos> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = blocks.get(i);
            int index = i + 1;
            context.getSource().sendSuccess(() ->
                    Component.translatable("capturepoints.info.cmd_block_entry",
                            index, pos.getX(), pos.getY(), pos.getZ()
                    ).withStyle(ChatFormatting.GRAY), false
            );
        }
    }

    private static int clearCommandBlocks(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");

        FinalCapturePoint point = CapturePointManager.getFinalPoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.final_point_not_found_short").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.clearCommandBlocks();
        CapturePointManager.persist();

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.cmd_blocks_cleared").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }



    private static int setRadius(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        int radius = IntegerArgumentType.getInteger(context, "radius");

        FinalCapturePoint point = CapturePointManager.getFinalPoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.final_point_not_found_short").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.setSize(radius);
        CapturePointManager.persist();
        CapturePointManager.syncFinalPoints();

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_radius_set", radius
                ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setShape(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        ZoneShape shape = ShapeArguments.read(context);

        if (shape == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.unknown_shape").withStyle(ChatFormatting.RED));
            return 0;
        }

        FinalCapturePoint point = CapturePointManager.getFinalPoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.final_point_not_found_short").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.setShape(shape);
        CapturePointManager.persist();
        CapturePointManager.syncFinalPoints();

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_shape_set", shape.id()).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setHeight(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        int up = IntegerArgumentType.getInteger(context, "up");
        int down = IntegerArgumentType.getInteger(context, "down");

        FinalCapturePoint point = CapturePointManager.getFinalPoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.final_point_not_found_short").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.setHeightUp(up);
        point.setHeightDown(down);
        CapturePointManager.persist();
        CapturePointManager.syncFinalPoints();

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_height_set", name, up, down).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int resetHeight(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");

        FinalCapturePoint point = CapturePointManager.getFinalPoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.final_point_not_found_short").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.resetHeight();
        CapturePointManager.persist();
        CapturePointManager.syncFinalPoints();

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_height_reset", name).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setCaptureTime(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        int seconds = IntegerArgumentType.getInteger(context, "seconds");

        FinalCapturePoint point = CapturePointManager.getFinalPoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.final_point_not_found_short").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.setCaptureTime(seconds * 20);
        CapturePointManager.persist();

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_capture_time_set", seconds
                ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setCooldown(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        int seconds = IntegerArgumentType.getInteger(context, "seconds");

        FinalCapturePoint point = CapturePointManager.getFinalPoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.final_point_not_found_short").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.setCooldown(seconds * 20);
        CapturePointManager.persist();

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_cooldown_set", seconds
                ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int resetAllFinalPoints(CommandContext<CommandSourceStack> context) {
        CapturePointManager.resetAllFinalPoints();

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_all_reset").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);

        if (context.getSource().getServer() != null) {
            context.getSource().getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("capturepoints.info.final_all_reset_broadcast").withStyle(ChatFormatting.YELLOW),
                    false
            );
        }

        return 1;
    }

    private static int enableProtection(CommandContext<CommandSourceStack> context) {
        BlockProtectionHandler.setProtectionEnabled(true);
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_protection_enabled").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.UNDERLINE), true);

        if (context.getSource().getServer() != null) {
            context.getSource().getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("capturepoints.info.protection_enabled_broadcast").withStyle(ChatFormatting.YELLOW),
                    false
            );
        }
        return 1;
    }

    private static int disableProtection(CommandContext<CommandSourceStack> context) {
        BlockProtectionHandler.setProtectionEnabled(false);
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_protection_disabled").withStyle(ChatFormatting.RED, ChatFormatting.BOLD, ChatFormatting.UNDERLINE), true);

        if (context.getSource().getServer() != null) {
            context.getSource().getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("capturepoints.info.protection_disabled_broadcast").withStyle(ChatFormatting.YELLOW),
                    false
            );
        }
        return 1;
    }

    private static int setOpenerOnly(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        CapturePointManager.setFinalForOpenerOnly(enabled);
        context.getSource().sendSuccess(() -> Component.translatable(enabled
                ? "capturepoints.success.final_opener_only"
                : "capturepoints.success.final_opener_any").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setServerMarkers(CommandContext<CommandSourceStack> context) {
        boolean enabled = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled");
        CapturePointManager.setGlobalFinalMarkers(enabled);
        context.getSource().sendSuccess(() -> Component.literal("Server view markers for final points set to " + enabled).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

}
