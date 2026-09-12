package com.persiki84.minimap.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.persiki84.battlecraft.BattleCraftCommands;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import com.persiki84.minimap.server.MapScanner;
import com.persiki84.minimap.server.ServerMapStorage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MapCommand {
    private MapCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("map")
                .requires(source -> source.hasPermission(2))
                .executes(context -> BattleCraftCommands.openMenu(context, ModuleMenuStates.MAP))
                .then(loadBranch())
                .then(Commands.literal("cancel").executes(MapCommand::cancel))
                .then(resetBranch());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> resetBranch() {
        return Commands.literal("reset")
                .then(Commands.literal("shared").executes(MapCommand::resetShared))
                .then(Commands.literal("teams").executes(MapCommand::resetTeams));
    }

    private static int resetShared(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int chunks = ServerMapStorage.resetShared(ServerMapStorage.dimensionKey(player.serverLevel()));

        context.getSource().sendSuccess(() -> Component.translatable("minimap.reset.shared", chunks), true);
        return 1;
    }

    private static int resetTeams(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int teams = ServerMapStorage.resetTeams(ServerMapStorage.dimensionKey(player.serverLevel()));

        context.getSource().sendSuccess(() -> Component.translatable("minimap.reset.teams", teams), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> loadBranch() {
        return Commands.literal("load")
                .then(Commands.argument("radius", IntegerArgumentType
                                .integer(MapScanner.MIN_RADIUS, MapScanner.MAX_RADIUS))
                        .executes(MapCommand::load));
    }

    private static int load(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!ModuleSwitches.allows(ModuleId.MINIMAP)) {
            context.getSource().sendFailure(Component.translatable("minimap.scan.disabled"));
            return 0;
        }

        MapScanner.start(player, IntegerArgumentType.getInteger(context, "radius"));
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!MapScanner.cancel(player)) {
            context.getSource().sendFailure(Component.translatable("minimap.scan.none"));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable("minimap.scan.cancelled"), false);
        return 1;
    }
}
