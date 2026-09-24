package com.persiki84.knockdown.command;

import com.persiki84.battlecraft.BattleCraftCommands;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.knockdown.config.KnockdownConfig;
import com.persiki84.shared.ConfigHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

public class KnockdownCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("knockdown")
                .requires(source -> source.hasPermission(2))
                .executes(context -> BattleCraftCommands.openMenu(context, ModuleMenuStates.KNOCKDOWN))
                .then(Commands.literal("config")
                        .then(setting("bleed_time", 5, 600, KnockdownConfig.BLEED_TIME_SECONDS,
                                "knockdown.cmd.bleed_time_set"))
                        .then(setting("revive_time", 1, 60, KnockdownConfig.REVIVE_TIME_SECONDS,
                                "knockdown.cmd.revive_time_set"))
                        .then(setting("injector_time", 1, 60, KnockdownConfig.INJECTOR_TIME_SECONDS,
                                "knockdown.cmd.injector_time_set"))
                        .then(setting("cooldown_time", 0, 3600, KnockdownConfig.COOLDOWN_TIME_SECONDS,
                                "knockdown.cmd.cooldown_time_set"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> setting(String name, int min, int max,
                                                                      ForgeConfigSpec.IntValue value, String done) {
        return Commands.literal(name)
                .then(Commands.argument("seconds", IntegerArgumentType.integer(min, max))
                        .executes(context -> {
                            int seconds = IntegerArgumentType.getInteger(context, "seconds");
                            ConfigHelper.setAndSave(value, seconds, KnockdownConfig.SPEC);
                            context.getSource().sendSuccess(() -> Component.translatable(done, seconds)
                                    .withStyle(ChatFormatting.GREEN), true);
                            return 1;
                        }));
    }
}
