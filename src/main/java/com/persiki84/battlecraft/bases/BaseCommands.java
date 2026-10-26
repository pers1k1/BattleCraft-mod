package com.persiki84.battlecraft.bases;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.zones.ZoneLookup;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

// WHY: команды для тех, кто набирает руками; экран смерти и окно перелёта шлют пакет, а условия у
// WHY: обоих путей одни и живут на сервере
public final class BaseCommands {
    private static final String BASE = "base";
    private static final SuggestionProvider<CommandSourceStack> OWN_BASES = (context, builder) ->
            SharedSuggestionProvider.suggest(ownIds(context.getSource()), builder);

    private BaseCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> spawnAt() {
        return Commands.literal("spawnat").then(Commands.argument(BASE, StringArgumentType.greedyString())
                .suggests(OWN_BASES).executes(BaseCommands::chooseSpawn));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> travel() {
        return Commands.literal("travel").then(Commands.argument(BASE, StringArgumentType.greedyString())
                .suggests(OWN_BASES).executes(BaseCommands::startTravel));
    }

    private static List<String> ownIds(CommandSourceStack source) {
        List<String> ids = new ArrayList<>();
        if (!(source.getEntity() instanceof ServerPlayer player)) return ids;
        for (BaseSpot spot : BaseNetwork.of(ZoneLookup.teamNameOf(player))) {
            ids.add(spot.id());
        }
        return ids;
    }

    private static int chooseSpawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Component refusal = RespawnChoices.choose(player, StringArgumentType.getString(context, BASE));
        if (refusal == null) return 1;
        context.getSource().sendFailure(refusal);
        return 0;
    }

    private static int startTravel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Component refusal = BaseTravel.start(player, StringArgumentType.getString(context, BASE));
        if (refusal == null) return 1;
        context.getSource().sendFailure(refusal);
        return 0;
    }
}
