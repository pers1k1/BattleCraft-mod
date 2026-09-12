package com.persiki84.capturepoints.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.shared.zone.ZoneShape;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Arrays;

public final class ShapeArguments {
    public static final String ARGUMENT_NAME = "shape";

    public static final SuggestionProvider<CommandSourceStack> SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(ZoneShape.values()).map(ZoneShape::id), builder);

    private ShapeArguments() {
    }

    public static ZoneShape read(CommandContext<CommandSourceStack> context) {
        return ZoneShape.byId(StringArgumentType.getString(context, ARGUMENT_NAME));
    }

    public static ZoneShape readOrDefault(CommandContext<CommandSourceStack> context, ZoneShape fallback) {
        return isPresent(context) ? read(context) : fallback;
    }

    private static boolean isPresent(CommandContext<CommandSourceStack> context) {
        try {
            StringArgumentType.getString(context, ARGUMENT_NAME);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
