package com.persiki84.capturepoints.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ResetCooldownCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("resetcapturecooldown")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(CapturePointCommand.POINT_SUGGESTIONS)
                        .executes(ResetCooldownCommand::resetCooldown))
        );
    }

    private static int resetCooldown(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CapturePoint point = CapturePointManager.getCapturePoint(name);

        if (point == null) {
            context.getSource().sendFailure(
                    Component.translatable("capturepoints.error.capture_point_not_found").withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        point.resetCooldown();
        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.success.cooldown_reset", name).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
