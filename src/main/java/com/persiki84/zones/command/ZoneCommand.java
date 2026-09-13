package com.persiki84.zones.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.battlecraft.BattleCraftCommands;
import com.persiki84.shared.zone.ZoneArea;
import com.persiki84.shared.zone.ZoneShape;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneRegistry;
import com.persiki84.zones.ZoneRule;
import com.persiki84.zones.ZoneSource;
import com.persiki84.zones.ZoneType;
import com.persiki84.zones.ZonesMod;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

public final class ZoneCommand {

    private static final SuggestionProvider<CommandSourceStack> ZONE_IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(ZoneRegistry.ids(), builder);

    private static final SuggestionProvider<CommandSourceStack> SHAPES = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(ZoneShape.values()).map(ZoneShape::id), builder);

    private static final SuggestionProvider<CommandSourceStack> TYPES = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(ZoneType.values())
                    .filter(ZoneType::isCreatableByCommand)
                    .map(ZoneType::id), builder);

    private static final SuggestionProvider<CommandSourceStack> TEAMS = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getServer().getScoreboard().getTeamNames(), builder);

    private static final SuggestionProvider<CommandSourceStack> RULES = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(ZoneRule.values()).map(ZoneRule::id), builder);

    private ZoneCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("zone")
                .requires(source -> source.hasPermission(2))
                .executes(context -> BattleCraftCommands.openMenu(context, ZonesMod.ZONES_MENU_ID))
                .then(createBranch())
                .then(deleteBranch())
                .then(listBranch())
                .then(teleportBranch())
                .then(rulesBranch())
                .then(editBranch());
    }

    private static LiteralArgumentBuilder<CommandSourceStack>createBranch() {
        return Commands.literal("create")
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("shape", StringArgumentType.word())
                                .suggests(SHAPES)
                                .then(Commands.argument("size", DoubleArgumentType.doubleArg(1.0, 512.0))
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests(TYPES)
                                                .executes(context -> create(context, null))
                                                .then(Commands.argument("owner", StringArgumentType.word())
                                                        .suggests(TEAMS)
                                                        .executes(context -> create(context,
                                                                StringArgumentType.getString(context, "owner"))))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack>deleteBranch() {
        return Commands.literal("delete")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(ZONE_IDS)
                        .executes(ZoneCommand::delete));
    }

    private static LiteralArgumentBuilder<CommandSourceStack>listBranch() {
        return Commands.literal("list").executes(ZoneCommand::list);
    }

    private static LiteralArgumentBuilder<CommandSourceStack>teleportBranch() {
        return Commands.literal("tp")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(ZONE_IDS)
                        .executes(ZoneCommand::teleport));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> editBranch() {
        return Commands.literal("edit")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(ZONE_IDS)
                        .then(shapeEdits())
                        .then(sizeEdits())
                        .then(heightEdits())
                        .then(typeEdits())
                        .then(ownerEdits())
                        .then(clearOwnerEdit())
                        .then(colorEdits())
                        .then(clearColorEdit())
                        .then(placementEdits())
                        .then(spawnEdits())
                        .then(ruleEdits()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sizeEdits() {
        return Commands.literal("size")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0, 512.0))
                        .executes(ZoneCommand::editSize));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shapeEdits() {
        return Commands.literal("shape")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(SHAPES)
                        .executes(ZoneCommand::editShape));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> typeEdits() {
        return Commands.literal("type")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(TYPES)
                        .executes(ZoneCommand::editType));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> ownerEdits() {
        return Commands.literal("owner")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(TEAMS)
                        .executes(ZoneCommand::editOwner));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> clearOwnerEdit() {
        return Commands.literal("clearowner").executes(ZoneCommand::clearOwner);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> colorEdits() {
        return Commands.literal("color")
                .then(Commands.argument("value", IntegerArgumentType.integer())
                        .executes(ZoneCommand::editColor));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> clearColorEdit() {
        return Commands.literal("clearcolor").executes(ZoneCommand::clearColor);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> heightEdits() {
        return Commands.literal("height")
                .then(Commands.argument("up", DoubleArgumentType.doubleArg(0.0, 256.0))
                        .then(Commands.argument("down", DoubleArgumentType.doubleArg(0.0, 256.0))
                                .executes(ZoneCommand::editHeight)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> placementEdits() {
        return Commands.literal("here").executes(ZoneCommand::moveHere);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spawnEdits() {
        return Commands.literal("spawn")
                .then(Commands.literal("here").executes(ZoneCommand::spawnHere))
                .then(Commands.literal("random").executes(ZoneCommand::spawnRandom));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> ruleEdits() {
        return Commands.literal("rule")
                .then(Commands.argument("rule", StringArgumentType.word())
                        .suggests(RULES)
                        .then(Commands.literal("allow").executes(context -> setRule(context, true)))
                        .then(Commands.literal("deny").executes(context -> setRule(context, false)))
                        .then(Commands.literal("default").executes(ZoneCommand::resetRule)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rulesBranch() {
        return Commands.literal("rules")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(ZONE_IDS)
                        .executes(ZoneCommand::listRules));
    }

    private static int create(CommandContext<CommandSourceStack> context, String owner) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "id");
        if (ZoneRegistry.exists(id)) {
            return fail(context, "zones.error.zone_exists", id);
        }

        ZoneShape shape = ZoneShape.byId(StringArgumentType.getString(context, "shape"));
        if (shape == null) return fail(context, "zones.error.unknown_shape");

        ZoneType type = ZoneType.byId(StringArgumentType.getString(context, "type"));
        if (type == null || !type.isCreatableByCommand()) return fail(context, "zones.error.unknown_type");

        ServerPlayer player = context.getSource().getPlayerOrException();
        double size = DoubleArgumentType.getDouble(context, "size");
        ZoneArea area = new ZoneArea(shape, player.blockPosition(), size, ZoneArea.DEFAULT_HEIGHT, ZoneArea.DEFAULT_HEIGHT);
        if (owner != null && context.getSource().getServer().getScoreboard().getPlayerTeam(owner) == null) {
            return fail(context, "zones.error.unknown_team", owner);
        }

        Zone zone = new Zone(id, area, type, owner, Zone.TEAM_COLOR, ZoneSource.STORED);

        ZoneRegistry.upsert(zone);
        ZonesMod.broadcastUpsert(zone);
        return succeed(context, "zones.success.created", id);
    }

    private static int delete(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        if (!ZoneRegistry.remove(id)) return fail(context, "zones.error.zone_not_found", id);

        ZonesMod.broadcastRemoval(id);
        return succeed(context, "zones.success.deleted", id);
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        if (ZoneRegistry.all().isEmpty()) {
            context.getSource().sendSuccess(() ->
                    Component.translatable("zones.info.empty").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        context.getSource().sendSuccess(() ->
                Component.translatable("zones.info.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        for (Zone zone : ZoneRegistry.all()) {
            context.getSource().sendSuccess(() -> describe(zone), false);
        }
        return 1;
    }

    private static MutableComponent describe(Zone zone) {
        BlockPos center = zone.area().center();
        String owner = zone.ownerTeam() == null ? "-" : zone.ownerTeam();
        MutableComponent line = Component.literal(zone.id()).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(String.format(" %s %s %.1f [%d, %d, %d] %s",
                        zone.type().id(), zone.area().shape().id(), zone.area().size(),
                        center.getX(), center.getY(), center.getZ(), owner)).withStyle(ChatFormatting.GRAY));
        return line.withStyle(style -> style.withClickEvent(
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/battlecraft zone tp " + zone.id())));
    }

    private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Zone zone = requireZone(context);
        if (zone == null) return 0;

        ServerPlayer player = context.getSource().getPlayerOrException();
        player.teleportTo(player.serverLevel(), zone.area().centerX(), zone.area().center().getY(),
                zone.area().centerZ(), player.getYRot(), player.getXRot());
        return succeed(context, "zones.success.teleported", zone.id());
    }

    private static int editSize(CommandContext<CommandSourceStack> context) {
        Zone zone = requireZone(context);
        if (zone == null) return 0;

        zone.setArea(zone.area().withSize(DoubleArgumentType.getDouble(context, "value")));
        return applyEdit(context, zone);
    }

    private static int editShape(CommandContext<CommandSourceStack> context) {
        Zone zone = requireZone(context);
        if (zone == null) return 0;

        ZoneShape shape = ZoneShape.byId(StringArgumentType.getString(context, "value"));
        if (shape == null) return fail(context, "zones.error.unknown_shape");

        zone.setArea(zone.area().withShape(shape));
        return applyEdit(context, zone);
    }

    private static int editType(CommandContext<CommandSourceStack> context) {
        Zone zone = requireZone(context);
        if (zone == null) return 0;

        ZoneType type = ZoneType.byId(StringArgumentType.getString(context, "value"));
        if (type == null || !type.isCreatableByCommand()) return fail(context, "zones.error.unknown_type");

        zone.setType(type);
        return applyEdit(context, zone);
    }

    private static int editOwner(CommandContext<CommandSourceStack> context) {
        Zone zone = requireZone(context);
        if (zone == null) return 0;

        String team = StringArgumentType.getString(context, "value");
        if (context.getSource().getServer().getScoreboard().getPlayerTeam(team) == null) {
            return fail(context, "zones.error.unknown_team", team);
        }

        zone.setOwnerTeam(team);
        return applyEdit(context, zone);
    }

    private static int clearOwner(CommandContext<CommandSourceStack> context) {
        Zone zone = requireZone(context);
        if (zone == null) return 0;

        zone.setOwnerTeam(null);
        return applyEdit(context, zone);
    }

    private static int editColor(CommandContext<CommandSourceStack> context) {
        Zone zone = requireZone(context);
        if (zone == null) return 0;

        zone.setColor(IntegerArgumentType.getInteger(context, "value"));
        return applyEdit(context, zone);
    }

    private static int clearColor(CommandContext<CommandSourceStack> context) {
        Zone zone = requireZone(context);
        if (zone == null) return 0;

        zone.setColor(Zone.TEAM_COLOR);
        return applyEdit(context, zone);
    }

    private static int editHeight(CommandContext<CommandSourceStack> context) {
        Zone zone = requireZone(context);
        if (zone == null) return 0;

        double up = DoubleArgumentType.getDouble(context, "up");
        double down = DoubleArgumentType.getDouble(context, "down");
        zone.setArea(zone.area().withHeight(up, down));
        return applyEdit(context, zone);
    }

    private static int moveHere(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Zone zone = requireZone(context);
        if (zone == null) return 0;

        ServerPlayer player = context.getSource().getPlayerOrException();
        zone.setArea(zone.area().withCenter(player.blockPosition()));
        return applyEdit(context, zone);
    }

    private static int spawnHere(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Zone zone = requireZone(context);
        if (zone == null) return 0;

        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!zone.area().contains(player.getX(), player.getY(), player.getZ())) {
            return fail(context, "zones.error.anchor_outside", zone.id());
        }

        zone.setSpawnAnchor(player.blockPosition());
        return applyEdit(context, zone);
    }

    private static int spawnRandom(CommandContext<CommandSourceStack> context) {
        Zone zone = requireZone(context);
        if (zone == null) return 0;

        zone.setSpawnAnchor(null);
        return applyEdit(context, zone);
    }

    private static int setRule(CommandContext<CommandSourceStack> context, boolean allowed) {
        Zone zone = requireZone(context);
        if (zone == null) return 0;

        ZoneRule rule = requireRule(context);
        if (rule == null) return 0;

        zone.rules().override(rule, allowed);
        return applyEdit(context, zone);
    }

    private static int resetRule(CommandContext<CommandSourceStack> context) {
        Zone zone = requireZone(context);
        if (zone == null) return 0;

        ZoneRule rule = requireRule(context);
        if (rule == null) return 0;

        zone.rules().resetToDefault(rule);
        return applyEdit(context, zone);
    }

    private static ZoneRule requireRule(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "rule");
        ZoneRule rule = ZoneRule.byId(id);
        if (rule == null) fail(context, "zones.error.unknown_rule", id);
        return rule;
    }

    private static int listRules(CommandContext<CommandSourceStack> context) {
        Zone zone = requireZone(context);
        if (zone == null) return 0;

        context.getSource().sendSuccess(() -> Component.translatable("zones.rule.header", zone.id())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        for (ZoneRule rule : ZoneRule.values()) {
            context.getSource().sendSuccess(() -> describeRule(zone, rule), false);
        }
        return 1;
    }

    private static MutableComponent describeRule(Zone zone, ZoneRule rule) {
        boolean allowed = zone.allows(rule);
        MutableComponent state = Component.translatable(allowed ? "zones.rule.allowed" : "zones.rule.denied")
                .withStyle(allowed ? ChatFormatting.GREEN : ChatFormatting.RED);
        MutableComponent origin = Component.translatable(zone.rules().isOverridden(rule)
                ? "zones.rule.origin.custom" : "zones.rule.origin.default").withStyle(ChatFormatting.DARK_GRAY);

        return Component.literal(rule.id() + " ").withStyle(ChatFormatting.YELLOW)
                .append(state).append(Component.literal(" ")).append(origin)
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "/battlecraft zone edit " + zone.id() + " rule " + rule.id() + " ")));
    }

    private static Zone requireZone(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        Zone zone = ZoneRegistry.byId(id);
        if (zone == null) fail(context, "zones.error.zone_not_found", id);
        return zone;
    }

    private static int applyEdit(CommandContext<CommandSourceStack> context, Zone zone) {
        ZoneRegistry.persist();
        ZonesMod.broadcastUpsert(zone);
        return succeed(context, "zones.success.edited", zone.id());
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
