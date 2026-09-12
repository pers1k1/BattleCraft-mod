package com.persiki84.capturepoints.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.persiki84.capturepoints.event.BlockProtectionHandler;
import com.persiki84.capturepoints.network.LocalMarkerOverridePacket;
import com.persiki84.capturepoints.network.PacketHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class PointCommands {

    private PointCommands() {}

    public static int protectionStatus(CommandContext<CommandSourceStack> context) {
        boolean enabled = BlockProtectionHandler.isProtectionEnabled();
        Component statusText = enabled
                ? Component.translatable("capturepoints.status.enabled").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                : Component.translatable("capturepoints.status.disabled").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        context.getSource().sendSuccess(() ->
                Component.translatable("capturepoints.info.protection_status", statusText).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    public static int setLocalMarkers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        ServerPlayer player = context.getSource().getPlayerOrException();
        PacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new LocalMarkerOverridePacket(enabled));
        context.getSource().sendSuccess(() ->
                Component.literal("Local markers visibility set to " + enabled).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
