package com.persiki84.battlecraft.mode;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.shared.gunsmith.GunSlot;
import com.persiki84.shared.gunsmith.GunSmith;
import com.persiki84.shared.menu.MenuNetwork;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ObjIntConsumer;

// WHY: голос открыт всем, поэтому на литерале mode прав нет: они стоят на подветках правки, иначе
// WHY: голосование было бы недостижимо для игрока
public final class ModeCommand {
    static final String VALUE = "value";
    static final String ITEM = "item";
    static final String COUNT = "count";
    static final String SLOT = "slot";
    static final String INDEX = "index";
    static final String TO = "to";
    static final String ATTACHMENT = "attachment";
    static final String TEXT = "text";
    private static final int SLOT_MAX = 40;

    private ModeCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext build) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("mode")
                .executes(ModeCommand::openMenu)
                .then(voteBranch())
                .then(perMode(Commands.literal("active").requires(ModeCommand::operator), ModeEdits::active))
                .then(Commands.literal("lock").requires(ModeCommand::operator)
                        .then(Commands.argument(VALUE, BoolArgumentType.bool()).executes(ModeEdits::lock)));
        for (ModeId id : ModeId.values()) {
            root.then(Commands.literal(id.id()).requires(ModeCommand::operator).then(numbers(id))
                    .then(kitBranch(id, build)).then(Commands.literal("quarries")
                            .then(Commands.argument(VALUE, BoolArgumentType.bool())
                                    .executes(context -> ModeEdits.quarries(context, id))))
                    .then(Commands.literal("money").then(Commands.argument(VALUE, LongArgumentType.longArg(0L,
                            ModeLimits.MONEY_MAX)).executes(context -> ModeEdits.money(context, id))))
                    .then(Commands.literal("describe").executes(context -> ModeEdits.undescribe(context, id))
                            .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                                    .executes(context -> ModeEdits.describe(context, id))))
                    .then(Commands.literal("reset").executes(context -> ModeEdits.reset(context, id))));
        }
        return root;
    }

    private static boolean operator(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> voteBranch() {
        LiteralArgumentBuilder<CommandSourceStack> vote = Commands.literal("vote");
        for (ModeId id : ModeId.values()) {
            vote.then(Commands.literal(id.id()).executes(context -> ModeEdits.vote(context, id)));
        }
        return vote;
    }

    private interface ModeAction {
        int run(CommandContext<CommandSourceStack> context, ModeId id) throws CommandSyntaxException;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> perMode(LiteralArgumentBuilder<CommandSourceStack> node,
                                                                      ModeAction action) {
        for (ModeId id : ModeId.values()) {
            node.then(Commands.literal(id.id()).executes(context -> action.run(context, id)));
        }
        return node;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> numbers(ModeId id) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("set");
        number(node, id, "points", ModeLimits.SCORE_MAX, MatchMode::pointScore);
        number(node, id, "kills", ModeLimits.SCORE_MAX, MatchMode::killScore);
        number(node, id, "vehicles", ModeLimits.SCORE_MAX, MatchMode::vehicleScore);
        number(node, id, "transport", ModeLimits.SCORE_MAX, MatchMode::transportScore);
        number(node, id, "target", ModeLimits.TARGET_MAX, MatchMode::scoreTarget);
        number(node, id, "cap", ModeLimits.CAP_MINUTES_MAX, MatchMode::capMinutes);
        number(node, id, "sudden_margin", ModeLimits.SUDDEN_MARGIN_MAX, MatchMode::suddenMargin);
        number(node, id, "sudden_minutes", ModeLimits.SUDDEN_MINUTES_MAX, MatchMode::suddenMinutes);
        return node;
    }

    private static void number(LiteralArgumentBuilder<CommandSourceStack> node, ModeId id, String name, int maximum,
                               ObjIntConsumer<MatchMode> setter) {
        node.then(Commands.literal(name).then(Commands.argument(VALUE, IntegerArgumentType.integer(0, maximum))
                .executes(context -> ModeEdits.number(context, id, name, setter))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> kitBranch(ModeId id, CommandBuildContext build) {
        return Commands.literal("kit")
                .then(Commands.literal("add")
                        .then(Commands.literal("slot").then(Commands.argument(SLOT, IntegerArgumentType.integer(0, SLOT_MAX))
                                .executes(context -> ModeEdits.addSlot(context, id))))
                        .then(Commands.argument(ITEM, ItemArgument.item(build))
                                .executes(context -> ModeEdits.addItem(context, id, 1))
                                .then(Commands.argument(COUNT, IntegerArgumentType.integer(1, ModeLimits.KIT_COUNT_MAX))
                                        .executes(context -> ModeEdits.addItem(context, id,
                                                IntegerArgumentType.getInteger(context, COUNT))))))
                .then(Commands.literal("clear").executes(context -> ModeEdits.clearKit(context, id)))
                .then(Commands.argument(INDEX, IntegerArgumentType.integer(0, ModeLimits.KIT_ENTRIES_MAX - 1))
                        .then(Commands.literal("remove").executes(context -> ModeEdits.removeKit(context, id)))
                        .then(Commands.literal("count").then(Commands.argument(COUNT,
                                IntegerArgumentType.integer(1, ModeLimits.KIT_COUNT_MAX))
                                .executes(context -> ModeEdits.countKit(context, id))))
                        .then(Commands.literal("move").then(Commands.argument(TO,
                                IntegerArgumentType.integer(0, ModeLimits.KIT_ENTRIES_MAX - 1))
                                .executes(context -> ModeEdits.moveKit(context, id))))
                        .then(Commands.literal("attach").then(Commands.argument(ATTACHMENT, StringArgumentType.string())
                                .suggests(attachments(id)).executes(context -> ModeEdits.attachKit(context, id))))
                        .then(Commands.literal("detach").then(Commands.argument(SLOT, StringArgumentType.word())
                                .suggests(slots(id)).executes(context -> ModeEdits.detachKit(context, id)))));
    }

    // WHY: обвес это id с двоеточием, а оно без кавычек не разбирается: подсказка ставит кавычки
    // WHY: сама и совпадает с набранным и с кавычкой, и без неё
    private static SuggestionProvider<CommandSourceStack> attachments(ModeId id) {
        return (context, builder) -> {
            ItemStack stack = pointedStack(context, id);
            String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String option : stack.isEmpty() ? List.<String>of() : GunSmith.options(stack)) {
                String quoted = StringArgumentType.escapeIfRequired(option);
                if (option.toLowerCase(Locale.ROOT).startsWith(typed)
                        || quoted.toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(quoted);
            }
            return builder.buildFuture();
        };
    }

    private static SuggestionProvider<CommandSourceStack> slots(ModeId id) {
        return (context, builder) -> {
            List<String> names = new ArrayList<>();
            ItemStack stack = pointedStack(context, id);
            if (!stack.isEmpty()) {
                for (GunSlot slot : GunSmith.slots(stack)) names.add(slot.id());
            }
            return SharedSuggestionProvider.suggest(names, builder);
        };
    }

    // WHY: подсказка спрашивает про запись, номер которой набран раньше: недописанный номер это не ошибка
    private static ItemStack pointedStack(CommandContext<CommandSourceStack> context, ModeId id) {
        try {
            int index = IntegerArgumentType.getInteger(context, INDEX);
            List<KitEntry> kit = ModeBook.mode(id).kit();
            return index < kit.size() ? kit.get(index).stack() : ItemStack.EMPTY;
        } catch (IllegalArgumentException incomplete) {
            return ItemStack.EMPTY;
        }
    }

    private static int openMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MenuNetwork.open(context.getSource().getPlayerOrException(), ModeMenuState.MENU_ID);
        return 1;
    }
}
