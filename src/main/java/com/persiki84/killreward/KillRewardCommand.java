package com.persiki84.killreward;

import com.persiki84.battlecraft.BattleCraftCommands;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.Names;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.shared.CommandHelper;
import com.persiki84.shared.ConfigHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.stream.Collectors;

public class KillRewardCommand {

    private static final SuggestionProvider<CommandSourceStack> ITEM_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    ForgeRegistries.ITEMS.getKeys().stream()
                            .map(ResourceLocation::toString)
                            .collect(Collectors.toList()),
                    builder
            );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("killreward")
                .requires(source -> source.hasPermission(2))
                .executes(KillRewardCommand::openMenu)
                .then(toggleBranch())
                .then(switchBranch("enable", true))
                .then(switchBranch("disable", false))
                .then(itemBranch())
                .then(amountBranch())
                .then(teamKillsBranch())
                .then(lastBranch())
                .then(infoBranch()));

        dispatcher.register(Commands.literal("kr")
                .requires(source -> source.hasPermission(2))
                .executes(KillRewardCommand::openMenu)
                .redirect(dispatcher.getRoot().getChild("killreward")));
    }

    private static int openMenu(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return BattleCraftCommands.openMenu(context, ModuleMenuStates.KILL_REWARD);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> toggleBranch() {
        return Commands.literal("toggle").executes(context -> applyEnabled(context, !KillRewardMod.modEnabled));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> switchBranch(String name, boolean enabled) {
        return Commands.literal(name).executes(context -> applyEnabled(context, enabled));
    }

    private static int applyEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        KillRewardMod.modEnabled = enabled;
        ConfigHelper.setAndSave(Config.MOD_ENABLED, enabled, Config.SPEC);
        context.getSource().sendSuccess(() -> enabled
                ? Component.translatable("killreward.mod_enabled").withStyle(ChatFormatting.GREEN)
                : Component.translatable("killreward.mod_disabled").withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> itemBranch() {
        return Commands.literal("setitem")
                .then(Commands.argument("item", StringArgumentType.string())
                        .suggests(ITEM_SUGGESTIONS)
                        .executes(KillRewardCommand::applyItem));
    }

    private static int applyItem(CommandContext<CommandSourceStack> context) {
        String item = StringArgumentType.getString(context, "item");
        net.minecraft.resources.ResourceLocation parsed = net.minecraft.resources.ResourceLocation.tryParse(item);
        if (parsed == null || !net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(parsed)) {
            context.getSource().sendFailure(Component.translatable("killreward.unknown_item", item));
            return 0;
        }

        KillRewardMod.rewardItem = item;
        ConfigHelper.setAndSave(Config.REWARD_ITEM, item, Config.SPEC);
        context.getSource().sendSuccess(() -> Component.translatable("killreward.reward_item_set",
                Names.item(item)).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> amountBranch() {
        return Commands.literal("setamount")
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                        .executes(KillRewardCommand::applyAmount));
    }

    private static int applyAmount(CommandContext<CommandSourceStack> context) {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        KillRewardMod.rewardAmount = amount;
        ConfigHelper.setAndSave(Config.REWARD_AMOUNT, amount, Config.SPEC);
        context.getSource().sendSuccess(() ->
                Component.translatable("killreward.reward_amount_set", amount).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> teamKillsBranch() {
        return Commands.literal("teamkills")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(KillRewardCommand::applyTeamKills));
    }

    private static int applyTeamKills(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        KillRewardMod.rewardTeamKills = enabled;
        ConfigHelper.setAndSave(Config.REWARD_TEAM_KILLS, enabled, Config.SPEC);
        context.getSource().sendSuccess(() ->
                Component.translatable("killreward.teamkills_set", state(enabled)).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static Component state(boolean enabled) {
        return enabled
                ? Component.translatable("killreward.enabled").withStyle(ChatFormatting.GREEN)
                : Component.translatable("killreward.disabled").withStyle(ChatFormatting.RED);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> lastBranch() {
        return Commands.literal("last").executes(KillRewardCommand::showLast);
    }

    private static int showLast(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandHelper.requirePlayer(context, "killreward");
        if (player == null) return 0;

        String last = KillRewardMod.lastRewards.get(player.getUUID());
        if (last == null) {
            context.getSource().sendFailure(
                    Component.translatable("killreward.no_rewards").withStyle(ChatFormatting.RED));
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.translatable("killreward.last_reward",
                Component.literal(last).withStyle(ChatFormatting.GREEN)).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> infoBranch() {
        return Commands.literal("info").executes(KillRewardCommand::showInfo);
    }

    private static int showInfo(CommandContext<CommandSourceStack> context) {
        say(context, Component.translatable("killreward.info_header").withStyle(ChatFormatting.GOLD));
        say(context, Component.translatable("killreward.info_status").withStyle(ChatFormatting.YELLOW)
                .append(state(KillRewardMod.modEnabled)));
        say(context, Component.translatable("killreward.info_item").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(KillRewardMod.rewardItem).withStyle(ChatFormatting.WHITE)));
        say(context, Component.translatable("killreward.info_amount").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(String.valueOf(KillRewardMod.rewardAmount)).withStyle(ChatFormatting.WHITE)));
        say(context, Component.translatable("killreward.info_teamkills").withStyle(ChatFormatting.YELLOW)
                .append(yesNo(KillRewardMod.rewardTeamKills)));
        return 1;
    }

    private static Component yesNo(boolean value) {
        return value
                ? Component.translatable("killreward.yes").withStyle(ChatFormatting.GREEN)
                : Component.translatable("killreward.no").withStyle(ChatFormatting.RED);
    }

    private static void say(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendSuccess(() -> message, false);
    }
}
