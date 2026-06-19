package com.persiki84.capturepoints.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.capturepoints.capture.FinalCapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.event.BlockProtectionHandler;
import com.persiki84.capturepoints.network.FinalPointSyncPacket;
import com.persiki84.capturepoints.network.PacketHandler;
import com.persiki84.capturepoints.util.TeamUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

public class FinalPointCommand {
    private static final SuggestionProvider<CommandSourceStack> FINAL_POINT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(CapturePointManager.getAllFinalPoints().stream().map(FinalCapturePoint::getName), builder);

    private static final SuggestionProvider<CommandSourceStack> SCOREBOARD_TEAM_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getServer().getScoreboard().getTeamNames(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("finalpoint")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("position", BlockPosArgument.blockPos())
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                                                .then(Commands.argument("captureTimeSeconds", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("cooldownSeconds", IntegerArgumentType.integer(0))
                                                                .executes(FinalPointCommand::createPoint)))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(FINAL_POINT_SUGGESTIONS)
                                .executes(FinalPointCommand::removePoint)))
                .then(Commands.literal("list")
                        .executes(FinalPointCommand::listPoints))
                .then(Commands.literal("setowner")
                        .then(Commands.argument("pointName", StringArgumentType.string())
                                .suggests(FINAL_POINT_SUGGESTIONS)
                                .then(Commands.argument("teamName", StringArgumentType.string())
                                        .suggests(SCOREBOARD_TEAM_SUGGESTIONS)
                                        .executes(FinalPointCommand::setOwner))))
                .then(Commands.literal("addcommandblock")
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
                                .executes(FinalPointCommand::clearCommandBlocks)))
                .then(Commands.literal("setreward")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(FINAL_POINT_SUGGESTIONS)
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(FinalPointCommand::setReward)))))
                .then(Commands.literal("removereward")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(FINAL_POINT_SUGGESTIONS)
                                .executes(FinalPointCommand::removeReward)))
                .then(Commands.literal("setradius")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(FINAL_POINT_SUGGESTIONS)
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                                        .executes(FinalPointCommand::setRadius))))
                .then(Commands.literal("setcapturetime")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(FINAL_POINT_SUGGESTIONS)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(FinalPointCommand::setCaptureTime))))
                .then(Commands.literal("setcooldown")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(FINAL_POINT_SUGGESTIONS)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                        .executes(FinalPointCommand::setCooldown))))
                .then(Commands.literal("resetall")
                        .executes(FinalPointCommand::resetAllFinalPoints))
                .then(Commands.literal("protection")
                        .then(Commands.literal("enable")
                                .executes(FinalPointCommand::enableProtection))
                        .then(Commands.literal("disable")
                                .executes(FinalPointCommand::disableProtection))
                        .then(Commands.literal("status")
                                .executes(FinalPointCommand::protectionStatus)))
                
                .then(Commands.literal("serverviewmarkers")
                        .then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(FinalPointCommand::setServerMarkers)))
                
                .then(Commands.literal("markers")
                        .then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(FinalPointCommand::setLocalMarkers))));
    }

    private static int createPoint(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");

        if (CapturePointManager.getFinalPoint(name) != null || CapturePointManager.getCapturePoint(name) != null) {
            context.getSource().sendFailure(
                    Component.translatable("capturepoints.error.final_point_exists", name).withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        BlockPos pos = BlockPosArgument.getBlockPos(context, "position");
        int radius = IntegerArgumentType.getInteger(context, "radius");
        int captureTimeSeconds = IntegerArgumentType.getInteger(context, "captureTimeSeconds");
        int cooldownSeconds = IntegerArgumentType.getInteger(context, "cooldownSeconds");

        int captureTimeTicks = captureTimeSeconds * 20;
        int cooldownTicks = cooldownSeconds * 20;

        FinalCapturePoint point = new FinalCapturePoint(name, pos, radius, captureTimeTicks, cooldownTicks);
        CapturePointManager.addFinalPoint(point);

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_point_created",
                        name, captureTimeSeconds, cooldownSeconds
                ).withStyle(ChatFormatting.GREEN), true);
        return 1;
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
                            .append(Component.literal(String.format(": [%d, %d, %d] Radius: %d, Owner: ", pos.getX(), pos.getY(), pos.getZ(), point.getRadius())).withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(owner).withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(", Command blocks: ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(String.valueOf(cmdBlocks)).withStyle(ChatFormatting.AQUA)),
                    false
            );
        }

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
        CapturePointManager.save(null);
        CapturePointManager.syncFinalPoints();

        ChatFormatting teamColor = TeamUtil.getTeamColor(server, teamName);
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_owner_set",
                        Component.literal(pointName).withStyle(ChatFormatting.YELLOW),
                        Component.literal(teamName).withStyle(teamColor)
                ).withStyle(ChatFormatting.GREEN), true);

        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("capturepoints.info.final_owner_broadcast",
                            Component.literal(pointName).withStyle(ChatFormatting.AQUA),
                            Component.literal(teamName).withStyle(teamColor)
                    ).withStyle(ChatFormatting.YELLOW),
                    false
            );
        }

        return 1;
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
        CapturePointManager.save(null);

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
        CapturePointManager.save(null);

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

        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = blocks.get(i);
            int index = i + 1;
            context.getSource().sendSuccess(() ->
                    Component.translatable("capturepoints.info.cmd_block_entry",
                            index, pos.getX(), pos.getY(), pos.getZ()
                    ).withStyle(ChatFormatting.GRAY), false
            );
        }

        return 1;
    }

    private static int clearCommandBlocks(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");

        FinalCapturePoint point = CapturePointManager.getFinalPoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.final_point_not_found_short").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.clearCommandBlocks();
        CapturePointManager.save(null);

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.cmd_blocks_cleared").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setReward(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        ItemStack item = ItemArgument.getItem(context, "item").createItemStack(1, false);
        int amount = IntegerArgumentType.getInteger(context, "amount");

        FinalCapturePoint point = CapturePointManager.getFinalPoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.final_point_not_found_short").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.setReward(item);
        point.setRewardAmount(amount);
        CapturePointManager.save(null);

        String itemName = item.getHoverName().getString();
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_reward_set",
                        amount, itemName
                ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeReward(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");

        FinalCapturePoint point = CapturePointManager.getFinalPoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.final_point_not_found_short").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.setReward(new ItemStack(Items.AIR));
        point.setRewardAmount(0);
        CapturePointManager.save(null);

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_reward_removed",
                        Component.literal(name).withStyle(ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GREEN), true);
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

        point.setRadius(radius);
        CapturePointManager.save(null);

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.final_radius_set", radius
                ).withStyle(ChatFormatting.GREEN), true);
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
        CapturePointManager.save(null);

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
        CapturePointManager.save(null);

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

    private static int protectionStatus(CommandContext<CommandSourceStack> context) {
        boolean enabled = BlockProtectionHandler.isProtectionEnabled();
        Component statusText = enabled
                ? Component.translatable("capturepoints.status.enabled").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                : Component.translatable("capturepoints.status.disabled").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.info.protection_status", statusText).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int setServerMarkers(CommandContext<CommandSourceStack> context) {
        boolean enabled = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled");
        CapturePointManager.setGlobalFinalMarkers(enabled);
        context.getSource().sendSuccess(() -> Component.literal("Server view markers for final points set to " + enabled).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setLocalMarkers(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean enabled = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled");
        net.minecraft.server.level.ServerPlayer player = context.getSource().getPlayerOrException();
        com.persiki84.capturepoints.network.PacketHandler.INSTANCE.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new com.persiki84.capturepoints.network.LocalMarkerOverridePacket(enabled)
        );
        context.getSource().sendSuccess(() -> Component.literal("Local markers visibility set to " + enabled).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

}
