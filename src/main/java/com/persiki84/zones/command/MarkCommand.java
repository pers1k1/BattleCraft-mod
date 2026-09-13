package com.persiki84.zones.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.battlecraft.BattleCraftCommands;
import com.persiki84.zones.ZonesMod;
import com.persiki84.zones.mark.MapMark;
import com.persiki84.zones.mark.MarkRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public final class MarkCommand {
    public static final String MENU_ID = "marks";

    private static final SuggestionProvider<CommandSourceStack> MARK_IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(MarkRegistry.ids(), builder);

    private static final SuggestionProvider<CommandSourceStack> TEAMS = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getServer().getScoreboard().getTeamNames(), builder);

    private MarkCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("mark")
                .requires(source -> source.hasPermission(2))
                .executes(context -> BattleCraftCommands.openMenu(context, MENU_ID))
                .then(createBranch())
                .then(deleteBranch())
                .then(listBranch())
                .then(teleportBranch())
                .then(editBranch());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createBranch() {
        return Commands.literal("create")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> create(context, null, null))
                        .then(placedBranch())
                        .then(Commands.argument("label", StringArgumentType.greedyString())
                                .executes(context -> create(context, null, label(context)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> placedBranch() {
        return Commands.literal("at")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> create(context, position(context), null))
                        .then(Commands.argument("label", StringArgumentType.greedyString())
                                .executes(context -> create(context, position(context), label(context)))));
    }

    private static BlockPos position(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return BlockPosArgument.getSpawnablePos(context, "pos");
    }

    private static String label(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "label");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> deleteBranch() {
        return Commands.literal("delete")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(MARK_IDS)
                        .executes(MarkCommand::delete));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> listBranch() {
        return Commands.literal("list").executes(MarkCommand::list);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> teleportBranch() {
        return Commands.literal("tp")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(MARK_IDS)
                        .executes(MarkCommand::teleport));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> editBranch() {
        return Commands.literal("edit")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(MARK_IDS)
                        .then(labelEdit())
                        .then(colorEdit())
                        .then(Commands.literal("here").executes(MarkCommand::moveHere))
                        .then(worldEdit())
                        .then(Commands.literal("everyone").executes(MarkCommand::showEveryone))
                        .then(teamEdit("show", true))
                        .then(teamEdit("hide", false)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> labelEdit() {
        return Commands.literal("label")
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(MarkCommand::editLabel));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> colorEdit() {
        return Commands.literal("color")
                .then(Commands.argument("value", IntegerArgumentType.integer())
                        .executes(MarkCommand::editColor));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> worldEdit() {
        return Commands.literal("world")
                .then(Commands.argument("shown", BoolArgumentType.bool())
                        .executes(MarkCommand::editWorld));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> teamEdit(String name, boolean allowed) {
        return Commands.literal(name)
                .then(Commands.argument("team", StringArgumentType.word())
                        .suggests(TEAMS)
                        .executes(context -> editTeam(context, allowed)));
    }

    private static int create(CommandContext<CommandSourceStack> context, BlockPos placed, String label)
            throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "id");
        if (MarkRegistry.exists(id)) return fail(context, "zones.mark.error.exists", id);

        ServerPlayer player = context.getSource().getPlayerOrException();
        MapMark mark = new MapMark(id, placed == null ? player.blockPosition() : placed,
                player.serverLevel().dimension().location(), label, MapMark.DEFAULT_COLOR);

        MarkRegistry.upsert(mark);
        ZonesMod.syncMarksToEveryone(context.getSource().getServer());
        return succeed(context, "zones.mark.success.created", id);
    }

    private static int delete(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        if (!MarkRegistry.remove(id)) return fail(context, "zones.mark.error.not_found", id);

        ZonesMod.syncMarksToEveryone(context.getSource().getServer());
        return succeed(context, "zones.mark.success.deleted", id);
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        if (MarkRegistry.all().isEmpty()) {
            context.getSource().sendSuccess(() ->
                    Component.translatable("zones.mark.info.empty").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable("zones.mark.info.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (MapMark mark : MarkRegistry.all()) {
            context.getSource().sendSuccess(() -> describe(mark), false);
        }
        return 1;
    }

    private static MutableComponent describe(MapMark mark) {
        String teams = mark.everyone() ? "*" : String.join(",", mark.teams());
        MutableComponent line = Component.literal(mark.id()).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(String.format(" [%d, %d, %d] %s",
                        mark.position().getX(), mark.position().getY(), mark.position().getZ(), teams))
                        .withStyle(ChatFormatting.GRAY));

        return line.withStyle(style -> style.withClickEvent(
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/battlecraft mark tp " + mark.id())));
    }

    private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MapMark mark = require(context);
        if (mark == null) return 0;

        ServerPlayer player = context.getSource().getPlayerOrException();
        player.teleportTo(player.serverLevel(), mark.position().getX() + 0.5, mark.position().getY(),
                mark.position().getZ() + 0.5, player.getYRot(), player.getXRot());
        return succeed(context, "zones.mark.success.teleported", mark.id());
    }

    private static int editLabel(CommandContext<CommandSourceStack> context) {
        MapMark mark = require(context);
        if (mark == null) return 0;

        mark.setLabel(StringArgumentType.getString(context, "text"));
        return apply(context, mark);
    }

    private static int editColor(CommandContext<CommandSourceStack> context) {
        MapMark mark = require(context);
        if (mark == null) return 0;

        mark.setColor(IntegerArgumentType.getInteger(context, "value"));
        return apply(context, mark);
    }

    private static int editWorld(CommandContext<CommandSourceStack> context) {
        MapMark mark = require(context);
        if (mark == null) return 0;

        mark.setInWorld(BoolArgumentType.getBool(context, "shown"));
        return apply(context, mark);
    }

    private static int moveHere(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MapMark mark = require(context);
        if (mark == null) return 0;

        ServerPlayer player = context.getSource().getPlayerOrException();
        mark.setPosition(player.blockPosition());
        mark.setDimension(player.serverLevel().dimension().location());
        return apply(context, mark);
    }

    private static int showEveryone(CommandContext<CommandSourceStack> context) {
        MapMark mark = require(context);
        if (mark == null) return 0;

        mark.showEveryone();
        return apply(context, mark);
    }

    private static int editTeam(CommandContext<CommandSourceStack> context, boolean allowed) {
        MapMark mark = require(context);
        if (mark == null) return 0;

        String team = StringArgumentType.getString(context, "team");
        if (context.getSource().getServer().getScoreboard().getPlayerTeam(team) == null) {
            return fail(context, "zones.error.unknown_team", team);
        }

        if (allowed) {
            mark.allow(team);
        } else {
            mark.forbid(team);
        }
        return apply(context, mark);
    }

    private static MapMark require(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        MapMark mark = MarkRegistry.byId(id);
        if (mark == null) fail(context, "zones.mark.error.not_found", id);
        return mark;
    }

    private static int apply(CommandContext<CommandSourceStack> context, MapMark mark) {
        MarkRegistry.persist();
        ZonesMod.syncMarksToEveryone(context.getSource().getServer());
        return succeed(context, "zones.mark.success.edited", mark.id());
    }

    private static int succeed(CommandContext<CommandSourceStack> context, String key, Object... args) {
        context.getSource().sendSuccess(() ->
                Component.translatable(key, args).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> context, String key, Object... args) {
        context.getSource().sendFailure(Component.translatable(key, args).withStyle(ChatFormatting.RED));
        return 0;
    }
}
