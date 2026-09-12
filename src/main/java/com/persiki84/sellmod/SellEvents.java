package com.persiki84.sellmod;

import com.persiki84.battlecraft.BattleCraftCommands;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import com.persiki84.sellmod.network.PacketHandler;
import com.persiki84.shared.Names;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

public class SellEvents {

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketHandler.syncTo(player);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sell")
                .executes(context -> BattleCraftCommands.openMenu(context, ModuleMenuStates.SELL))
                .then(pricesNode())
                .then(helpNode())
                .then(currencyNode(event.getBuildContext()))
                .then(priceNode(event.getBuildContext())));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> pricesNode() {
        return Commands.literal("prices").executes(context -> {
            Component priceList = SellManager.getPriceList();
            context.getSource().sendSuccess(() -> priceList, false);
            return 1;
        });
    }

    private static ArgumentBuilder<CommandSourceStack, ?> helpNode() {
        return Commands.literal("help").executes(context -> {
            MutableComponent message = Component.translatable("sellmod.help.header").withStyle(ChatFormatting.GOLD)
                    .append(Component.translatable("sellmod.help.prices").withStyle(ChatFormatting.WHITE))
                    .append(Component.translatable("sellmod.help.help").withStyle(ChatFormatting.WHITE));
            if (context.getSource().hasPermission(2)) {
                message.append(Component.translatable("sellmod.help.setcurrency").withStyle(ChatFormatting.WHITE))
                        .append(Component.translatable("sellmod.help.priceset").withStyle(ChatFormatting.WHITE))
                        .append(Component.translatable("sellmod.help.priceremove").withStyle(ChatFormatting.WHITE));
            }
            MutableComponent shown = message;
            context.getSource().sendSuccess(() -> shown, false);
            return 1;
        });
    }

    private static ArgumentBuilder<CommandSourceStack, ?> currencyNode(CommandBuildContext build) {
        return Commands.literal("setcurrency")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("item", ItemArgument.item(build)).executes(context -> {
                    ResourceLocation id = itemId(ItemArgument.getItem(context, "item").getItem());
                    if (id == null || !SellManager.setCurrency(id.toString())) {
                        context.getSource().sendFailure(
                                Component.translatable("sellmod.cmd.currency_failed").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    PacketHandler.syncToAll(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.translatable("sellmod.cmd.currency_set",
                            Names.item(id.toString())).withStyle(ChatFormatting.GREEN), true);
                    return 1;
                }));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> priceNode(CommandBuildContext build) {
        return Commands.literal("price")
                .requires(source -> source.hasPermission(2))
                .then(priceSetNode(build))
                .then(priceRemoveNode(build));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> priceSetNode(CommandBuildContext build) {
        return Commands.literal("set").then(Commands.argument("item", ItemArgument.item(build))
                .then(Commands.argument("price", IntegerArgumentType.integer(0)).executes(context -> {
                    ResourceLocation id = itemId(ItemArgument.getItem(context, "item").getItem());
                    int price = IntegerArgumentType.getInteger(context, "price");
                    if (id == null) {
                        context.getSource().sendFailure(
                                Component.translatable("sellmod.cmd.price_failed").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    SellManager.setPrice(id.toString(), price);
                    PacketHandler.syncToAll(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.translatable("sellmod.cmd.price_set",
                            Names.item(id.toString()), price).withStyle(ChatFormatting.GREEN), true);
                    return 1;
                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> priceRemoveNode(CommandBuildContext build) {
        return Commands.literal("remove").then(Commands.argument("item", ItemArgument.item(build))
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        SellManager.getSellPrices().keySet(), builder))
                .executes(context -> {
                    ResourceLocation id = itemId(ItemArgument.getItem(context, "item").getItem());
                    if (id == null) {
                        context.getSource().sendFailure(
                                Component.translatable("sellmod.cmd.price_remove_failed").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    SellManager.setPrice(id.toString(), 0);
                    PacketHandler.syncToAll(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.translatable("sellmod.cmd.price_removed",
                            Names.item(id.toString())).withStyle(ChatFormatting.GREEN), true);
                    return 1;
                }));
    }

    private static ResourceLocation itemId(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }
}
