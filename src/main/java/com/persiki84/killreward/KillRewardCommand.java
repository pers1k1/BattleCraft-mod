package com.persiki84.killreward;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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

                .then(Commands.literal("toggle")
                        .executes(context -> {
                            KillRewardMod.modEnabled = !KillRewardMod.modEnabled;
                            ConfigHelper.setAndSave(Config.MOD_ENABLED, KillRewardMod.modEnabled, Config.SPEC);
                            context.getSource().sendSuccess(() ->
                                    KillRewardMod.modEnabled
                                            ? Component.translatable("killreward.mod_enabled").withStyle(ChatFormatting.GREEN)
                                            : Component.translatable("killreward.mod_disabled").withStyle(ChatFormatting.RED),
                                    true);
                            return 1;
                        })
                )

                .then(Commands.literal("enable")
                        .executes(context -> {
                            KillRewardMod.modEnabled = true;
                            ConfigHelper.setAndSave(Config.MOD_ENABLED, true, Config.SPEC);
                            context.getSource().sendSuccess(() ->
                                    Component.translatable("killreward.mod_enabled").withStyle(ChatFormatting.GREEN),
                                    true);
                            return 1;
                        })
                )

                .then(Commands.literal("disable")
                        .executes(context -> {
                            KillRewardMod.modEnabled = false;
                            ConfigHelper.setAndSave(Config.MOD_ENABLED, false, Config.SPEC);
                            context.getSource().sendSuccess(() ->
                                    Component.translatable("killreward.mod_disabled").withStyle(ChatFormatting.RED),
                                    true);
                            return 1;
                        })
                )

                .then(Commands.literal("setitem")
                        .then(Commands.argument("item", StringArgumentType.string())
                                .suggests(ITEM_SUGGESTIONS)
                                .executes(context -> {
                                    String item = StringArgumentType.getString(context, "item");
                                    KillRewardMod.rewardItem = item;
                                    ConfigHelper.setAndSave(Config.REWARD_ITEM, item, Config.SPEC);
                                    context.getSource().sendSuccess(() ->
                                            Component.translatable("killreward.reward_item_set", item)
                                                    .withStyle(ChatFormatting.GREEN),
                                            true);
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("setamount")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                .executes(context -> {
                                    int amount = IntegerArgumentType.getInteger(context, "amount");
                                    KillRewardMod.rewardAmount = amount;
                                    ConfigHelper.setAndSave(Config.REWARD_AMOUNT, amount, Config.SPEC);
                                    context.getSource().sendSuccess(() ->
                                            Component.translatable("killreward.reward_amount_set", amount)
                                                    .withStyle(ChatFormatting.GREEN),
                                            true);
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("teamkills")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                    KillRewardMod.rewardTeamKills = enabled;
                                    ConfigHelper.setAndSave(Config.REWARD_TEAM_KILLS, enabled, Config.SPEC);
                                    context.getSource().sendSuccess(() ->
                                            Component.translatable("killreward.teamkills_set",
                                                    enabled
                                                            ? Component.translatable("killreward.enabled").withStyle(ChatFormatting.GREEN)
                                                            : Component.translatable("killreward.disabled").withStyle(ChatFormatting.RED)
                                            ).withStyle(ChatFormatting.GREEN),
                                            true);
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("last")
                        .executes(context -> {
                            ServerPlayer player = CommandHelper.requirePlayer(context, "killreward");
                            if (player == null) return 0;
                            String last = KillRewardMod.lastRewards.get(player.getUUID());
                            if (last != null) {
                                context.getSource().sendSuccess(() ->
                                        Component.translatable("killreward.last_reward", Component.literal(last).withStyle(ChatFormatting.GREEN))
                                                .withStyle(ChatFormatting.YELLOW),
                                        false);
                            } else {
                                context.getSource().sendFailure(
                                        Component.translatable("killreward.no_rewards").withStyle(ChatFormatting.RED));
                            }
                            return 1;
                        })
                )

                .then(Commands.literal("info")
                        .executes(context -> {
                            context.getSource().sendSuccess(() ->
                                    Component.translatable("killreward.info_header").withStyle(ChatFormatting.GOLD), false);
                            context.getSource().sendSuccess(() ->
                                    Component.translatable("killreward.info_status")
                                            .withStyle(ChatFormatting.YELLOW)
                                            .append(KillRewardMod.modEnabled
                                                    ? Component.translatable("killreward.enabled").withStyle(ChatFormatting.GREEN)
                                                    : Component.translatable("killreward.disabled").withStyle(ChatFormatting.RED)), false);
                            context.getSource().sendSuccess(() ->
                                    Component.translatable("killreward.info_item")
                                            .withStyle(ChatFormatting.YELLOW)
                                            .append(Component.literal(KillRewardMod.rewardItem).withStyle(ChatFormatting.WHITE)), false);
                            context.getSource().sendSuccess(() ->
                                    Component.translatable("killreward.info_amount")
                                            .withStyle(ChatFormatting.YELLOW)
                                            .append(Component.literal(String.valueOf(KillRewardMod.rewardAmount)).withStyle(ChatFormatting.WHITE)), false);
                            context.getSource().sendSuccess(() ->
                                    Component.translatable("killreward.info_teamkills")
                                            .withStyle(ChatFormatting.YELLOW)
                                            .append(KillRewardMod.rewardTeamKills
                                                    ? Component.translatable("killreward.yes").withStyle(ChatFormatting.GREEN)
                                                    : Component.translatable("killreward.no").withStyle(ChatFormatting.RED)), false);
                            return 1;
                        })
                )
        );

        dispatcher.register(Commands.literal("kr")
                .requires(source -> source.hasPermission(2))
                .redirect(dispatcher.getRoot().getChild("killreward"))
        );
    }
}
