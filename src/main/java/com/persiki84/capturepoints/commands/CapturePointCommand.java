package com.persiki84.capturepoints.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.event.BlockProtectionHandler;
import com.persiki84.capturepoints.network.CaptureCompletePacket;
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
import net.minecraftforge.registries.ForgeRegistries;

public class CapturePointCommand {

    private static final SuggestionProvider<CommandSourceStack> EFFECT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(ForgeRegistries.MOB_EFFECTS.getKeys(), builder);

    public static final SuggestionProvider<CommandSourceStack> POINT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(CapturePointManager.getAllPoints().stream().map(CapturePoint::getName), builder);

    private static final SuggestionProvider<CommandSourceStack> SCOREBOARD_TEAM_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getServer().getScoreboard().getTeamNames(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("capturepoint")
                .requires(source -> source.hasPermission(2))

                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("position", BlockPosArgument.blockPos())
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                                                .then(Commands.argument("captureTimeSeconds", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("cooldownSeconds", IntegerArgumentType.integer(0))
                                                                .executes(CapturePointCommand::createPoint)))))))
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

                .then(Commands.literal("setradius")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(POINT_SUGGESTIONS)
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                                        .executes(CapturePointCommand::setRadius))))

                .then(Commands.literal("setcapturetime")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(POINT_SUGGESTIONS)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(CapturePointCommand::setCaptureTime))))

                .then(Commands.literal("setcooldown")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(POINT_SUGGESTIONS)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                        .executes(CapturePointCommand::setCooldown))))
                .then(Commands.literal("setheight")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(POINT_SUGGESTIONS)
                                .then(Commands.argument("up", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("down", IntegerArgumentType.integer(0))
                                                .executes(CapturePointCommand::setHeight)))))

                .then(Commands.literal("resetheight")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(POINT_SUGGESTIONS)
                                .executes(CapturePointCommand::resetHeight)))


                .then(Commands.literal("setreward")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(POINT_SUGGESTIONS)
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(CapturePointCommand::setReward)))))

                .then(Commands.literal("removereward")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(POINT_SUGGESTIONS)
                                .executes(CapturePointCommand::removeReward)))

                .then(Commands.literal("setincome")
                        .then(Commands.argument("point", StringArgumentType.string())
                                .suggests(POINT_SUGGESTIONS)
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(CapturePointCommand::setIncome)))))

                .then(Commands.literal("setincomeinterval")
                        .then(Commands.argument("point", StringArgumentType.string())
                                .suggests(POINT_SUGGESTIONS)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(CapturePointCommand::setIncomeInterval))))

                .then(Commands.literal("setbuff")
                        .then(Commands.argument("point", StringArgumentType.string())
                                .suggests(POINT_SUGGESTIONS)
                                .then(Commands.argument("effect", StringArgumentType.string())
                                        .suggests(EFFECT_SUGGESTIONS)
                                        .then(Commands.argument("amplifier", IntegerArgumentType.integer(0, 5))
                                                .executes(CapturePointCommand::setBuff)))))

                .then(Commands.literal("protection")
                        .then(Commands.literal("enable").executes(CapturePointCommand::enableProtection))
                        .then(Commands.literal("disable").executes(CapturePointCommand::disableProtection))
                        .then(Commands.literal("status").executes(CapturePointCommand::protectionStatus)))
                        
                .then(Commands.literal("serverviewmarkers")
                        .then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(CapturePointCommand::setServerMarkers)))
                
                .then(Commands.literal("markers")
                        .then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(CapturePointCommand::setLocalMarkers))));
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

        CapturePoint point = new CapturePoint(name, pos, radius, captureTimeSeconds * 20, cooldownSeconds * 20);
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
            BlockPos pos = point.getPosition();
            String owner = point.getOwnerTeam() != null ? point.getOwnerTeam() : "нет";
            ChatFormatting ownerColor = point.getOwnerTeam() != null ? TeamUtil.getTeamColor(server, point.getOwnerTeam()) : ChatFormatting.GRAY;

            String incomeInfo = "";
            if (point.getPassiveIncomeAmount() > 0 && !point.getIncomeItem().isEmpty()) {
                String itemName = point.getIncomeItem().getHoverName().getString();
                int minutes = point.getIncomeIntervalSeconds() / 60;
                incomeInfo = String.format(" (Income: %dx %s / %d min)", point.getPassiveIncomeAmount(), itemName, minutes);
            }

            String finalMsg = String.format("%s [%d, %d, %d] Owner: %s%s",
                    point.getName(), pos.getX(), pos.getY(), pos.getZ(), owner, incomeInfo);

            String pointName = point.getName();
            ChatFormatting finalOwnerColor = ownerColor;
            String finalOwner = owner;
            String finalIncomeInfo = incomeInfo;
            context.getSource().sendSuccess(() ->
                    Component.empty()
                            .append(Component.literal(pointName).withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(String.format(" [%d, %d, %d] Owner: ", pos.getX(), pos.getY(), pos.getZ())).withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(finalOwner).withStyle(finalOwnerColor))
                            .append(Component.literal(finalIncomeInfo).withStyle(ChatFormatting.GREEN)),
                    false
            );
        }
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
        CapturePointManager.save(null);
        CapturePointManager.syncPoints();
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new CaptureCompletePacket(pointName, teamName));
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.owner_set").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setReward(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        ItemStack item = ItemArgument.getItem(context, "item").createItemStack(1, false);
        int amount = IntegerArgumentType.getInteger(context, "amount");

        CapturePoint point = CapturePointManager.getCapturePoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.point_not_found").withStyle(ChatFormatting.RED));
            return 0;
        }
        point.setReward(item);
        point.setRewardAmount(amount);
        CapturePointManager.save(null);
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.reward_updated").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeReward(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CapturePoint point = CapturePointManager.getCapturePoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.point_not_found").withStyle(ChatFormatting.RED));
            return 0;
        }
        point.setReward(new ItemStack(Items.AIR));
        point.setRewardAmount(0);
        CapturePointManager.save(null);
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.reward_removed").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setRadius(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        int radius = IntegerArgumentType.getInteger(context, "radius");
        CapturePoint point = CapturePointManager.getCapturePoint(name);
        if (point != null) {
            point.setRadius(radius);
            CapturePointManager.save(null);
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
            CapturePointManager.save(null);
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
            CapturePointManager.save(null);
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
        CapturePointManager.setGlobalCaptureMarkers(enabled);
        context.getSource().sendSuccess(() -> Component.literal("Server view markers for capture points set to " + enabled).withStyle(ChatFormatting.GREEN), true);
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

    private static int setBuff(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "point");
        String effect = StringArgumentType.getString(context, "effect");
        int amplifier = IntegerArgumentType.getInteger(context, "amplifier");

        CapturePoint point = CapturePointManager.getCapturePoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.point_not_found").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.setBuffEffect(effect);
        point.setBuffAmplifier(amplifier);
        CapturePointManager.save(null);
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.buff_set",
                        Component.literal(name).withStyle(ChatFormatting.YELLOW),
                        Component.literal(effect).withStyle(ChatFormatting.LIGHT_PURPLE),
                        amplifier + 1
                ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setIncome(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "point");
        ItemStack item = ItemArgument.getItem(context, "item").createItemStack(1, false);
        int amount = IntegerArgumentType.getInteger(context, "amount");

        CapturePoint point = CapturePointManager.getCapturePoint(name);
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.point_not_found").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.setIncomeItem(item);
        point.setPassiveIncomeAmount(amount);
        CapturePointManager.save(null);

        String itemName = item.getHoverName().getString();
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.income_set",
                        Component.literal(name).withStyle(ChatFormatting.YELLOW),
                        Component.literal(amount + "x " + itemName).withStyle(ChatFormatting.GOLD)
                ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setIncomeInterval(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "point");
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        CapturePoint point = CapturePointManager.getCapturePoint(name);

        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.point_not_found").withStyle(ChatFormatting.RED));
            return 0;
        }

        point.setIncomeIntervalSeconds(seconds);
        CapturePointManager.save(null);
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.income_interval_set",
                        Component.literal(name).withStyle(ChatFormatting.YELLOW),
                        Component.literal(seconds + " sec.").withStyle(ChatFormatting.GOLD)
                ).withStyle(ChatFormatting.GREEN), true);
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
        CapturePointManager.save(context.getSource().getLevel());

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
        CapturePointManager.save(context.getSource().getLevel());

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.height_reset",
                        Component.literal(name).withStyle(ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

}
