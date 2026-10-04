package com.persiki84.quarrymod.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.persiki84.quarrymod.QuarryMod;
import com.persiki84.quarrymod.data.QuarryBlockManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

// WHY: студия карьера правит блок, выбранный на карте, а не тот, на который смотрит оператор:
// WHY: позиция приходит числами и берётся только в мире источника команды, чанк не грузится
final class QuarryPositionCommands {
    private QuarryPositionCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("at")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.literal("remove").executes(QuarryPositionCommands::remove))
                        .then(Commands.literal("cooldown")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                                .executes(QuarryPositionCommands::setCooldown)))
                                .then(Commands.literal("reset").executes(QuarryPositionCommands::resetCooldown))));
    }

    private static QuarryBlockManager manager() {
        return QuarryMod.getInstance().getDataManager().getBlockManager();
    }

    private static boolean known(CommandSourceStack source, BlockPos pos) {
        if (manager().isQuarryBlock(pos, dimension(source))) return true;
        source.sendFailure(Component.translatable("quarrymod.command.block.not_quarry").withStyle(ChatFormatting.RED));
        return false;
    }

    private static String dimension(CommandSourceStack source) {
        return source.getLevel().dimension().location().toString();
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return removeAt(context.getSource(), BlockPosArgument.getBlockPos(context, "pos"));
    }

    private static int setCooldown(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setCooldownAt(context.getSource(), BlockPosArgument.getBlockPos(context, "pos"),
                IntegerArgumentType.getInteger(context, "seconds"));
    }

    private static int resetCooldown(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return resetCooldownAt(context.getSource(), BlockPosArgument.getBlockPos(context, "pos"));
    }

    static int removeAt(CommandSourceStack source, BlockPos pos) {
        if (!known(source, pos)) return 0;
        manager().removeQuarryBlock(pos, dimension(source));
        source.sendSuccess(() -> Component.translatable("quarrymod.command.block.removed",
                Component.literal(pos.toShortString()).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    static int setCooldownAt(CommandSourceStack source, BlockPos pos, int seconds) {
        if (!known(source, pos)) return 0;
        if (!manager().setCustomCooldown(pos, dimension(source), seconds)) {
            source.sendFailure(Component.translatable("quarrymod.command.cooldown.error_set").withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("quarrymod.command.cooldown.block_set",
                Component.literal(pos.toShortString()).withStyle(ChatFormatting.WHITE),
                Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GREEN),
                false);
        return 1;
    }

    static int resetCooldownAt(CommandSourceStack source, BlockPos pos) {
        if (!known(source, pos)) return 0;
        manager().removeCustomCooldown(pos, dimension(source));
        source.sendSuccess(() -> Component.translatable("quarrymod.command.cooldown.reset",
                Component.literal(pos.toShortString()).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
