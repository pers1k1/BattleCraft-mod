package com.persiki84.airdrop.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.airdrop.cache.LootCaches;
import com.persiki84.airdrop.config.AirDropConfig;
import com.persiki84.airdrop.loot.LootEntry;
import com.persiki84.airdrop.loot.LootTable;
import com.persiki84.airdrop.loot.LootTables;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

public final class LootCommands {
    public static final SuggestionProvider<CommandSourceStack> TABLES = (context, builder) ->
            SharedSuggestionProvider.suggest(LootTables.names(), builder);

    private static final int INVENTORY_SLOTS = 41;

    private LootCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return Commands.literal("loot")
                .then(Commands.literal("tables").executes(LootCommands::tables))
                .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())
                        .executes(LootCommands::create)))
                .then(Commands.literal("delete").then(Commands.argument("name", StringArgumentType.word())
                        .suggests(TABLES).executes(LootCommands::delete)))
                .then(Commands.literal("copy").then(Commands.argument("name", StringArgumentType.word())
                        .suggests(TABLES).then(Commands.argument("target", StringArgumentType.word())
                                .executes(LootCommands::copy))))
                .then(Commands.argument("table", StringArgumentType.word()).suggests(TABLES)
                        .then(Commands.literal("list").executes(LootCommands::list))
                        .then(Commands.literal("clear").executes(LootCommands::clear))
                        .then(Commands.literal("add").then(Commands.argument("item", ItemArgument.item(context))
                                .then(amounts(LootCommands::addItem))))
                        .then(Commands.literal("hand").then(amounts(LootCommands::addHand)))
                        .then(Commands.literal("slot").then(Commands.argument("slot",
                                IntegerArgumentType.integer(0, INVENTORY_SLOTS - 1)).then(amounts(LootCommands::addSlot))))
                        .then(Commands.literal("items").then(Commands.argument("min",
                                IntegerArgumentType.integer(0, LootTable.ITEMS_LIMIT)).then(Commands.argument("max",
                                IntegerArgumentType.integer(0, LootTable.ITEMS_LIMIT)).executes(LootCommands::rules))))
                        .then(LootEntryCommands.indexed()));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> amounts(
            com.mojang.brigadier.Command<CommandSourceStack> action) {
        return Commands.argument("min", IntegerArgumentType.integer(1, LootEntry.MAX_COUNT))
                .then(Commands.argument("max", IntegerArgumentType.integer(1, LootEntry.MAX_COUNT))
                        .then(Commands.argument("percent", FloatArgumentType.floatArg(0.01f, 100.0f))
                                .executes(action)));
    }

    static LootTable table(CommandContext<CommandSourceStack> context) {
        return LootTables.get(StringArgumentType.getString(context, "table"));
    }

    private static int tables(CommandContext<CommandSourceStack> context) {
        for (LootTable table : LootTables.all()) {
            context.getSource().sendSuccess(() -> Component.translatable("airdrop.loot.table_line", table.name(),
                    table.entries().size(), LootCaches.usingTable(table.name())), false);
        }
        return LootTables.names().size();
    }

    private static int create(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!LootTables.validName(name)) return fail(context, "airdrop.loot.bad_name", LootTables.NAME_LIMIT);
        if (LootTables.exists(name)) return fail(context, "airdrop.loot.table_exists", name);

        LootTables.put(LootTable.empty(name));
        return report(context, "airdrop.loot.table_created", ChatFormatting.GREEN, name);
    }

    private static int copy(CommandContext<CommandSourceStack> context) {
        LootTable source = LootTables.get(StringArgumentType.getString(context, "name"));
        String target = StringArgumentType.getString(context, "target");
        if (source == null) return fail(context, "airdrop.loot.no_table", StringArgumentType.getString(context, "name"));
        if (!LootTables.validName(target)) return fail(context, "airdrop.loot.bad_name", LootTables.NAME_LIMIT);
        if (LootTables.exists(target)) return fail(context, "airdrop.loot.table_exists", target);

        LootTables.put(source.renamed(target));
        return report(context, "airdrop.loot.table_copied", ChatFormatting.GREEN, source.name(), target);
    }

    // WHY: таблица, на которую смотрит тайник или аирдроп, не удаляется: тайник молча остался бы
    // WHY: пустым на весь матч, и причину пришлось бы искать по файлам
    private static int delete(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!LootTables.exists(name)) return fail(context, "airdrop.loot.no_table", name);
        if (LootTables.AIRDROP.equals(name) || name.equals(AirDropConfig.airdropTable())) {
            return fail(context, "airdrop.loot.table_airdrop", name);
        }
        int users = LootCaches.usingTable(name);
        if (users > 0) return fail(context, "airdrop.loot.table_used", name, users);

        LootTables.delete(name);
        return report(context, "airdrop.loot.table_deleted", ChatFormatting.YELLOW, name);
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        LootTable table = table(context);
        if (table == null) return missing(context);
        if (table.entries().isEmpty()) return report(context, "airdrop.loot.empty", ChatFormatting.GRAY);

        context.getSource().sendSuccess(() -> Component.translatable("airdrop.loot.list_header", table.name(),
                table.entries().size()).withStyle(ChatFormatting.GOLD), false);
        for (int index = 0; index < table.entries().size(); index++) {
            LootEntry entry = table.entries().get(index);
            int shown = index;
            context.getSource().sendSuccess(() -> Component.translatable("airdrop.loot.line", shown,
                    entry.template().getHoverName(), entry.min(), entry.max(), percent(entry.chance())), false);
        }
        return table.entries().size();
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        return edit(context, table -> table.withEntries(List.of()), "airdrop.loot.cleared");
    }

    private static int rules(CommandContext<CommandSourceStack> context) {
        int min = IntegerArgumentType.getInteger(context, "min");
        int max = IntegerArgumentType.getInteger(context, "max");
        if (max != LootTable.UNCAPPED && max < min) return fail(context, "airdrop.loot.error_max_min");

        return edit(context, table -> table.withRules(min, max), "airdrop.loot.rules_set");
    }

    private static int addItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return store(context, ItemArgument.getItem(context, "item").createItemStack(1, false));
    }

    private static int addHand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ItemStack held = context.getSource().getPlayerOrException().getMainHandItem();
        if (held.isEmpty()) return fail(context, "airdrop.loot.empty_hand");
        return store(context, held.copy());
    }

    // WHY: редактор переносит предмет из инвентаря номером слота, а сам предмет сервер берёт у себя:
    // WHY: присланный клиентом стак с NBT был бы заявкой, которой верить нельзя
    private static int addSlot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack stack = player.getInventory().getItem(IntegerArgumentType.getInteger(context, "slot"));
        if (stack.isEmpty()) return fail(context, "airdrop.loot.empty_slot");
        return store(context, stack.copy());
    }

    private static int store(CommandContext<CommandSourceStack> context, ItemStack stack) {
        int min = IntegerArgumentType.getInteger(context, "min");
        int max = IntegerArgumentType.getInteger(context, "max");
        if (max < min) return fail(context, "airdrop.loot.error_max_min");

        float chance = FloatArgumentType.getFloat(context, "percent") / 100.0f;
        LootEntry entry = LootEntry.of(stack, min, max, chance);
        return edit(context, table -> {
            List<LootEntry> entries = table.editable();
            entries.add(entry);
            return table.withEntries(entries);
        }, "airdrop.loot.added", stack.getHoverName());
    }

    static int edit(CommandContext<CommandSourceStack> context, UnaryOperator<LootTable> change,
                    String key, Object... args) {
        LootTable table = table(context);
        if (table == null) return missing(context);

        LootTables.put(change.apply(table));
        return report(context, key, ChatFormatting.GREEN, args);
    }

    static int missing(CommandContext<CommandSourceStack> context) {
        return fail(context, "airdrop.loot.no_table", StringArgumentType.getString(context, "table"));
    }

    static String percent(float chance) {
        float value = chance * 100.0f;
        return value >= 10.0f || value == Math.round(value)
                ? String.valueOf(Math.round(value))
                : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    static int report(CommandContext<CommandSourceStack> context, String key, ChatFormatting color, Object... args) {
        context.getSource().sendSuccess(() -> Component.translatable(key, args).withStyle(color), true);
        return 1;
    }

    static int fail(CommandContext<CommandSourceStack> context, String key, Object... args) {
        context.getSource().sendFailure(Component.translatable(key, args));
        return 0;
    }
}
