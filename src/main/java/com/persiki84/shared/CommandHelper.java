package com.persiki84.shared;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class CommandHelper {

    private CommandHelper() {}

    public static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> ctx, String modId) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(ModMessage.error(modId, "error.player_only"));
        }
        return player;
    }

}
