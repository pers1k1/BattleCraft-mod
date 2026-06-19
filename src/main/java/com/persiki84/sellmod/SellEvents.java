package com.persiki84.sellmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

public class SellEvents {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("sell")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    SellManager.SellResult result = SellManager.sellAllItems(player);
                    Component message = SellManager.createSellMessage(result);
                    context.getSource().sendSuccess(() -> message, false);
                    return 1;
                })
                .then(Commands.literal("prices")
                        .executes(context -> {
                            Component priceList = SellManager.getPriceList();
                            context.getSource().sendSuccess(() -> priceList, false);
                            return 1;
                        }))
                .then(Commands.literal("help")
                        .executes(context -> {
                            MutableComponent helpMessage = Component.translatable("sellmod.help.header").withStyle(ChatFormatting.GOLD)
                                    .append(Component.translatable("sellmod.help.sell").withStyle(ChatFormatting.WHITE))
                                    .append(Component.translatable("sellmod.help.prices").withStyle(ChatFormatting.WHITE))
                                    .append(Component.translatable("sellmod.help.help").withStyle(ChatFormatting.WHITE));
                            if (context.getSource().hasPermission(2)) {
                                helpMessage.append(Component.translatable("sellmod.help.setcurrency").withStyle(ChatFormatting.WHITE))
                                        .append(Component.translatable("sellmod.help.priceset").withStyle(ChatFormatting.WHITE))
                                        .append(Component.translatable("sellmod.help.priceremove").withStyle(ChatFormatting.WHITE));
                            }
                            MutableComponent finalHelpMessage = helpMessage;
                            context.getSource().sendSuccess(() -> finalHelpMessage, false);
                            return 1;
                        }))
                .then(Commands.literal("setcurrency")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                .executes(context -> {
                                    Item item = ItemArgument.getItem(context, "item").getItem();
                                    net.minecraft.resources.ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
                                    if (rl != null) {
                                        boolean success = SellManager.setCurrency(rl.toString());
                                        if (success) {
                                            context.getSource().sendSuccess(() -> Component.translatable("sellmod.cmd.currency_set", rl.toString()).withStyle(ChatFormatting.GREEN), true);
                                            return 1;
                                        }
                                    }
                                    context.getSource().sendFailure(Component.translatable("sellmod.cmd.currency_failed").withStyle(ChatFormatting.RED));
                                    return 0;
                                })))
                .then(Commands.literal("price")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                        .then(Commands.argument("price", IntegerArgumentType.integer(0))
                                                .executes(context -> {
                                                    Item item = ItemArgument.getItem(context, "item").getItem();
                                                    net.minecraft.resources.ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
                                                    int price = IntegerArgumentType.getInteger(context, "price");
                                                    if (rl != null) {
                                                        SellManager.setPrice(rl.toString(), price);
                                                        context.getSource().sendSuccess(() -> Component.translatable("sellmod.cmd.price_set", rl.toString(), price).withStyle(ChatFormatting.GREEN), true);
                                                        return 1;
                                                    }
                                                    context.getSource().sendFailure(Component.translatable("sellmod.cmd.price_failed").withStyle(ChatFormatting.RED));
                                                    return 0;
                                                }))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                SellManager.getSellPrices().keySet(),
                                                builder
                                        ))
                                        .executes(context -> {
                                            Item item = ItemArgument.getItem(context, "item").getItem();
                                            net.minecraft.resources.ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
                                            if (rl != null) {
                                                SellManager.setPrice(rl.toString(), 0);
                                                context.getSource().sendSuccess(() -> Component.translatable("sellmod.cmd.price_removed", rl.toString()).withStyle(ChatFormatting.GREEN), true);
                                                return 1;
                                            }
                                            context.getSource().sendFailure(Component.translatable("sellmod.cmd.price_remove_failed").withStyle(ChatFormatting.RED));
                                            return 0;
                                        })))));
    }
}
