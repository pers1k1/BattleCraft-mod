package com.persiki84.battlecraft.rules;

import com.mojang.brigadier.arguments.BoolArgumentType;
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

public final class GameRulesCommand {

    private static final SuggestionProvider<CommandSourceStack> RULES = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(GameRule.values()).map(GameRule::id), builder);

    private GameRulesCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("rules")
                .requires(source -> source.hasPermission(2))
                .executes(context -> BattleCraftCommands.openMenu(context, ModuleMenuStates.GAME_RULES))
                .then(Commands.literal("list").executes(GameRulesCommand::list))
                .then(Commands.literal("reset").executes(GameRulesCommand::reset))
                .then(Commands.literal("set")
                        .then(Commands.argument("rule", StringArgumentType.word())
                                .suggests(RULES)
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(GameRulesCommand::set))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.translatable("battlecraft.rules.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        for (GameRule rule : GameRule.values()) {
            context.getSource().sendSuccess(() -> describe(rule), false);
        }
        return 1;
    }

    private static MutableComponent describe(GameRule rule) {
        boolean enabled = GameRules.allows(rule);
        return Component.literal(rule.id() + " ").withStyle(ChatFormatting.YELLOW)
                .append(Component.translatable(enabled ? "battlecraft.rules.on" : "battlecraft.rules.off")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal(" ").append(Component.translatable(rule.label()))
                        .withStyle(ChatFormatting.GRAY));
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "rule");
        GameRule rule = GameRule.byId(id);
        if (rule == null) {
            context.getSource().sendFailure(Component.translatable("battlecraft.rules.unknown", id)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        GameRules.set(rule, enabled);
        GameRulesEvents.syncToAll(context.getSource().getServer());

        context.getSource().sendSuccess(() -> Component.translatable(
                enabled ? "battlecraft.rules.enabled" : "battlecraft.rules.disabled",
                Component.translatable(rule.label())).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        GameRules.reset();
        GameRulesEvents.syncToAll(context.getSource().getServer());

        context.getSource().sendSuccess(() -> Component.translatable("battlecraft.rules.reset")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
