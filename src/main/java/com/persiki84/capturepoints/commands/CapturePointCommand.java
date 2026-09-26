package com.persiki84.capturepoints.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.battlecraft.BattleCraftCommands;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.event.BlockProtectionHandler;
import com.persiki84.capturepoints.menu.CapturePointMenuState;
import com.persiki84.capturepoints.network.CaptureCompletePacket;
import com.persiki84.capturepoints.network.PacketHandler;
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
import net.minecraftforge.network.PacketDistributor;


public class CapturePointCommand {

    public static final SuggestionProvider<CommandSourceStack> POINT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(CapturePointManager.getAllPoints().stream().map(CapturePoint::getName), builder);

    private static final SuggestionProvider<CommandSourceStack> SCOREBOARD_TEAM_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getServer().getScoreboard().getTeamNames(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("capturepoint")
                .requires(source -> source.hasPermission(2))
                .executes(source -> BattleCraftCommands.openMenu(source, CapturePointMenuState.MENU_ID));

        addLifecycleBranches(root);
        addGeometryBranches(root);
        addTimingBranches(root);
        addToggleBranches(root);
        PointBonusCommands.addBranches(root, POINT_SUGGESTIONS, CapturePointManager::getCapturePoint, context);
        CaptureTuningCommands.addBranches(root, POINT_SUGGESTIONS, CapturePointManager::getCapturePoint);

        dispatcher.register(root);
    }

    private static void addLifecycleBranches(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(createBranch())
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(POINT_SUGGESTIONS)
                                .executes(CapturePointCommand::removePoint)))
                .then(Commands.literal("list")
                        .executes(CapturePointCommand::listPoints))
                .then(Commands.literal("resetall")
                        .executes(CapturePointCommand::resetAllPoints))
                .then(Commands.literal("setowner")
                        .then(Commands.argument("pointName", StringArgumentType.string())
                                .suggests(POINT_SUGGESTIONS)
                                .then(Commands.argument("teamName", StringArgumentType.string())
                                        .suggests(SCOREBOARD_TEAM_SUGGESTIONS)
                                        .executes(CapturePointCommand::setOwner))))
                .then(Commands.literal("clearowner")
                        .then(Commands.argument("pointName", StringArgumentType.string())
                                .suggests(POINT_SUGGESTIONS)
                                .executes(CapturePointCommand::clearOwner)));
    }

    private static void addGeometryBranches(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(radiusBranch())
                .then(shapeBranch())
                .then(heightBranch())
                .then(resetHeightBranch());
    }

    private static void addTimingBranches(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(captureTimeBranch())
                .then(cooldownBranch());
    }

    private static void addToggleBranches(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(protectionBranch())
                .then(markerBranch())
                .then(localMarkerBranch());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createBranch() {
        return Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.string())
                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                                        .then(Commands.argument("captureTimeSeconds", IntegerArgumentType.integer(1, PointCommands.MAX_SECONDS))
                                                .then(Commands.argument("cooldownSeconds", IntegerArgumentType.integer(0, PointCommands.MAX_SECONDS))
                                                        .executes(CapturePointCommand::createPoint)
                                                        .then(Commands.argument(ShapeArguments.ARGUMENT_NAME, StringArgumentType.word())
                                                                .suggests(ShapeArguments.SUGGESTIONS)
                                                                .executes(CapturePointCommand::createPoint)))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> radiusBranch() {
        return Commands.literal("setradius")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(POINT_SUGGESTIONS)
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                                .executes(CapturePointCommand::setRadius)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shapeBranch() {
        return Commands.literal("setshape")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(POINT_SUGGESTIONS)
                        .then(Commands.argument(ShapeArguments.ARGUMENT_NAME, StringArgumentType.word())
                                .suggests(ShapeArguments.SUGGESTIONS)
                                .executes(CapturePointCommand::setShape)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> heightBranch() {
        return Commands.literal("setheight")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(POINT_SUGGESTIONS)
                        .then(Commands.argument("up", IntegerArgumentType.integer(0))
                                .then(Commands.argument("down", IntegerArgumentType.integer(0))
                                        .executes(CapturePointCommand::setHeight))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> resetHeightBranch() {
        return Commands.literal("resetheight")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(POINT_SUGGESTIONS)
                        .executes(CapturePointCommand::resetHeight));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> captureTimeBranch() {
        return Commands.literal("setcapturetime")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(POINT_SUGGESTIONS)
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, PointCommands.MAX_SECONDS))
                                .executes(CapturePointCommand::setCaptureTime)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cooldownBranch() {
        return Commands.literal("setcooldown")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(POINT_SUGGESTIONS)
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, PointCommands.MAX_SECONDS))
                                .executes(CapturePointCommand::setCooldown)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> protectionBranch() {
        return Commands.literal("protection")
                .then(Commands.literal("enable").executes(CapturePointCommand::enableProtection))
                .then(Commands.literal("disable").executes(CapturePointCommand::disableProtection))
                .then(Commands.literal("status").executes(PointCommands::protectionStatus))
                .then(Commands.literal("breakplaced")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(CapturePointCommand::setPlayerPlacedBreakable)))
                .then(Commands.literal("denyplace")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(CapturePointCommand::setPlacementDenied)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> markerBranch() {
        return Commands.literal("serverviewmarkers")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(CapturePointCommand::setServerMarkers));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> localMarkerBranch() {
        return Commands.literal("markers")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(PointCommands::setLocalMarkers));
    }

    private static int createPoint(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        if (CapturePointManager.getCapturePoint(name) != null || CapturePointManager.getFinalPoint(name) != null) {
            context.getSource().sendFailure(
                    Component.translatable("capturepoints.error.point_exists", name).withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        BlockPos pos = BlockPosArgument.getBlockPos(context, "position");
        int radius = IntegerArgumentType.getInteger(context, "radius");
        int captureTimeSeconds = IntegerArgumentType.getInteger(context, "captureTimeSeconds");
        int cooldownSeconds = IntegerArgumentType.getInteger(context, "cooldownSeconds");

        ZoneShape shape = ShapeArguments.readOrDefault(context, ZoneShape.CIRCLE);
        if (shape == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.unknown_shape").withStyle(ChatFormatting.RED));
            return 0;
        }

        ZoneArea area = new ZoneArea(shape, pos, radius, ZoneArea.DEFAULT_HEIGHT, ZoneArea.DEFAULT_HEIGHT);
        CapturePoint point = new CapturePoint(name, area, captureTimeSeconds * 20, cooldownSeconds * 20);
        point.setDimension(context.getSource().getLevel().dimension());
        CapturePointManager.addCapturePoint(point);

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.point_created", name).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removePoint(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (CapturePointManager.getCapturePoint(name) == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.point_not_found").withStyle(ChatFormatting.RED));
            return 0;
        }
        CapturePointManager.removeCapturePoint(name);
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.point_removed", name).withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private static int listPoints(CommandContext<CommandSourceStack> context) {
        var points = CapturePointManager.getAllPoints();
        if (points.isEmpty()) {
            context.getSource().sendSuccess(() ->
                    Component.translatable("capturepoints.info.no_points").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.info.point_list_header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        MinecraftServer server = context.getSource().getServer();

        for (CapturePoint point : points) {
            Component line = describePoint(point, server);
            context.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static Component describePoint(CapturePoint point, MinecraftServer server) {
        BlockPos pos = point.getPosition();
        String owner = point.getOwnerTeam() != null ? point.getOwnerTeam() : "нет";
        ChatFormatting ownerColor = point.getOwnerTeam() != null ? TeamUtil.getTeamColor(server, point.getOwnerTeam()) : ChatFormatting.GRAY;

        return Component.empty()
                .append(Component.literal(point.getName()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(String.format(" [%d, %d, %d] %s %.1f Owner: ",
                        pos.getX(), pos.getY(), pos.getZ(), point.getShape().id(), point.getSize())).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(owner).withStyle(ownerColor))
                .append(Component.literal(describeIncome(point)).withStyle(ChatFormatting.GREEN));
    }

    private static String describeIncome(CapturePoint point) {
        if (point.getPassiveIncomeAmount() <= 0 || point.getIncomeItem().isEmpty()) return "";

        String itemName = point.getIncomeItem().getHoverName().getString();
        int minutes = point.getIncomeIntervalSeconds() / 60;
        return String.format(" (Income: %dx %s / %d min)", point.getPassiveIncomeAmount(), itemName, minutes);
    }

    private static int clearOwner(CommandContext<CommandSourceStack> context) {
        String pointName = StringArgumentType.getString(context, "pointName");
        CapturePoint point = CapturePointManager.getCapturePoint(pointName);

        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.point_not_found").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.clearOwner();
        CapturePointManager.cancelCaptureForPoint(pointName);
        CapturePointManager.persist();
        CapturePointManager.syncPoints();
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.owner_cleared",
                        Component.literal(pointName).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setOwner(CommandContext<CommandSourceStack> context) {
        String pointName = StringArgumentType.getString(context, "pointName");
        String teamName = StringArgumentType.getString(context, "teamName");
        CapturePoint point = CapturePointManager.getCapturePoint(pointName);

        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.point_not_found").withStyle(ChatFormatting.RED));
            return 0;
        }

        var server = context.getSource().getServer();
        var team = server.getScoreboard().getPlayerTeam(teamName);
        if (team == null) {
            context.getSource().sendFailure(
                    Component.translatable("capturepoints.error.team_not_found", teamName).withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        point.setOwnerTeam(teamName);
        CapturePointManager.cancelCaptureForPoint(pointName);
        CapturePointManager.persist();
        CapturePointManager.syncPoints();
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new CaptureCompletePacket(pointName, teamName));
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.owner_set").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }



    private static int setShape(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        ZoneShape shape = ShapeArguments.read(context);
        CapturePoint point = CapturePointManager.getCapturePoint(name);

        if (shape == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.unknown_shape").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.point_not_found").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.setShape(shape);
        CapturePointManager.persist();
        CapturePointManager.syncPoints();
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.shape_updated", shape.id()).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setRadius(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        int radius = IntegerArgumentType.getInteger(context, "radius");
        CapturePoint point = CapturePointManager.getCapturePoint(name);
        if (point != null) {
            point.setSize(radius);
            CapturePointManager.persist();
            CapturePointManager.syncPoints();
            context.getSource().sendSuccess(() ->
                    Component.translatable("capturepoints.success.radius_updated").withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        return 0;
    }

    private static int setCaptureTime(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        CapturePoint point = CapturePointManager.getCapturePoint(name);
        if (point != null) {
            point.setCaptureTime(seconds * 20);
            CapturePointManager.persist();
            context.getSource().sendSuccess(() ->
                    Component.translatable("capturepoints.success.capture_time_updated").withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        return 0;
    }

    private static int setCooldown(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        CapturePoint point = CapturePointManager.getCapturePoint(name);
        if (point != null) {
            point.setCooldown(seconds * 20);
            CapturePointManager.persist();
            context.getSource().sendSuccess(() ->
                    Component.translatable("capturepoints.success.cooldown_updated").withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        return 0;
    }

    private static int resetAllPoints(CommandContext<CommandSourceStack> context) {
        CapturePointManager.resetAllPoints();
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.all_reset").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int enableProtection(CommandContext<CommandSourceStack> context) {
        BlockProtectionHandler.setProtectionEnabled(true);
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.protection_enabled").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int disableProtection(CommandContext<CommandSourceStack> context) {
        BlockProtectionHandler.setProtectionEnabled(false);
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.protection_disabled").withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private static int setPlayerPlacedBreakable(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        BlockProtectionHandler.setPlayerPlacedBreakable(enabled);
        context.getSource().sendSuccess(() -> Component.translatable(enabled
                        ? "capturepoints.success.break_placed_enabled"
                        : "capturepoints.success.break_placed_disabled")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        return 1;
    }

    private static int setPlacementDenied(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        BlockProtectionHandler.setPlacementDenied(enabled);
        context.getSource().sendSuccess(() -> Component.translatable(enabled
                        ? "capturepoints.success.deny_place_enabled"
                        : "capturepoints.success.deny_place_disabled")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        return 1;
    }

    private static int setServerMarkers(CommandContext<CommandSourceStack> context) {
        boolean enabled = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled");
        CapturePointManager.setGlobalCaptureMarkers(enabled);
        context.getSource().sendSuccess(() -> Component.literal("Server view markers for capture points set to " + enabled).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }



    private static int setHeight(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        int up = IntegerArgumentType.getInteger(context, "up");
        int down = IntegerArgumentType.getInteger(context, "down");

        CapturePoint point = CapturePointManager.getCapturePoint(name);
        if (point == null) {
            context.getSource().sendFailure(
                    Component.translatable("capturepoints.error.point_name_not_found", name).withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        point.setHeightUp(up);
        point.setHeightDown(down);
        CapturePointManager.persist();
        CapturePointManager.syncPoints();

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.height_set",
                        Component.literal(name).withStyle(ChatFormatting.YELLOW),
                        Component.literal("↑" + up).withStyle(ChatFormatting.GOLD),
                        Component.literal("↓" + down).withStyle(ChatFormatting.GOLD)
                ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int resetHeight(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");

        CapturePoint point = CapturePointManager.getCapturePoint(name);
        if (point == null) {
            context.getSource().sendFailure(
                    Component.translatable("capturepoints.error.point_name_not_found", name).withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        point.resetHeight();
        CapturePointManager.persist();
        CapturePointManager.syncPoints();

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.height_reset",
                        Component.literal(name).withStyle(ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

}
