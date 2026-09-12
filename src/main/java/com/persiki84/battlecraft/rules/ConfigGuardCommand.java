package com.persiki84.battlecraft.rules;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class ConfigGuardCommand {
    private static final int LISTED = 20;

    private static final SuggestionProvider<CommandSourceStack> WATCHED = (context, builder) ->
            SharedSuggestionProvider.suggest(ConfigManifest.paths(), builder);

    private ConfigGuardCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("configs")
                .requires(source -> source.hasPermission(2))
                .executes(ConfigGuardCommand::list)
                .then(Commands.literal("list").executes(ConfigGuardCommand::list))
                .then(Commands.literal("snapshot").executes(ConfigGuardCommand::snapshot))
                .then(Commands.literal("recheck").executes(ConfigGuardCommand::recheck))
                .then(Commands.literal("clear").executes(ConfigGuardCommand::clear))
                .then(Commands.literal("forget")
                        .then(Commands.argument("file", StringArgumentType.greedyString())
                                .suggests(WATCHED)
                                .executes(ConfigGuardCommand::forget)));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        List<String> paths = ConfigManifest.paths();
        if (paths.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("battlecraft.configs.empty")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.translatable("battlecraft.configs.header", paths.size())
                .withStyle(ChatFormatting.GOLD), false);
        for (int index = 0; index < Math.min(paths.size(), LISTED); index++) {
            String path = paths.get(index);
            context.getSource().sendSuccess(() -> Component.literal(" " + path).withStyle(ChatFormatting.GRAY), false);
        }
        if (paths.size() > LISTED) {
            context.getSource().sendSuccess(() -> Component.translatable("battlecraft.configs.tail",
                    paths.size() - LISTED).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int snapshot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer operator = context.getSource().getPlayerOrException();
        ConfigAudit.requestScan(operator);
        context.getSource().sendSuccess(() -> Component.translatable("battlecraft.configs.snapshot.asked")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int recheck(CommandContext<CommandSourceStack> context) {
        ConfigAudit.expectAll(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.translatable("battlecraft.configs.rechecked")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        ConfigManifest.clear(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.translatable("battlecraft.configs.cleared")
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int forget(CommandContext<CommandSourceStack> context) {
        String path = StringArgumentType.getString(context, "file");
        if (!ConfigManifest.forget(context.getSource().getServer(), path)) {
            context.getSource().sendFailure(Component.translatable("battlecraft.configs.unknown", path));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable("battlecraft.configs.forgotten", path)
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }
}
