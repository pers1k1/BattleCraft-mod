package com.persiki84.battlecraft.craft;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.shared.menu.MenuNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class CraftCommand {
    private static final String ITEM = "item";
    private static final int LISTED = 30;

    private static final SuggestionProvider<CommandSourceStack> BANNED = (context, builder) ->
            SharedSuggestionProvider.suggest(bannedSpecs(), builder);

    private CraftCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext build) {
        return Commands.literal("craft")
                .requires(source -> source.hasPermission(2))
                .executes(CraftCommand::openMenu)
                .then(Commands.literal("ban").then(withPhases(Commands.argument(ITEM, ItemArgument.item(build))
                        .executes(context -> ban(context, CraftPhase.ALWAYS)), CraftCommand::ban)))
                .then(Commands.literal("unban").then(Commands.argument(ITEM, ItemArgument.item(build))
                        .suggests(BANNED).executes(CraftCommand::unban)))
                .then(Commands.literal("phase").then(withPhases(Commands.argument(ITEM, ItemArgument.item(build))
                        .suggests(BANNED), CraftCommand::rephase)))
                .then(Commands.literal("list").executes(CraftCommand::list))
                .then(withPhases(Commands.literal("clear").executes(context -> clear(context, null)),
                        CraftCommand::clear));
    }

    private interface PhaseAction {
        int run(CommandContext<CommandSourceStack> context, CraftPhase phase) throws CommandSyntaxException;
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withPhases(T node, PhaseAction action) {
        for (CraftPhase phase : CraftPhase.values()) {
            node.then(Commands.literal(phase.id()).executes(context -> action.run(context, phase)));
        }
        return node;
    }

    private static List<String> bannedSpecs() {
        List<String> specs = new ArrayList<>();
        for (CraftBan ban : CraftBanBook.all()) {
            specs.add(ban.key());
        }
        return specs;
    }

    private static int openMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MenuNetwork.open(context.getSource().getPlayerOrException(), CraftMenuState.MENU_ID);
        return 1;
    }

    private static ItemStack typed(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return ItemArgument.getItem(context, ITEM).createItemStack(1, false);
    }

    private static int ban(CommandContext<CommandSourceStack> context, CraftPhase phase) throws CommandSyntaxException {
        ItemStack stack = typed(context);
        CraftBan ban = new CraftBan(stack.getItem(), CraftBanIdentity.typed(stack), phase);
        CraftBanBook.Outcome outcome = CraftBanBook.ban(ban);
        String key = switch (outcome) {
            case ADDED -> "battlecraft.craft.banned";
            case CHANGED -> "battlecraft.craft.rephased";
            case SAME -> "battlecraft.craft.already";
        };
        Component name = stack.getDisplayName();
        Component phaseName = Component.translatable(phase.label());
        context.getSource().sendSuccess(() -> Component.translatable(key, name, phaseName)
                .withStyle(outcome == CraftBanBook.Outcome.SAME ? ChatFormatting.GRAY : ChatFormatting.YELLOW), true);
        return outcome == CraftBanBook.Outcome.SAME ? 0 : 1;
    }

    private static int unban(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ItemStack stack = typed(context);
        int removed = CraftBanBook.unban(stack.getItem(), CraftBanIdentity.typed(stack));
        return report(context, removed, stack, count -> Component.translatable("battlecraft.craft.unbanned",
                stack.getDisplayName(), count));
    }

    private static int rephase(CommandContext<CommandSourceStack> context, CraftPhase phase)
            throws CommandSyntaxException {
        ItemStack stack = typed(context);
        int moved = CraftBanBook.rephase(stack.getItem(), CraftBanIdentity.typed(stack), phase);
        return report(context, moved, stack, count -> Component.translatable("battlecraft.craft.moved",
                stack.getDisplayName(), Component.translatable(phase.label()), count));
    }

    private static int report(CommandContext<CommandSourceStack> context, int count, ItemStack stack,
                              Function<Integer, Component> done) {
        if (count == 0) {
            context.getSource().sendFailure(Component.translatable("battlecraft.craft.not_banned",
                    stack.getDisplayName()));
            return 0;
        }
        context.getSource().sendSuccess(() -> done.apply(count).copy().withStyle(ChatFormatting.YELLOW), true);
        return count;
    }

    private static int clear(CommandContext<CommandSourceStack> context, CraftPhase phase) {
        int removed = CraftBanBook.clear(phase);
        Component scope = phase == null ? Component.translatable("battlecraft.craft.scope.all")
                : Component.translatable(phase.label());
        context.getSource().sendSuccess(() -> Component.translatable("battlecraft.craft.cleared", removed, scope)
                .withStyle(ChatFormatting.YELLOW), true);
        return removed;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        List<CraftBan> bans = CraftBanBook.all();
        if (bans.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("battlecraft.craft.list.empty")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("battlecraft.craft.list.header", bans.size(),
                CraftBanBook.count(CraftPhase.ALWAYS), CraftBanBook.count(CraftPhase.MATCH),
                CraftBanBook.count(CraftPhase.LOBBY)).withStyle(ChatFormatting.GOLD), false);
        for (int index = 0; index < Math.min(bans.size(), LISTED); index++) {
            CraftBan ban = bans.get(index);
            context.getSource().sendSuccess(() -> Component.translatable("battlecraft.craft.list.row",
                    ban.stack().getDisplayName(), ban.key(), Component.translatable(ban.phase().label()))
                    .withStyle(ChatFormatting.GRAY), false);
        }
        if (bans.size() > LISTED) {
            context.getSource().sendSuccess(() -> Component.translatable("battlecraft.craft.list.tail",
                    bans.size() - LISTED).withStyle(ChatFormatting.GRAY), false);
        }
        return bans.size();
    }
}
