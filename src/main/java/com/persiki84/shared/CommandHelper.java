package com.persiki84.shared;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class CommandHelper {

    private CommandHelper() {}

    public static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> ctx, String modId) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(ModMessage.error(modId, "error.player_only"));
        }
        return player;
    }

    public static ItemStack requireHeldItem(ServerPlayer player, String modId) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            player.sendSystemMessage(ModMessage.error(modId, "error.no_held_item"));
        }
        return stack;
    }
}
