package com.persiki84.knockdown.command;

import com.persiki84.knockdown.config.KnockdownConfig;
import com.persiki84.shared.ConfigHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class KnockdownCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("knockdown")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("config")
                        .then(Commands.literal("bleed_time")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(5))
                                        .executes(context -> {
                                            int val = IntegerArgumentType.getInteger(context, "seconds");
                                            ConfigHelper.setAndSave(KnockdownConfig.BLEED_TIME_SECONDS, val, KnockdownConfig.SPEC);
                                            context.getSource().sendSuccess(() -> Component.translatable("knockdown.cmd.bleed_time_set", val).withStyle(ChatFormatting.GREEN), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("revive_time")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            int val = IntegerArgumentType.getInteger(context, "seconds");
                                            ConfigHelper.setAndSave(KnockdownConfig.REVIVE_TIME_SECONDS, val, KnockdownConfig.SPEC);
                                            context.getSource().sendSuccess(() -> Component.translatable("knockdown.cmd.revive_time_set", val).withStyle(ChatFormatting.GREEN), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("injector_time")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            int val = IntegerArgumentType.getInteger(context, "seconds");
                                            ConfigHelper.setAndSave(KnockdownConfig.INJECTOR_TIME_SECONDS, val, KnockdownConfig.SPEC);
                                            context.getSource().sendSuccess(() -> Component.translatable("knockdown.cmd.injector_time_set", val).withStyle(ChatFormatting.GREEN), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("cooldown_time")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            int val = IntegerArgumentType.getInteger(context, "seconds");
                                            ConfigHelper.setAndSave(KnockdownConfig.COOLDOWN_TIME_SECONDS, val, KnockdownConfig.SPEC);
                                            context.getSource().sendSuccess(() -> Component.translatable("knockdown.cmd.cooldown_time_set", val).withStyle(ChatFormatting.GREEN), true);
                                            return 1;
                                        })))
                )
        );
    }
}
