package com.persiki84.combattimer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class CombatCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kt")
                .requires(source -> source.hasPermission(2))

                .then(Commands.literal("settime")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 300))
                                .executes(context -> {
                                    int time = IntegerArgumentType.getInteger(context, "seconds");
                                    CombatTimerMod.combatDuration = time;
                                    context.getSource().sendSuccess(() -> Component.translatable(
                                            "combattimer.command.settime",
                                            Component.literal(String.valueOf(time)).withStyle(ChatFormatting.YELLOW)
                                    ).withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("killlogout")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                    CombatTimerMod.killOnLogout = enabled;
                                    context.getSource().sendSuccess(() -> Component.translatable(
                                            "combattimer.command.killlogout",
                                            enabled
                                                    ? Component.translatable("combattimer.command.killlogout.enabled").withStyle(ChatFormatting.RED)
                                                    : Component.translatable("combattimer.command.killlogout.disabled").withStyle(ChatFormatting.DARK_GREEN)
                                    ).withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("info")
                        .executes(context -> {
                            Component info = Component.translatable("combattimer.command.info.header").withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal("\n"))
                                    .append(Component.translatable("combattimer.command.info.duration",
                                            Component.literal(String.valueOf(CombatTimerMod.combatDuration)).withStyle(ChatFormatting.WHITE)
                                    ).withStyle(ChatFormatting.YELLOW))
                                    .append(Component.literal("\n"))
                                    .append(Component.translatable("combattimer.command.info.kill",
                                            CombatTimerMod.killOnLogout
                                                    ? Component.translatable("combattimer.command.info.kill.enabled").withStyle(ChatFormatting.RED)
                                                    : Component.translatable("combattimer.command.info.kill.disabled").withStyle(ChatFormatting.GREEN)
                                    ).withStyle(ChatFormatting.YELLOW));
                            context.getSource().sendSuccess(() -> info, false);
                            return 1;
                        })
                )
        );
    }
}
