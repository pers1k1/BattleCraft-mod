package com.persiki84.battlecraft.rules;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.battlecraft.BattleCraftCommands;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Arrays;

public final class MarkerRangeCommand {

    private static final SuggestionProvider<CommandSourceStack> KINDS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(MarkerRange.values()).map(MarkerRange::id), builder);

    private MarkerRangeCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("markers")
                .requires(source -> source.hasPermission(2))
                .executes(context -> BattleCraftCommands.openMenu(context, ModuleMenuStates.GAME_RULES))
                .then(Commands.literal("list").executes(MarkerRangeCommand::list))
                .then(Commands.literal("reset").executes(MarkerRangeCommand::reset))
                .then(Commands.literal("range")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests(KINDS)
                                .then(Commands.argument("blocks", IntegerArgumentType
                                                .integer(MarkerRange.MIN_BLOCKS, MarkerRange.MAX_BLOCKS))
                                        .executes(MarkerRangeCommand::set))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.translatable("battlecraft.markers.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        for (MarkerRange range : MarkerRange.values()) {
            context.getSource().sendSuccess(() -> describe(range), false);
        }
        return 1;
    }

    private static MutableComponent describe(MarkerRange range) {
        return Component.literal(range.id() + " ").withStyle(ChatFormatting.YELLOW)
                .append(Component.translatable("battlecraft.markers.blocks", MarkerRanges.blocks(range))
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" ").append(Component.translatable(range.label()))
                        .withStyle(ChatFormatting.GRAY));
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "kind");
        MarkerRange range = MarkerRange.byId(id);
        if (range == null) {
            context.getSource().sendFailure(Component.translatable("battlecraft.markers.unknown", id)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        int blocks = IntegerArgumentType.getInteger(context, "blocks");
        MarkerRanges.set(range, blocks);
        GameRulesEvents.syncToAll(context.getSource().getServer());

        context.getSource().sendSuccess(() -> Component.translatable("battlecraft.markers.set",
                Component.translatable(range.label()), blocks).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        MarkerRanges.reset();
        GameRulesEvents.syncToAll(context.getSource().getServer());

        context.getSource().sendSuccess(() -> Component.translatable("battlecraft.markers.reset",
                MarkerRange.DEFAULT_BLOCKS).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
