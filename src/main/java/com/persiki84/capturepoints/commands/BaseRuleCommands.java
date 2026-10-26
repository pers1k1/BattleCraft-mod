package com.persiki84.capturepoints.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.persiki84.capturepoints.capture.BaseRules;
import com.persiki84.capturepoints.capture.CapturePointManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

// WHY: возможности сети баз общие для всех точек-баз: возрождение на них, перелёт и магазин
final class BaseRuleCommands {
    private static final String VALUE = "value";

    private BaseRuleCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("bases")
                .then(flag("respawn", BaseRules::respawn))
                .then(flag("travel", BaseRules::travel))
                .then(flag("shop", BaseRules::shop))
                .then(number("channel", BaseRules.CHANNEL_MIN, BaseRules.CHANNEL_MAX, BaseRules::channelSeconds))
                .then(number("cooldown", 0, BaseRules.COOLDOWN_MAX, BaseRules::cooldownSeconds));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> flag(String name, Consumer<Boolean> apply) {
        return Commands.literal(name).then(Commands.argument(VALUE, BoolArgumentType.bool())
                .executes(context -> applied(context, name, () -> apply.accept(BoolArgumentType.getBool(context, VALUE)),
                        String.valueOf(BoolArgumentType.getBool(context, VALUE)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> number(String name, int minimum, int maximum,
                                                                     IntConsumer apply) {
        return Commands.literal(name).then(Commands.argument(VALUE, IntegerArgumentType.integer(minimum, maximum))
                .executes(context -> applied(context, name, () -> apply.accept(IntegerArgumentType.getInteger(context, VALUE)),
                        String.valueOf(IntegerArgumentType.getInteger(context, VALUE)))));
    }

    private static int applied(CommandContext<CommandSourceStack> context, String name, Runnable change, String value) {
        change.run();
        CapturePointManager.persist();
        context.getSource().sendSuccess(() -> Component.translatable("capturepoints.success.bases",
                Component.translatable("capturepoints.bases." + name), value).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
