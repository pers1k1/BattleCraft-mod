package com.persiki84.airdrop.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.airdrop.loot.LootEntry;
import com.persiki84.airdrop.loot.LootTable;
import com.persiki84.shared.gunsmith.GunSlot;
import com.persiki84.shared.gunsmith.GunSmith;
import com.persiki84.shared.gunsmith.Outcome;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public final class LootEntryCommands {
    private static final SuggestionProvider<CommandSourceStack> ATTACHMENTS = (context, builder) ->
            SharedSuggestionProvider.suggest(attachmentOptions(context), builder);
    private static final SuggestionProvider<CommandSourceStack> SLOTS = (context, builder) ->
            SharedSuggestionProvider.suggest(slotNames(context), builder);

    private LootEntryCommands() {}

    public static RequiredArgumentBuilder<CommandSourceStack, Integer> indexed() {
        return Commands.argument("index", IntegerArgumentType.integer(0))
                .then(Commands.literal("remove").executes(LootEntryCommands::remove))
                .then(Commands.literal("duplicate").executes(LootEntryCommands::duplicate))
                .then(Commands.literal("clear_nbt").executes(context -> replace(context,
                        entry -> entry.tagged(null), "airdrop.loot.cleared_nbt")))
                .then(Commands.literal("move").then(Commands.argument("to", IntegerArgumentType.integer(0))
                        .executes(LootEntryCommands::move)))
                .then(Commands.literal("count").then(Commands.argument("min",
                        IntegerArgumentType.integer(1, LootEntry.MAX_COUNT)).then(Commands.argument("max",
                        IntegerArgumentType.integer(1, LootEntry.MAX_COUNT)).executes(LootEntryCommands::count))))
                .then(Commands.literal("chance").then(Commands.argument("percent",
                        FloatArgumentType.floatArg(0.01f, 100.0f)).executes(LootEntryCommands::chance)))
                .then(Commands.literal("nbt").then(Commands.argument("nbt_data", CompoundTagArgument.compoundTag())
                        .executes(context -> replace(context, entry -> entry.tagged(
                                CompoundTagArgument.getCompoundTag(context, "nbt_data").toString()),
                                "airdrop.loot.updated_nbt"))))
                .then(Commands.literal("attach").then(Commands.argument("attachment", StringArgumentType.string())
                        .suggests(ATTACHMENTS).executes(LootEntryCommands::attach)))
                .then(Commands.literal("detach").then(Commands.argument("slot", StringArgumentType.word())
                        .suggests(SLOTS).executes(LootEntryCommands::detach)));
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        return reshape(context, (entries, index) -> entries.remove((int) index), "airdrop.loot.removed");
    }

    private static int duplicate(CommandContext<CommandSourceStack> context) {
        return reshape(context, (entries, index) -> entries.add(index + 1, entries.get(index)),
                "airdrop.loot.duplicated");
    }

    // WHY: перенос плитки в редакторе это вынуть и вставить, а не обмен: обмен двигал бы чужую
    // WHY: плитку через всю сетку на место взятой
    private static int move(CommandContext<CommandSourceStack> context) {
        int target = IntegerArgumentType.getInteger(context, "to");
        return reshape(context, (entries, index) -> {
            LootEntry taken = entries.remove((int) index);
            entries.add(Math.min(target, entries.size()), taken);
        }, "airdrop.loot.moved");
    }

    private static int count(CommandContext<CommandSourceStack> context) {
        int min = IntegerArgumentType.getInteger(context, "min");
        int max = IntegerArgumentType.getInteger(context, "max");
        if (max < min) return LootCommands.fail(context, "airdrop.loot.error_max_min");
        return replace(context, entry -> entry.counted(min, max), "airdrop.loot.updated_count");
    }

    private static int chance(CommandContext<CommandSourceStack> context) {
        float chance = FloatArgumentType.getFloat(context, "percent") / 100.0f;
        return replace(context, entry -> entry.chanced(chance), "airdrop.loot.updated_chance");
    }

    private static int attach(CommandContext<CommandSourceStack> context) {
        String option = StringArgumentType.getString(context, "attachment");
        return smith(context, stack -> GunSmith.attach(stack, option), "airdrop.loot.attached",
                GunSmith.optionName(option));
    }

    private static int detach(CommandContext<CommandSourceStack> context) {
        String slot = StringArgumentType.getString(context, "slot");
        return smith(context, stack -> GunSmith.detach(stack, slot), "airdrop.loot.detached", GunSmith.slotName(slot));
    }

    private static int smith(CommandContext<CommandSourceStack> context,
                             java.util.function.Function<ItemStack, Outcome> work, String key, Object shown) {
        LootTable table = LootCommands.table(context);
        if (table == null) return LootCommands.missing(context);
        int index = IntegerArgumentType.getInteger(context, "index");
        if (!table.contains(index)) return LootCommands.fail(context, "airdrop.loot.invalid_index");

        LootEntry entry = table.entries().get(index);
        ItemStack stack = entry.template();
        Outcome outcome = work.apply(stack);
        if (outcome != Outcome.DONE) return LootCommands.fail(context, refusal(outcome), shown);

        return replace(context, current -> current.describing(stack), key, shown, stack.getHoverName());
    }

    private static String refusal(Outcome outcome) {
        return switch (outcome) {
            case NOT_A_GUN -> "airdrop.loot.attach.not_a_gun";
            case INCOMPATIBLE -> "airdrop.loot.attach.incompatible";
            default -> "airdrop.loot.attach.unknown";
        };
    }

    private static int replace(CommandContext<CommandSourceStack> context, UnaryOperator<LootEntry> change,
                               String key, Object... args) {
        return reshape(context, (entries, index) -> entries.set(index, change.apply(entries.get(index))), key, args);
    }

    private static int reshape(CommandContext<CommandSourceStack> context, Reshape work, String key, Object... args) {
        LootTable table = LootCommands.table(context);
        if (table == null) return LootCommands.missing(context);
        int index = IntegerArgumentType.getInteger(context, "index");
        if (!table.contains(index)) return LootCommands.fail(context, "airdrop.loot.invalid_index");

        return LootCommands.edit(context, current -> {
            List<LootEntry> entries = current.editable();
            work.apply(entries, index);
            return current.withEntries(entries);
        }, key, args.length == 0 ? new Object[] {index} : args);
    }

    private static List<String> attachmentOptions(CommandContext<CommandSourceStack> context) {
        ItemStack stack = pointedStack(context);
        return stack == null ? List.of() : GunSmith.options(stack);
    }

    private static List<String> slotNames(CommandContext<CommandSourceStack> context) {
        ItemStack stack = pointedStack(context);
        List<String> names = new ArrayList<>();
        if (stack == null) return names;

        for (GunSlot slot : GunSmith.slots(stack)) {
            names.add(slot.id());
        }
        return names;
    }

    private static ItemStack pointedStack(CommandContext<CommandSourceStack> context) {
        try {
            LootTable table = LootCommands.table(context);
            int index = IntegerArgumentType.getInteger(context, "index");
            return table == null || !table.contains(index) ? null : table.entries().get(index).template();
        } catch (IllegalArgumentException incomplete) {
            return null;
        }
    }

    @FunctionalInterface
    private interface Reshape {
        void apply(List<LootEntry> entries, int index);
    }
}
