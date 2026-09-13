package com.persiki84.capturepoints.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.capturepoints.capture.CaptureMode;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.capture.CooldownScope;
import com.persiki84.capturepoints.capture.RewardSplit;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;

public final class CaptureTuningCommands {
    private static final int MAX_PERCENT = 2000;
    private static final int MAX_COOLDOWN = 3600;

    private static final SuggestionProvider<CommandSourceStack> MODE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(ids(CaptureMode.values(), CaptureMode::id), builder);

    private static final SuggestionProvider<CommandSourceStack> SPLIT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(ids(RewardSplit.values(), RewardSplit::id), builder);

    private static final SuggestionProvider<CommandSourceStack> SCOPE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(ids(CooldownScope.values(), CooldownScope::id), builder);

    private CaptureTuningCommands() {}

    public static void addBranches(LiteralArgumentBuilder<CommandSourceStack> root,
                                   SuggestionProvider<CommandSourceStack> points,
                                   Function<String, CapturePoint> lookup) {
        root.then(wordBranch("setmode", points, lookup, CaptureTuningCommands::applyMode, MODE_SUGGESTIONS))
                .then(wordBranch("setrewardsplit", points, lookup, CaptureTuningCommands::applySplit, SPLIT_SUGGESTIONS))
                .then(wordBranch("setteamcooldownscope", points, lookup, CaptureTuningCommands::applyScope, SCOPE_SUGGESTIONS))
                .then(numberBranch("setcapturespeed", points, lookup, 1, MAX_PERCENT, CapturePoint::setCaptureSpeed))
                .then(numberBranch("setrollback", points, lookup, 1, MAX_PERCENT, CapturePoint::setRollbackSpeed))
                .then(numberBranch("setpressuredrollback", points, lookup, 1, MAX_PERCENT, CapturePoint::setPressuredRollbackSpeed))
                .then(numberBranch("setownedrollback", points, lookup, 1, MAX_PERCENT, CapturePoint::setOwnedRollbackSpeed))
                .then(numberBranch("setteamcooldown", points, lookup, 0, MAX_COOLDOWN, CapturePoint::setTeamCooldown))
                .then(flagBranch("setrequired", points, lookup, CapturePoint::setRequired))
                .then(flagBranch("sethud", points, lookup, CapturePoint::setShownInHud));
    }

    // WHY: обязательность и показ в HUD живут в общей ветке обоих корней, как бонусы точки:
    // WHY: заведённое только у обычных точек финальные молча не получают
    private static LiteralArgumentBuilder<CommandSourceStack> flagBranch(
            String literal, SuggestionProvider<CommandSourceStack> points, Function<String, CapturePoint> lookup,
            BiConsumer<CapturePoint, Boolean> apply) {
        return Commands.literal(literal)
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(points)
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(context -> applyFlag(context, lookup, apply))));
    }

    private static int applyFlag(CommandContext<CommandSourceStack> context, Function<String, CapturePoint> lookup,
                                 BiConsumer<CapturePoint, Boolean> apply) {
        CapturePoint point = resolve(context, lookup);
        if (point == null) return 0;

        apply.accept(point, BoolArgumentType.getBool(context, "value"));
        return confirm(context, point);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> numberBranch(
            String literal, SuggestionProvider<CommandSourceStack> points, Function<String, CapturePoint> lookup,
            int minimum, int maximum, ObjIntConsumer<CapturePoint> apply) {
        return Commands.literal(literal)
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(points)
                        .then(Commands.argument("value", IntegerArgumentType.integer(minimum, maximum))
                                .executes(context -> applyNumber(context, lookup, apply))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> wordBranch(
            String literal, SuggestionProvider<CommandSourceStack> points, Function<String, CapturePoint> lookup,
            java.util.function.BiConsumer<CapturePoint, String> apply,
            SuggestionProvider<CommandSourceStack> values) {
        return Commands.literal(literal)
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(points)
                        .then(Commands.argument("value", StringArgumentType.word())
                                .suggests(values)
                                .executes(context -> applyWord(context, lookup, apply))));
    }

    private static int applyNumber(CommandContext<CommandSourceStack> context, Function<String, CapturePoint> lookup,
                                   ObjIntConsumer<CapturePoint> apply) {
        CapturePoint point = resolve(context, lookup);
        if (point == null) return 0;

        apply.accept(point, IntegerArgumentType.getInteger(context, "value"));
        return confirm(context, point);
    }

    private static int applyWord(CommandContext<CommandSourceStack> context, Function<String, CapturePoint> lookup,
                                 java.util.function.BiConsumer<CapturePoint, String> apply) {
        CapturePoint point = resolve(context, lookup);
        if (point == null) return 0;

        apply.accept(point, StringArgumentType.getString(context, "value"));
        return confirm(context, point);
    }

    private static void applyMode(CapturePoint point, String value) {
        point.setMode(CaptureMode.byId(value));
        CapturePointManager.cancelCaptureForPoint(point.getName());
    }

    private static void applySplit(CapturePoint point, String value) {
        point.setRewardSplit(RewardSplit.byId(value));
    }

    private static void applyScope(CapturePoint point, String value) {
        point.setTeamCooldownScope(CooldownScope.byId(value));
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

    private static int confirm(CommandContext<CommandSourceStack> context, CapturePoint point) {
        CapturePointManager.persist();
        CapturePointManager.syncPoints();
        CapturePointManager.syncFinalPoints();
        context.getSource().sendSuccess(() -> Component.translatable("capturepoints.success.tuning_updated",
                Component.literal(point.getName()).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static <T> List<String> ids(T[] values, Function<T, String> id) {
        List<String> result = new ArrayList<>();
        for (T value : values) {
            result.add(id.apply(value));
        }
        return result;
    }
}
