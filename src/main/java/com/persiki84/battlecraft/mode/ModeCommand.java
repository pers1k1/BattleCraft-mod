package com.persiki84.battlecraft.mode;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.persiki84.shared.menu.MenuNetwork;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;

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
                                .executes(context -> ModeEdits.moveKit(context, id)))));
    }

    private static int openMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MenuNetwork.open(context.getSource().getPlayerOrException(), ModeMenuState.MENU_ID);
        return 1;
    }
}
