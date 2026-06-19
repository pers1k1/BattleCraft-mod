package com.persiki84.immortality.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.persiki84.immortality.event.ImmortalityHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public class ImmortalityCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("immortality")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("enable")
                        .executes(ImmortalityCommand::enable))
                .then(Commands.literal("disable")
                        .executes(ImmortalityCommand::disable))
                .then(Commands.literal("status")
                        .executes(ImmortalityCommand::status))
                .then(Commands.literal("setduration")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                .executes(ImmortalityCommand::setDuration)))
                .then(Commands.literal("give")
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ImmortalityCommand::giveImmortality)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ImmortalityCommand::removeImmortality)))
                .then(Commands.literal("check")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ImmortalityCommand::checkPlayer)))
                .then(Commands.literal("clearall")
                        .executes(ImmortalityCommand::clearAll))
        );
    }

    private static int enable(CommandContext<CommandSourceStack> context) {
        ImmortalityHandler.setEnabled(true);
        context.getSource().sendSuccess(() ->
                Component.translatable("immortality.command.enable")
                        .withStyle(ChatFormatting.GREEN), true);

        if (context.getSource().getServer() != null) {
            context.getSource().getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("immortality.broadcast.enabled")
                            .withStyle(ChatFormatting.GOLD), false);
        }
        return 1;
    }

    private static int disable(CommandContext<CommandSourceStack> context) {
        ImmortalityHandler.setEnabled(false);
        context.getSource().sendSuccess(() ->
                Component.translatable("immortality.command.disable")
                        .withStyle(ChatFormatting.RED), true);

        if (context.getSource().getServer() != null) {
            context.getSource().getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("immortality.broadcast.disabled")
                            .withStyle(ChatFormatting.GOLD), false);
        }
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        boolean enabled = ImmortalityHandler.isEnabled();
        int duration = ImmortalityHandler.getDuration();

        Component statusComponent = enabled
                ? Component.translatable("immortality.status.enabled").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                : Component.translatable("immortality.status.disabled").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        context.getSource().sendSuccess(() ->
                Component.translatable("immortality.command.status", statusComponent)
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.translatable("immortality.command.status.duration", duration)
                                .withStyle(ChatFormatting.YELLOW)), false);
        return 1;
    }

    private static int setDuration(CommandContext<CommandSourceStack> context) {
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        ImmortalityHandler.setDuration(seconds);

        context.getSource().sendSuccess(() ->
                Component.translatable("immortality.command.setduration", seconds)
                        .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int giveImmortality(CommandContext<CommandSourceStack> context) {
        if (!ImmortalityHandler.isEnabled()) {
            context.getSource().sendFailure(
                    Component.translatable("immortality.command.give.disabled")
                            .withStyle(ChatFormatting.RED));
            return 0;
        }

        try {
            Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
            int duration = ImmortalityHandler.getDuration();

            for (ServerPlayer player : players) {
                ImmortalityHandler.giveImmortality(player);
                player.sendSystemMessage(
                        Component.translatable("immortality.give.target", duration)
                                .withStyle(ChatFormatting.GOLD));
            }

            int playerCount = players.size();
            context.getSource().sendSuccess(() ->
                    Component.translatable(getPluralKey(playerCount, "immortality.command.give.success"), playerCount, duration)
                            .withStyle(ChatFormatting.GREEN), true);

            if (context.getSource().getServer() != null) {
                for (ServerPlayer target : players) {
                    Component msg = Component.translatable("immortality.broadcast.given", target.getName().getString())
                            .withStyle(ChatFormatting.GOLD);
                    for (ServerPlayer online : context.getSource().getServer().getPlayerList().getPlayers()) {
                        if (!players.contains(online)) {
                            online.sendSystemMessage(msg);
                        }
                    }
                }
            }

            return 1;
        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(
                    Component.translatable("immortality.command.error", e.getMessage())
                            .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int removeImmortality(CommandContext<CommandSourceStack> context) {
        try {
            Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");

            for (ServerPlayer player : players) {
                ImmortalityHandler.removeImmortality(player);
                player.sendSystemMessage(
                        Component.translatable("immortality.remove.target")
                                .withStyle(ChatFormatting.RED));
            }

            int playerCount = players.size();
            context.getSource().sendSuccess(() ->
                    Component.translatable(getPluralKey(playerCount, "immortality.command.remove.success"), playerCount)
                            .withStyle(ChatFormatting.RED), true);
            return 1;
        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(
                    Component.translatable("immortality.command.error", e.getMessage())
                            .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int checkPlayer(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");

            if (ImmortalityHandler.isImmortal(player)) {
                int remaining = ImmortalityHandler.getRemainingTime(player);
                String playerName = player.getName().getString();
                context.getSource().sendSuccess(() ->
                        Component.translatable("immortality.command.check.immortal", playerName, remaining)
                                .withStyle(ChatFormatting.YELLOW), false);
            } else {
                String playerName = player.getName().getString();
                context.getSource().sendSuccess(() ->
                        Component.translatable("immortality.command.check.mortal", playerName)
                                .withStyle(ChatFormatting.RED), false);
            }
            return 1;
        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(
                    Component.translatable("immortality.command.error", e.getMessage())
                            .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static String getPluralKey(int count, String baseKey) {
        int mod10 = count % 10;
        int mod100 = count % 100;
        if (mod10 == 1 && mod100 != 11) {
            return baseKey + ".one";
        } else if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return baseKey + ".few";
        } else {
            return baseKey + ".many";
        }
    }

    private static int clearAll(CommandContext<CommandSourceStack> context) {
        ImmortalityHandler.clearAll();

        context.getSource().sendSuccess(() ->
                Component.translatable("immortality.command.clearall")
                        .withStyle(ChatFormatting.GREEN), true);

        if (context.getSource().getServer() != null) {
            context.getSource().getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("immortality.broadcast.clearall")
                            .withStyle(ChatFormatting.GOLD), false);
        }
        return 1;
    }
}
