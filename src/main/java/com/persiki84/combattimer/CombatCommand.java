package com.persiki84.combattimer;

import com.persiki84.battlecraft.BattleCraftCommands;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.ConfigHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class CombatCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kt")
                .requires(source -> source.hasPermission(2))
                .executes(context -> BattleCraftCommands.openMenu(context, ModuleMenuStates.COMBAT))
                .then(timeNode())
                .then(logoutNode())
                .then(Commands.literal("info").executes(CombatCommand::info)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> timeNode() {
        return Commands.literal("settime")
                .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 300))
                        .executes(context -> {
                            int time = IntegerArgumentType.getInteger(context, "seconds");
                            ConfigHelper.setAndSave(CombatTimerConfig.DURATION, time, CombatTimerConfig.SPEC);
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "combattimer.command.settime",
                                    Component.literal(String.valueOf(time)).withStyle(ChatFormatting.YELLOW)
                            ).withStyle(ChatFormatting.GREEN), true);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> logoutNode() {
        return Commands.literal("killlogout")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> {
                            boolean enabled = BoolArgumentType.getBool(context, "enabled");
                            ConfigHelper.setAndSave(CombatTimerConfig.KILL_ON_LOGOUT, enabled, CombatTimerConfig.SPEC);
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "combattimer.command.killlogout",
                                    enabled
                                            ? Component.translatable("combattimer.command.killlogout.enabled").withStyle(ChatFormatting.RED)
                                            : Component.translatable("combattimer.command.killlogout.disabled").withStyle(ChatFormatting.DARK_GREEN)
                            ).withStyle(ChatFormatting.GREEN), true);
                            return 1;
                        }));
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        Component info = Component.translatable("combattimer.command.info.header").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("\n"))
                .append(Component.translatable("combattimer.command.info.duration",
                        Component.literal(String.valueOf(CombatTimerMod.combatDuration())).withStyle(ChatFormatting.WHITE)
                ).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"))
                .append(Component.translatable("combattimer.command.info.kill",
                        CombatTimerMod.killOnLogout()
                                ? Component.translatable("combattimer.command.info.kill.enabled").withStyle(ChatFormatting.RED)
                                : Component.translatable("combattimer.command.info.kill.disabled").withStyle(ChatFormatting.GREEN)
                ).withStyle(ChatFormatting.YELLOW));
        context.getSource().sendSuccess(() -> info, false);
        return 1;
    }
}
