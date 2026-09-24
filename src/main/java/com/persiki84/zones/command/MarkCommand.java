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
import com.persiki84.battlecraft.rules.MarkerRange;
import com.persiki84.shared.zone.ZoneShape;
import com.persiki84.zones.mark.MapMark;
import com.persiki84.zones.mark.MarkHideZone;
import com.persiki84.zones.mark.MarkKind;
import com.persiki84.zones.mark.MarkPalette;
import com.persiki84.zones.mark.MarkRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.UnaryOperator;

public final class MarkCommand {
    public static final String MENU_ID = "marks";

    private static final SuggestionProvider<CommandSourceStack> MARK_IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(MarkRegistry.ids(), builder);

    private static final SuggestionProvider<CommandSourceStack> PALETTE = (context, builder) ->
            SharedSuggestionProvider.suggest(MarkPalette.suggestions(), builder);

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
                        .then(kindEdit())
                        .then(scaleEdit())
                        .then(markerRangeEdit())
                        .then(hideZoneEdit())
                        .then(lineEdit())
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

    private static LiteralArgumentBuilder<CommandSourceStack> kindEdit() {
        LiteralArgumentBuilder<CommandSourceStack> branch = Commands.literal("kind");
        for (MarkKind kind : MarkKind.values()) {
            branch.then(Commands.literal(kind.id()).executes(context -> editKind(context, kind)));
        }
        return branch;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> scaleEdit() {
        return Commands.literal("scale")
                .then(Commands.argument("percent",
                                IntegerArgumentType.integer(MapMark.SCALE_MIN, MapMark.SCALE_MAX))
                        .executes(MarkCommand::editScale));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> markerRangeEdit() {
        return Commands.literal("markerrange")
                .then(Commands.argument("blocks",
                                IntegerArgumentType.integer(MapMark.KIND_RANGE, MarkerRange.MAX_BLOCKS))
                        .executes(MarkCommand::editMarkerRange));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> hideZoneEdit() {
        return Commands.literal("hidezone")
                .then(Commands.literal("radius")
                        .then(Commands.argument("blocks",
                                        IntegerArgumentType.integer(MarkHideZone.OFF, MarkHideZone.MAX_RADIUS))
                                .executes(context -> editHideZone(context, zone ->
                                        zone.withRadius(IntegerArgumentType.getInteger(context, "blocks"))))))
                .then(hideShapeEdit())
                .then(Commands.literal("height")
                        .then(Commands.argument("blocks",
                                        IntegerArgumentType.integer(MarkHideZone.WHOLE_COLUMN, MarkHideZone.MAX_HEIGHT))
                                .executes(context -> editHideZone(context, zone ->
                                        zone.withHeight(IntegerArgumentType.getInteger(context, "blocks"))))))
                .then(Commands.literal("show")
                        .then(Commands.argument("shown", BoolArgumentType.bool())
                                .executes(context -> editHideZone(context, zone ->
                                        zone.withShown(BoolArgumentType.getBool(context, "shown"))))))
                .then(hideColorEdit())
                .then(Commands.literal("off")
                        .executes(context -> editHideZone(context, zone -> zone.withRadius(MarkHideZone.OFF))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> hideColorEdit() {
        return Commands.literal("color")
                .then(Commands.literal("mark")
                        .executes(context -> editHideZone(context, zone -> zone.withColor(MarkHideZone.MARK_COLOR))))
                .then(Commands.argument("value", IntegerArgumentType.integer())
                        .suggests(PALETTE)
                        .executes(context -> editHideZone(context, zone ->
                                zone.withColor(IntegerArgumentType.getInteger(context, "value")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> hideShapeEdit() {
        LiteralArgumentBuilder<CommandSourceStack> branch = Commands.literal("shape");
        for (ZoneShape shape : ZoneShape.values()) {
            branch.then(Commands.literal(shape.id())
                    .executes(context -> editHideZone(context, zone -> zone.withShape(shape))));
        }
        return branch;
    }

    private static int editHideZone(CommandContext<CommandSourceStack> context, UnaryOperator<MarkHideZone> change) {
        MapMark mark = require(context);
        if (mark == null) return 0;

        mark.setHideZone(change.apply(mark.hideZone()));
        return apply(context, mark);
    }

    private static int editMarkerRange(CommandContext<CommandSourceStack> context) {
        MapMark mark = require(context);
        if (mark == null) return 0;

        mark.setMarkerRange(IntegerArgumentType.getInteger(context, "blocks"));
        return apply(context, mark);
    }

    private static int editScale(CommandContext<CommandSourceStack> context) {
        MapMark mark = require(context);
        if (mark == null) return 0;

        mark.setScalePercent(IntegerArgumentType.getInteger(context, "percent"));
        return apply(context, mark);
    }

    // WHY: строки надписи правятся по номеру, а первая это та же подпись метки: отдельной ветки
    // WHY: под неё нет, иначе у одной строки оказалось бы два имени в дереве команд
    private static LiteralArgumentBuilder<CommandSourceStack> lineEdit() {
        return Commands.literal("line")
                .then(Commands.literal("add")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(MarkCommand::addLine)))
                .then(Commands.literal("set")
                        .then(Commands.argument("index", IntegerArgumentType.integer(0, MapMark.MAX_LINES - 1))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(MarkCommand::setLine))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1, MapMark.MAX_LINES - 1))
                                .executes(MarkCommand::removeLine)));
    }

    private static int editKind(CommandContext<CommandSourceStack> context, MarkKind kind) {
        MapMark mark = require(context);
        if (mark == null) return 0;

        mark.setKind(kind);
        if (kind == MarkKind.TEXT) mark.setInWorld(false);
        return apply(context, mark);
    }

    private static int addLine(CommandContext<CommandSourceStack> context) {
        MapMark mark = require(context);
        if (mark == null) return 0;
        if (!mark.addLine(label(context))) return fail(context, "zones.mark.error.too_many_lines", MapMark.MAX_LINES);

        return apply(context, mark);
    }

    private static int setLine(CommandContext<CommandSourceStack> context) {
        MapMark mark = require(context);
        if (mark == null) return 0;

        int index = IntegerArgumentType.getInteger(context, "index");
        if (!mark.setLine(index, label(context))) return fail(context, "zones.mark.error.no_line", index);
        return apply(context, mark);
    }

    private static int removeLine(CommandContext<CommandSourceStack> context) {
        MapMark mark = require(context);
        if (mark == null) return 0;

        int index = IntegerArgumentType.getInteger(context, "index");
        if (!mark.removeLine(index)) return fail(context, "zones.mark.error.no_line", index);
        return apply(context, mark);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> colorEdit() {
        return Commands.literal("color")
                .then(Commands.argument("value", IntegerArgumentType.integer())
                        .suggests(PALETTE)
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
        ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, mark.dimension()));
        if (level == null) return fail(context, "zones.mark.error.no_world", mark.dimension().toString());

        player.teleportTo(level, mark.position().getX() + 0.5, mark.position().getY(),
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
