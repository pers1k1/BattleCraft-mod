package com.persiki84.battlecraft.announce;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.battlecraft.BattleCraftCommands;
import com.persiki84.battlecraft.menu.AnnounceMenuState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Collection;

public final class AnnounceCommand {
    private static final String STYLE = "style";
    private static final String SECONDS = "seconds";
    private static final String MESSAGE = "message";
    private static final String TEAM = "team";
    private static final String TARGETS = "targets";

    private static final SuggestionProvider<CommandSourceStack> STYLES = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(AnnounceStyle.values()).map(AnnounceStyle::id),
                    builder);
    private static final SuggestionProvider<CommandSourceStack> TEAMS = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getServer().getScoreboard().getTeamNames(),
                    builder);

    private AnnounceCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("announce")
                .requires(source -> source.hasPermission(2))
                .executes(context -> BattleCraftCommands.openMenu(context, AnnounceMenuState.MENU_ID))
                .then(Commands.literal("all")
                        .then(style(Commands.argument(MESSAGE, StringArgumentType.greedyString())
                                .executes(AnnounceCommand::toEveryone))))
                .then(Commands.literal("team")
                        .then(Commands.argument(TEAM, StringArgumentType.word())
                                .suggests(TEAMS)
                                .then(style(Commands.argument(MESSAGE, StringArgumentType.greedyString())
                                        .executes(AnnounceCommand::toTeam)))))
                .then(Commands.literal("player")
                        .then(Commands.argument(TARGETS, EntityArgument.players())
                                .then(style(Commands.argument(MESSAGE, StringArgumentType.greedyString())
                                        .executes(AnnounceCommand::toPlayers)))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String>
            style(ArgumentBuilder<CommandSourceStack, ?> tail) {
        return Commands.argument(STYLE, StringArgumentType.word()).suggests(STYLES)
                .then(Commands.argument(SECONDS, IntegerArgumentType.integer(
                        Announcements.MIN_SECONDS, Announcements.MAX_SECONDS)).then(tail));
    }

    private static int held(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, SECONDS);
    }

    private static int toEveryone(CommandContext<CommandSourceStack> context) {
        return report(context, Announcements.everyone(context.getSource().getServer(),
                message(context), chosen(context), held(context)));
    }

    private static int toTeam(CommandContext<CommandSourceStack> context) {
        Collection<ServerPlayer> found = Announcements.ofTeam(context.getSource().getServer(),
                StringArgumentType.getString(context, TEAM));
        return report(context, Announcements.deliver(found, message(context), chosen(context),
                held(context)));
    }

    private static int toPlayers(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Collection<ServerPlayer> found = EntityArgument.getPlayers(context, TARGETS);
        return report(context, Announcements.deliver(found, message(context), chosen(context),
                held(context)));
    }

    private static String message(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, MESSAGE);
    }

    private static AnnounceStyle chosen(CommandContext<CommandSourceStack> context) {
        return AnnounceStyle.byId(StringArgumentType.getString(context, STYLE));
    }

    private static int report(CommandContext<CommandSourceStack> context, int reached) {
        context.getSource().sendSuccess(() -> Announcements.receipt(reached), true);
        return reached;
    }
}
