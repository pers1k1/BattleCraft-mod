package com.persiki84.capturepoints.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.capturepoints.capture.CaptureCommandRunner;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Function;

public final class PointBonusCommands {
    private static final int MAX_AMOUNT = 64;
    private static final int MAX_INTERVAL = 3600;
    private static final int MAX_AMPLIFIER = 5;

    private static final SuggestionProvider<CommandSourceStack> EFFECT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(ForgeRegistries.MOB_EFFECTS.getKeys(), builder);

    private PointBonusCommands() {}

    public static void addBranches(LiteralArgumentBuilder<CommandSourceStack> root,
                                   SuggestionProvider<CommandSourceStack> points,
                                   Function<String, CapturePoint> lookup,
                                   CommandBuildContext build) {
        root.then(rewardBranch(points, lookup, build))
                .then(plain("removereward", "reward_removed", points, lookup, PointBonusCommands::clearReward))
                .then(incomeBranch(points, lookup, build))
                .then(intervalBranch(points, lookup))
                .then(buffBranch(points, lookup))
                .then(plain("clearbuff", "buff_cleared", points, lookup, PointBonusCommands::clearBuff))
                .then(commandBranch(points, lookup))
                .then(addCommandBranch(points, lookup))
                .then(removeCommandBranch(points, lookup))
                .then(plain("clearcommand", "command_cleared", points, lookup, PointBonusCommands::clearCommand));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rewardBranch(
            SuggestionProvider<CommandSourceStack> points, Function<String, CapturePoint> lookup,
            CommandBuildContext build) {
        return Commands.literal("setreward")
                .then(named(points)
                        .then(Commands.argument("item", ItemArgument.item(build))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_AMOUNT))
                                        .executes(context -> setReward(context, lookup)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> incomeBranch(
            SuggestionProvider<CommandSourceStack> points, Function<String, CapturePoint> lookup,
            CommandBuildContext build) {
        return Commands.literal("setincome")
                .then(named(points)
                        .then(Commands.argument("item", ItemArgument.item(build))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0, MAX_AMOUNT))
                                        .executes(context -> setIncome(context, lookup)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> intervalBranch(
            SuggestionProvider<CommandSourceStack> points, Function<String, CapturePoint> lookup) {
        return Commands.literal("setincomeinterval")
                .then(named(points)
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, MAX_INTERVAL))
                                .executes(context -> setInterval(context, lookup))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buffBranch(
            SuggestionProvider<CommandSourceStack> points, Function<String, CapturePoint> lookup) {
        return Commands.literal("setbuff")
                .then(named(points)
                        .then(Commands.argument("effect", StringArgumentType.string())
                                .suggests(EFFECT_SUGGESTIONS)
                                .then(Commands.argument("amplifier", IntegerArgumentType.integer(0, MAX_AMPLIFIER))
                                        .executes(context -> setBuff(context, lookup)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> addCommandBranch(
            SuggestionProvider<CommandSourceStack> points, Function<String, CapturePoint> lookup) {
        return Commands.literal("addcommand")
                .then(named(points)
                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                .executes(context -> addCommand(context, lookup))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> removeCommandBranch(
            SuggestionProvider<CommandSourceStack> points, Function<String, CapturePoint> lookup) {
        return Commands.literal("removecommand")
                .then(named(points)
                        .then(Commands.argument("index", IntegerArgumentType.integer(1, CapturePoint.MAX_COMMANDS))
                                .executes(context -> removeCommand(context, lookup))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commandBranch(
            SuggestionProvider<CommandSourceStack> points, Function<String, CapturePoint> lookup) {
        return Commands.literal("setcommand")
                .then(named(points)
                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                .executes(context -> setCommand(context, lookup))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> plain(
            String literal, String done, SuggestionProvider<CommandSourceStack> points,
            Function<String, CapturePoint> lookup, java.util.function.Consumer<CapturePoint> apply) {
        return Commands.literal(literal)
                .then(named(points).executes(context -> {
                    CapturePoint point = resolve(context, lookup);
                    if (point == null) return 0;

                    apply.accept(point);
                    return confirm(context, "capturepoints.success." + done, point);
                }));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> named(
            SuggestionProvider<CommandSourceStack> points) {
        return Commands.argument("name", StringArgumentType.string()).suggests(points);
    }

    private static int setReward(CommandContext<CommandSourceStack> context, Function<String, CapturePoint> lookup)
            throws CommandSyntaxException {
        CapturePoint point = resolve(context, lookup);
        if (point == null) return 0;

        point.setReward(ItemArgument.getItem(context, "item").createItemStack(1, false));
        point.setRewardAmount(IntegerArgumentType.getInteger(context, "amount"));
        return confirm(context, "capturepoints.success.reward_updated", point);
    }

    private static void clearReward(CapturePoint point) {
        point.clearReward();
    }

    private static int setIncome(CommandContext<CommandSourceStack> context, Function<String, CapturePoint> lookup)
            throws CommandSyntaxException {
        CapturePoint point = resolve(context, lookup);
        if (point == null) return 0;

        ItemStack item = ItemArgument.getItem(context, "item").createItemStack(1, false);
        point.setIncomeItem(item);
        point.setPassiveIncomeAmount(IntegerArgumentType.getInteger(context, "amount"));
        point.resetIncomeTimer();
        return confirm(context, "capturepoints.success.income_updated", point);
    }

    private static int setInterval(CommandContext<CommandSourceStack> context, Function<String, CapturePoint> lookup) {
        CapturePoint point = resolve(context, lookup);
        if (point == null) return 0;

        point.setIncomeIntervalSeconds(IntegerArgumentType.getInteger(context, "seconds"));
        point.resetIncomeTimer();
        return confirm(context, "capturepoints.success.income_interval_updated", point);
    }

    private static int setBuff(CommandContext<CommandSourceStack> context, Function<String, CapturePoint> lookup) {
        CapturePoint point = resolve(context, lookup);
        if (point == null) return 0;

        point.setBuffEffect(StringArgumentType.getString(context, "effect"));
        point.setBuffAmplifier(IntegerArgumentType.getInteger(context, "amplifier"));
        return confirm(context, "capturepoints.success.buff_updated", point);
    }

    private static void clearBuff(CapturePoint point) {
        point.setBuffEffect(null);
        point.setBuffAmplifier(0);
    }

    private static int setCommand(CommandContext<CommandSourceStack> context, Function<String, CapturePoint> lookup) {
        CapturePoint point = accepted(context, lookup);
        if (point == null) return 0;

        point.setCaptureCommand(StringArgumentType.getString(context, "command").trim());
        return confirm(context, "capturepoints.success.command_set", point);
    }

    private static int addCommand(CommandContext<CommandSourceStack> context, Function<String, CapturePoint> lookup) {
        CapturePoint point = accepted(context, lookup);
        if (point == null) return 0;

        if (!point.addCaptureCommand(StringArgumentType.getString(context, "command").trim())) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.too_many_commands",
                    CapturePoint.MAX_COMMANDS).withStyle(ChatFormatting.RED));
            return 0;
        }
        return confirm(context, "capturepoints.success.command_added", point);
    }

    // WHY: в списке команды нумеруются с единицы, как их видит оператор в меню и в чате
    private static int removeCommand(CommandContext<CommandSourceStack> context,
                                     Function<String, CapturePoint> lookup) {
        CapturePoint point = resolve(context, lookup);
        if (point == null) return 0;

        int index = IntegerArgumentType.getInteger(context, "index") - 1;
        if (!point.removeCaptureCommand(index)) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.no_command",
                    index + 1).withStyle(ChatFormatting.RED));
            return 0;
        }
        return confirm(context, "capturepoints.success.command_removed", point);
    }

    private static CapturePoint accepted(CommandContext<CommandSourceStack> context,
                                         Function<String, CapturePoint> lookup) {
        CapturePoint point = resolve(context, lookup);
        if (point == null) return null;

        if (StringArgumentType.getString(context, "command").trim().length() <= CaptureCommandRunner.MAX_LENGTH) {
            return point;
        }
        context.getSource().sendFailure(Component.translatable("capturepoints.error.command_too_long",
                CaptureCommandRunner.MAX_LENGTH).withStyle(ChatFormatting.RED));
        return null;
    }

    private static void clearCommand(CapturePoint point) {
        point.clearCaptureCommands();
    }

    private static CapturePoint resolve(CommandContext<CommandSourceStack> context,
                                        Function<String, CapturePoint> lookup) {
        CapturePoint point = lookup.apply(StringArgumentType.getString(context, "name"));
        if (point == null) {
            context.getSource().sendFailure(Component.translatable("capturepoints.error.point_not_found")
                    .withStyle(ChatFormatting.RED));
        }
        return point;
    }

    private static int confirm(CommandContext<CommandSourceStack> context, String key, CapturePoint point) {
        CapturePointManager.persist();
        context.getSource().sendSuccess(() -> Component.translatable(key,
                Component.literal(point.getName()).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
