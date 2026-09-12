package com.persiki84.airdrop.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.airdrop.loot.AirDropLootManager;
import com.persiki84.airdrop.loot.AirDropLootManager.LootEntry;
import com.persiki84.shared.gunsmith.GunSlot;
import com.persiki84.shared.gunsmith.GunSmith;
import com.persiki84.shared.gunsmith.Outcome;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AirDropLootCommands {

    private static final SuggestionProvider<CommandSourceStack> ATTACHMENTS = (context, builder) ->
            SharedSuggestionProvider.suggest(attachmentOptions(context), builder);

    private static final SuggestionProvider<CommandSourceStack> SLOTS = (context, builder) ->
            SharedSuggestionProvider.suggest(slotNames(context), builder);

    private final ResourceLocation table;

    private AirDropLootCommands(ResourceLocation table) {
        this.table = table;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(ResourceLocation table, CommandBuildContext context) {
        AirDropLootCommands commands = new AirDropLootCommands(table);
        return Commands.literal("loot")
                .then(Commands.literal("list").executes(commands::list))
                .then(commands.addBranch(context))
                .then(commands.handBranch())
                .then(commands.attachBranch())
                .then(commands.detachBranch())
                .then(commands.removeBranch())
                .then(commands.editBranch())
                .then(commands.orderBranch())
                .then(commands.duplicateBranch())
                .then(Commands.literal("clear").executes(commands::clear));
    }

    private LiteralArgumentBuilder<CommandSourceStack> addBranch(CommandBuildContext context) {
        return Commands.literal("add")
                .then(Commands.argument("item", ItemArgument.item(context))
                        .then(countNode(this::addFromArgument)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> handBranch() {
        return Commands.literal("hand").then(countNode(this::addFromHand));
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> countNode(
            com.mojang.brigadier.Command<CommandSourceStack> action) {
        return Commands.argument("min", IntegerArgumentType.integer(1))
                .then(Commands.argument("max", IntegerArgumentType.integer(1))
                        .then(Commands.argument("percent", IntegerArgumentType.integer(1, 100))
                                .executes(action)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> attachBranch() {
        return Commands.literal("attach")
                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                        .then(Commands.argument("attachment", StringArgumentType.string())
                                .suggests(ATTACHMENTS)
                                .executes(this::attach)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> detachBranch() {
        return Commands.literal("detach")
                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                        .then(Commands.argument("slot", StringArgumentType.word())
                                .suggests(SLOTS)
                                .executes(this::detach)));
    }

    private int detach(CommandContext<CommandSourceStack> context) {
        List<LootEntry> entries = entries();
        int index = IntegerArgumentType.getInteger(context, "index");
        if (!inRange(entries, index)) return fail(context, "airdrop.loot.invalid_index");

        LootEntry entry = entries.get(index);
        ItemStack stack = AirDropLootManager.stackOf(entry, 1);
        String slot = StringArgumentType.getString(context, "slot");

        Outcome outcome = GunSmith.detach(stack, slot);
        if (outcome != Outcome.DONE) return fail(context, refusal(outcome), GunSmith.slotName(slot));

        entries.set(index, AirDropLootManager.describing(entry, stack));
        persist(entries);
        return report(context, "airdrop.loot.detached", ChatFormatting.GREEN, GunSmith.slotName(slot), stack.getHoverName());
    }

    private LiteralArgumentBuilder<CommandSourceStack> removeBranch() {
        return Commands.literal("remove")
                .then(Commands.argument("index", IntegerArgumentType.integer(0)).executes(this::remove));
    }

    private LiteralArgumentBuilder<CommandSourceStack> editBranch() {
        return Commands.literal("edit")
                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                        .then(Commands.literal("count")
                                .then(Commands.argument("min", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("max", IntegerArgumentType.integer(1))
                                                .executes(this::editCount))))
                        .then(Commands.literal("chance")
                                .then(Commands.argument("percent", IntegerArgumentType.integer(1, 100))
                                        .executes(this::editChance)))
                        .then(Commands.literal("nbt")
                                .then(Commands.argument("nbt_data", CompoundTagArgument.compoundTag())
                                        .executes(this::editNbt)))
                        .then(Commands.literal("clear_nbt").executes(this::clearNbt)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> orderBranch() {
        return Commands.literal("swap")
                .then(Commands.argument("index1", IntegerArgumentType.integer(0))
                        .then(Commands.argument("index2", IntegerArgumentType.integer(0))
                                .executes(this::swap)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> duplicateBranch() {
        return Commands.literal("duplicate")
                .then(Commands.argument("index", IntegerArgumentType.integer(0)).executes(this::duplicate));
    }

    private int duplicate(CommandContext<CommandSourceStack> context) {
        List<LootEntry> entries = entries();
        int index = IntegerArgumentType.getInteger(context, "index");
        if (!inRange(entries, index)) return fail(context, "airdrop.loot.invalid_index");

        entries.add(entries.get(index));
        persist(entries);
        return report(context, "airdrop.loot.duplicated", ChatFormatting.AQUA, index, entries.size() - 1);
    }

    private int list(CommandContext<CommandSourceStack> context) {
        List<LootEntry> entries = AirDropLootManager.getTable(table);
        if (entries.isEmpty()) return report(context, "airdrop.loot.empty", ChatFormatting.RED);

        context.getSource().sendSuccess(() -> Component.translatable("airdrop.loot.list_header", entries.size())
                .withStyle(ChatFormatting.GOLD), false);
        for (int index = 0; index < entries.size(); index++) {
            context.getSource().sendSuccess(describe(entries.get(index), index), false);
        }
        return 1;
    }

    private java.util.function.Supplier<Component> describe(LootEntry entry, int index) {
        ItemStack stack = AirDropLootManager.stackOf(entry, 1);
        List<String> installed = GunSmith.installed(stack);
        return () -> Component.translatable("airdrop.loot.entry_index", index).withStyle(ChatFormatting.YELLOW)
                .append(stack.getHoverName().copy().withStyle(ChatFormatting.WHITE))
                .append(Component.translatable("airdrop.loot.count_range", entry.min(), entry.max())
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("airdrop.loot.chance", (int) (entry.chance() * 100))
                        .withStyle(ChatFormatting.GREEN))
                .append(installed.isEmpty() ? Component.empty()
                        : Component.literal(" " + String.join(", ", installed)).withStyle(ChatFormatting.AQUA));
    }

    private int addFromArgument(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ItemInput input = ItemArgument.getItem(context, "item");
        return store(context, input.createItemStack(1, false));
    }

    private int addFromHand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return fail(context, "airdrop.loot.empty_hand");
        return store(context, held.copy());
    }

    private int store(CommandContext<CommandSourceStack> context, ItemStack stack) {
        int min = IntegerArgumentType.getInteger(context, "min");
        int max = IntegerArgumentType.getInteger(context, "max");
        if (max < min) return fail(context, "airdrop.loot.error_max_min");

        float chance = IntegerArgumentType.getInteger(context, "percent") / 100.0F;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String nbt = stack.hasTag() ? stack.getTag().toString() : null;

        List<LootEntry> entries = entries();
        entries.add(new LootEntry(itemId, min, max, chance, nbt));
        persist(entries);
        return report(context, "airdrop.loot.added_index", ChatFormatting.GREEN, entries.size() - 1,
                stack.getHoverName().getString());
    }

    private int attach(CommandContext<CommandSourceStack> context) {
        List<LootEntry> entries = entries();
        int index = IntegerArgumentType.getInteger(context, "index");
        if (index < 0 || index >= entries.size()) return fail(context, "airdrop.loot.invalid_index");

        LootEntry entry = entries.get(index);
        ItemStack stack = AirDropLootManager.stackOf(entry, 1);
        String option = StringArgumentType.getString(context, "attachment");

        Outcome outcome = GunSmith.attach(stack, option);
        if (outcome != Outcome.DONE) return fail(context, refusal(outcome), GunSmith.optionName(option));

        entries.set(index, AirDropLootManager.describing(entry, stack));
        persist(entries);
        return report(context, "airdrop.loot.attached", ChatFormatting.GREEN, GunSmith.optionName(option), stack.getHoverName());
    }

    private static String refusal(Outcome outcome) {
        return switch (outcome) {
            case NOT_A_GUN -> "airdrop.loot.attach.not_a_gun";
            case INCOMPATIBLE -> "airdrop.loot.attach.incompatible";
            default -> "airdrop.loot.attach.unknown";
        };
    }

    private static List<String> attachmentOptions(CommandContext<CommandSourceStack> context) {
        ItemStack stack = pointedStack(context);
        return stack == null ? List.of() : GunSmith.options(stack);
    }

    private static List<String> slotNames(CommandContext<CommandSourceStack> context) {
        ItemStack stack = pointedStack(context);
        if (stack == null) return List.of();

        List<String> names = new ArrayList<>();
        for (GunSlot slot : GunSmith.slots(stack)) {
            names.add(slot.id());
        }
        return names;
    }

    private static ItemStack pointedStack(CommandContext<CommandSourceStack> context) {
        try {
            int index = IntegerArgumentType.getInteger(context, "index");
            List<LootEntry> entries = AirDropLootManager.getTable(new ResourceLocation("airdrop", "global"));
            if (index < 0 || index >= entries.size()) return null;
            return AirDropLootManager.stackOf(entries.get(index), 1);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int remove(CommandContext<CommandSourceStack> context) {
        List<LootEntry> entries = entries();
        int index = IntegerArgumentType.getInteger(context, "index");
        if (index < 0 || index >= entries.size()) return fail(context, "airdrop.loot.invalid_index");

        LootEntry removed = entries.remove(index);
        persist(entries);
        return report(context, "airdrop.loot.removed", ChatFormatting.GREEN, index,
                AirDropLootManager.stackOf(removed, 1).getHoverName().getString());
    }

    private int editCount(CommandContext<CommandSourceStack> context) {
        int min = IntegerArgumentType.getInteger(context, "min");
        int max = IntegerArgumentType.getInteger(context, "max");
        if (max < min) return fail(context, "airdrop.loot.error_max_min");

        return replace(context, entry -> new LootEntry(entry.itemId(), min, max, entry.chance(), entry.nbt()),
                "airdrop.loot.updated_count");
    }

    private int editChance(CommandContext<CommandSourceStack> context) {
        int percent = IntegerArgumentType.getInteger(context, "percent");
        return replace(context, entry -> new LootEntry(entry.itemId(), entry.min(), entry.max(),
                percent / 100.0F, entry.nbt()), "airdrop.loot.updated_chance");
    }

    private int editNbt(CommandContext<CommandSourceStack> context) {
        CompoundTag tag = CompoundTagArgument.getCompoundTag(context, "nbt_data");
        return replace(context, entry -> new LootEntry(entry.itemId(), entry.min(), entry.max(),
                entry.chance(), tag.toString()), "airdrop.loot.updated_nbt");
    }

    private int clearNbt(CommandContext<CommandSourceStack> context) {
        return replace(context, entry -> new LootEntry(entry.itemId(), entry.min(), entry.max(),
                entry.chance(), null), "airdrop.loot.cleared_nbt");
    }

    private int replace(CommandContext<CommandSourceStack> context,
                        java.util.function.UnaryOperator<LootEntry> change, String key) {
        List<LootEntry> entries = entries();
        int index = IntegerArgumentType.getInteger(context, "index");
        if (index < 0 || index >= entries.size()) return fail(context, "airdrop.loot.invalid_index");

        entries.set(index, change.apply(entries.get(index)));
        persist(entries);
        return report(context, key, ChatFormatting.GREEN, index);
    }

    private int swap(CommandContext<CommandSourceStack> context) {
        List<LootEntry> entries = entries();
        int first = IntegerArgumentType.getInteger(context, "index1");
        int second = IntegerArgumentType.getInteger(context, "index2");
        if (!inRange(entries, first) || !inRange(entries, second)) return fail(context, "airdrop.loot.invalid_index");

        Collections.swap(entries, first, second);
        persist(entries);
        return report(context, "airdrop.loot.swapped", ChatFormatting.LIGHT_PURPLE, first, second);
    }

    private static boolean inRange(List<LootEntry> entries, int index) {
        return index >= 0 && index < entries.size();
    }

    private int clear(CommandContext<CommandSourceStack> context) {
        persist(new ArrayList<>());
        return report(context, "airdrop.loot.cleared", ChatFormatting.RED);
    }

    private List<LootEntry> entries() {
        return new ArrayList<>(AirDropLootManager.getTable(table));
    }

    private void persist(List<LootEntry> entries) {
        AirDropLootManager.setTable(table, entries);
        AirDropLootManager.saveTable(table, FMLPaths.CONFIGDIR.get());
    }

    private static int report(CommandContext<CommandSourceStack> context, String key,
                              ChatFormatting color, Object... args) {
        context.getSource().sendSuccess(() -> Component.translatable(key, args).withStyle(color), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> context, String key, Object... args) {
        context.getSource().sendFailure(Component.translatable(key, args).withStyle(ChatFormatting.RED));
        return 0;
    }
}
