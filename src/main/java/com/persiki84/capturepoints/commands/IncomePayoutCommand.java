package com.persiki84.capturepoints.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.capture.IncomePayout;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

// WHY: способ выплаты дохода общий для всех точек: каждому целиком, поровну или в казну команды
final class IncomePayoutCommand {
    private IncomePayoutCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> branch() {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("incomepayout");
        for (IncomePayout payout : IncomePayout.values()) {
            node.then(Commands.literal(payout.id()).executes(context -> set(context, payout)));
        }
        return node;
    }

    private static int set(CommandContext<CommandSourceStack> context, IncomePayout payout) {
        CapturePointManager.incomePayout(payout);
        context.getSource().sendSuccess(() -> Component.translatable("capturepoints.success.payout",
                Component.translatable(payout.translationKey())).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
