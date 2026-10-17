package com.persiki84.sellmod.money;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.shared.economy.CurrencyMode;
import com.persiki84.shared.economy.EconomySettings;
import com.persiki84.shared.economy.Wallet;
import com.persiki84.shared.economy.WalletAccount;
import com.persiki84.shared.economy.WalletAdmin;
import com.persiki84.shared.economy.WalletEntry;
import com.persiki84.shared.economy.WalletEntryKind;
import com.persiki84.shared.economy.Wallets;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MoneyCommand {
    private static final String PLAYER = "player";
    private static final String ACCOUNT = "account";
    private static final String AMOUNT = "amount";
    private static final String NAME = "name";
    private static final String ITEM = "item";
    private static final String SLOT = "slot";
    private static final String PERCENT = "percent";
    private static final String ENABLED = "enabled";
    private static final int INVENTORY_SLOTS = 41;

    private static final SuggestionProvider<CommandSourceStack> ACCOUNTS = (context, builder) ->
            SharedSuggestionProvider.suggest(accountNames(context.getSource()), builder);
    private static final SuggestionProvider<CommandSourceStack> ITEM_IDS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder);

    private MoneyCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> pay() {
        return Commands.literal("pay")
                .then(Commands.argument(PLAYER, EntityArgument.player())
                        .then(Commands.argument(AMOUNT, LongArgumentType.longArg(1L, Wallets.LIMIT))
                                .executes(MoneyCommand::pay)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("money")
                .requires(source -> source.hasPermission(2))
                .executes(MoneyCommand::info)
                .then(Commands.literal("info").executes(MoneyCommand::info))
                .then(Commands.literal("get").then(account().executes(MoneyCommand::get)))
                .then(Commands.literal("history").then(account().executes(MoneyCommand::history)))
                .then(change("set", MoneyCommand::set))
                .then(change("add", context -> shift(context, 1L)))
                .then(change("take", context -> shift(context, -1L)))
                .then(Commands.literal("reset").executes(MoneyCommand::resetAll))
                .then(modeBranch())
                .then(nameBranch())
                .then(iconBranch())
                .then(settingsBranch("startbalance", Commands.argument(AMOUNT, LongArgumentType.longArg(0L,
                        Wallets.LIMIT)).executes(MoneyCommand::startBalance)))
                .then(settingsBranch("deathloss", Commands.argument(PERCENT, IntegerArgumentType.integer(0,
                        EconomySettings.DEATH_LOSS_MAX)).executes(MoneyCommand::deathLoss)))
                .then(settingsBranch("resetonstart", Commands.argument(ENABLED, BoolArgumentType.bool())
                        .executes(MoneyCommand::resetOnStart)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> account() {
        return Commands.argument(ACCOUNT, StringArgumentType.word()).suggests(ACCOUNTS);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> change(String literal, Action action) {
        return Commands.literal(literal).then(account()
                .then(Commands.argument(AMOUNT, LongArgumentType.longArg(0L, Wallets.LIMIT))
                        .executes(action::run)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> settingsBranch(
            String literal, RequiredArgumentBuilder<CommandSourceStack, ?> argument) {
        return Commands.literal(literal).then(argument);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> modeBranch() {
        LiteralArgumentBuilder<CommandSourceStack> branch = Commands.literal("mode");
        for (CurrencyMode mode : CurrencyMode.values()) {
            branch = branch.then(Commands.literal(mode.id()).executes(context -> mode(context, mode)));
        }
        return branch;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> nameBranch() {
        return Commands.literal("name")
                .executes(context -> name(context, ""))
                .then(Commands.argument(NAME, StringArgumentType.greedyString())
                        .executes(context -> name(context, StringArgumentType.getString(context, NAME))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> iconBranch() {
        return Commands.literal("icon")
                .then(Commands.literal("hand").executes(MoneyCommand::iconFromHand))
                .then(Commands.literal("clear").executes(context -> icon(context, ItemStack.EMPTY)))
                .then(Commands.literal("slot").then(Commands.argument(SLOT, IntegerArgumentType.integer(0,
                        INVENTORY_SLOTS - 1)).executes(MoneyCommand::iconFromSlot)))
                .then(Commands.literal("item").then(Commands.argument(ITEM, StringArgumentType.greedyString())
                        .suggests(ITEM_IDS).executes(MoneyCommand::iconById)));
    }

    private static int pay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer from = context.getSource().getPlayerOrException();
        ServerPlayer to = EntityArgument.getPlayer(context, PLAYER);
        return MoneyPayments.pay(from, to, LongArgumentType.getLong(context, AMOUNT)) ? 1 : 0;
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        EconomySettings settings = Wallets.settings();
        Component icon = settings.iconSet() ? settings.icon().getHoverName()
                : Component.translatable("sellmod.money.info.icon_default");
        say(context, Component.translatable("sellmod.money.info", Component.translatable(
                settings.mode().translationKey()), Wallet.unitName(), icon).withStyle(ChatFormatting.GOLD));
        say(context, Component.translatable("sellmod.money.info.rules", Wallet.amountText(settings.startBalance()),
                settings.deathLossPercent(), Component.translatable(settings.resetOnStart()
                        ? "sellmod.money.info.yes" : "sellmod.money.info.no")).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int get(CommandContext<CommandSourceStack> context) {
        WalletAccount account = resolve(context);
        if (account == null) return 0;

        long balance = WalletAdmin.balanceOf(account);
        if (balance == WalletAdmin.UNKNOWN) {
            return fail(context, Component.translatable("sellmod.money.error.offline_items", account.name()));
        }
        say(context, Component.translatable("sellmod.money.balance", account.name(), Wallet.amountText(balance))
                .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int history(CommandContext<CommandSourceStack> context) {
        WalletAccount account = resolve(context);
        if (account == null) return 0;

        List<WalletEntry> journal = account.journal();
        say(context, Component.translatable("sellmod.money.history", account.name(), journal.size())
                .withStyle(ChatFormatting.GOLD));
        for (int index = journal.size() - 1; index >= 0; index--) {
            say(context, line(journal.get(index)));
        }
        return 1;
    }

    private static Component line(WalletEntry entry) {
        String sign = entry.delta() >= 0L ? "+" : "-";
        return Component.translatable("sellmod.money.history.line", Component.translatable(
                entry.kind().translationKey()), sign + Math.abs(entry.delta()), entry.after(), entry.subject())
                .withStyle(entry.delta() >= 0L ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        WalletAccount account = resolve(context);
        if (account == null) return 0;
        return applied(context, account, WalletAdmin.set(account, LongArgumentType.getLong(context, AMOUNT),
                operator(context)));
    }

    private static int shift(CommandContext<CommandSourceStack> context, long sign) {
        WalletAccount account = resolve(context);
        if (account == null) return 0;
        return applied(context, account, WalletAdmin.change(account, sign * LongArgumentType.getLong(context,
                AMOUNT), operator(context)));
    }

    private static int applied(CommandContext<CommandSourceStack> context, WalletAccount account,
                               Component refusal) {
        if (refusal != null) return fail(context, refusal);

        long balance = WalletAdmin.balanceOf(account);
        Component shown = balance == WalletAdmin.UNKNOWN ? Component.literal("?") : Wallet.amountText(balance);
        context.getSource().sendSuccess(() -> Component.translatable("sellmod.money.balance", account.name(),
                shown).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int resetAll(CommandContext<CommandSourceStack> context) {
        int changed = WalletAdmin.resetAll(WalletEntryKind.RESET, operator(context));
        return report(context, Component.translatable("sellmod.money.reset", changed,
                Wallet.amountText(Wallets.settings().startBalance())));
    }

    private static int mode(CommandContext<CommandSourceStack> context, CurrencyMode mode) {
        WalletAdmin.mode(mode);
        return report(context, Component.translatable("sellmod.money.mode_set",
                Component.translatable(mode.translationKey())));
    }

    private static int name(CommandContext<CommandSourceStack> context, String name) {
        WalletAdmin.name(name);
        return report(context, Component.translatable("sellmod.money.name_set", Wallet.unitName()));
    }

    private static int iconFromHand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ItemStack held = context.getSource().getPlayerOrException().getMainHandItem();
        if (held.isEmpty()) return fail(context, Component.translatable("sellmod.money.error.empty_hand"));
        return icon(context, held);
    }

    // WHY: студия называет слот инвентаря, а стак сервер берёт у себя, как магазин: присланный
    // WHY: клиентом предмет с тегом был бы заявкой
    private static int iconFromSlot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack stack = player.getInventory().getItem(IntegerArgumentType.getInteger(context, SLOT));
        if (stack.isEmpty()) return fail(context, Component.translatable("sellmod.money.error.empty_hand"));
        return icon(context, stack);
    }

    private static int iconById(CommandContext<CommandSourceStack> context) {
        ItemStack stack = parseItem(StringArgumentType.getString(context, ITEM));
        if (stack.isEmpty()) return fail(context, Component.translatable("sellmod.money.error.no_item"));
        return icon(context, stack);
    }

    private static ItemStack parseItem(String text) {
        try {
            ItemParser.ItemResult parsed = ItemParser.parseForItem(BuiltInRegistries.ITEM.asLookup(),
                    new StringReader(text));
            ItemStack stack = new ItemStack(parsed.item());
            if (parsed.nbt() != null) stack.setTag(parsed.nbt().copy());
            return stack;
        } catch (CommandSyntaxException invalid) {
            return ItemStack.EMPTY;
        }
    }

    private static int icon(CommandContext<CommandSourceStack> context, ItemStack stack) {
        WalletAdmin.icon(stack);
        Component shown = stack.isEmpty() ? Component.translatable("sellmod.money.info.icon_default")
                : stack.getHoverName();
        return report(context, Component.translatable("sellmod.money.icon_set", shown));
    }

    private static int startBalance(CommandContext<CommandSourceStack> context) {
        WalletAdmin.startBalance(LongArgumentType.getLong(context, AMOUNT));
        return report(context, Component.translatable("sellmod.money.start_set",
                Wallet.amountText(Wallets.settings().startBalance())));
    }

    private static int deathLoss(CommandContext<CommandSourceStack> context) {
        WalletAdmin.deathLossPercent(IntegerArgumentType.getInteger(context, PERCENT));
        return report(context, Component.translatable("sellmod.money.deathloss_set",
                Wallets.settings().deathLossPercent()));
    }

    private static int resetOnStart(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, ENABLED);
        WalletAdmin.resetOnStart(enabled);
        return report(context, Component.translatable(enabled
                ? "sellmod.money.resetonstart_on" : "sellmod.money.resetonstart_off"));
    }

    private static WalletAccount resolve(CommandContext<CommandSourceStack> context) {
        String key = StringArgumentType.getString(context, ACCOUNT);
        WalletAccount account = Wallets.byName(key);
        if (account == null) account = Wallets.account(parseId(key));
        if (account == null) fail(context, Component.translatable("sellmod.money.error.no_account", key));
        return account;
    }

    private static UUID parseId(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static List<String> accountNames(CommandSourceStack source) {
        List<String> names = new ArrayList<>(source.getOnlinePlayerNames());
        for (WalletAccount account : Wallets.all()) {
            if (!account.name().isEmpty() && !names.contains(account.name())) names.add(account.name());
        }
        return names;
    }

    private static String operator(CommandContext<CommandSourceStack> context) {
        return context.getSource().getTextName();
    }

    private static void say(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendSuccess(() -> message, false);
    }

    private static int report(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendSuccess(() -> message.copy().withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendFailure(message.copy().withStyle(ChatFormatting.RED));
        return 0;
    }

    @FunctionalInterface
    private interface Action {
        int run(CommandContext<CommandSourceStack> context);
    }
}
