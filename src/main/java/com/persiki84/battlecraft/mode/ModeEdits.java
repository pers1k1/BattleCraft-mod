package com.persiki84.battlecraft.mode;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

final class ModeEdits {
    private ModeEdits() {}

    static int vote(CommandContext<CommandSourceStack> context, ModeId id) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Component refusal = MatchModes.vote(player, id);
        if (refusal == null) {
            Component voted = Component.translatable("battlecraft.mode.voted", name(id)).withStyle(ChatFormatting.YELLOW);
            context.getSource().sendSuccess(() -> voted, false);
            return 1;
        }
        if (refusal != MatchModes.SILENT_REFUSAL) context.getSource().sendFailure(refusal);
        return 0;
    }

    static int active(CommandContext<CommandSourceStack> context, ModeId id) {
        if (!ModeBook.active(id)) return closed(context);
        return reply(context, Component.translatable("battlecraft.mode.active.set", name(id)));
    }

    static int lock(CommandContext<CommandSourceStack> context) {
        boolean locked = BoolArgumentType.getBool(context, ModeCommand.VALUE);
        if (!ModeBook.locked(locked)) return closed(context);
        return reply(context, Component.translatable(locked ? "battlecraft.mode.lock.on" : "battlecraft.mode.lock.off"));
    }

    static int number(CommandContext<CommandSourceStack> context, ModeId id, String field,
                      ObjIntConsumer<MatchMode> setter) {
        int value = IntegerArgumentType.getInteger(context, ModeCommand.VALUE);
        return change(context, id, mode -> setter.accept(mode, value),
                Component.translatable("battlecraft.mode.field." + field), String.valueOf(value));
    }

    static int quarries(CommandContext<CommandSourceStack> context, ModeId id) {
        boolean value = BoolArgumentType.getBool(context, ModeCommand.VALUE);
        return change(context, id, mode -> mode.quarries(value),
                Component.translatable("battlecraft.mode.field.quarries"), String.valueOf(value));
    }

    static int money(CommandContext<CommandSourceStack> context, ModeId id) {
        long value = LongArgumentType.getLong(context, ModeCommand.VALUE);
        return change(context, id, mode -> mode.startMoney(value),
                Component.translatable("battlecraft.mode.field.money"), String.valueOf(value));
    }

    static int reset(CommandContext<CommandSourceStack> context, ModeId id) {
        MatchMode fresh = MatchMode.defaults(id);
        boolean done = ModeBook.edit(id, mode -> copyInto(fresh, mode));
        if (!done) return closed(context);
        return reply(context, Component.translatable("battlecraft.mode.reset", name(id)));
    }

    // WHY: сброс оставляет набор и деньги: их собирали руками, и заводских значений у них нет
    private static void copyInto(MatchMode fresh, MatchMode target) {
        target.quarries(fresh.quarries());
        target.pointScore(fresh.pointScore());
        target.killScore(fresh.killScore());
        target.vehicleScore(fresh.vehicleScore());
        target.transportScore(fresh.transportScore());
        target.scoreTarget(fresh.scoreTarget());
        target.capMinutes(fresh.capMinutes());
        target.suddenMargin(fresh.suddenMargin());
        target.suddenMinutes(fresh.suddenMinutes());
    }

    // WHY: стак с тегом (стволы TACZ) не влезает в 256 знаков команды, поэтому студия называет слот,
    // WHY: а сам стак сервер берёт из своего инвентаря игрока: клиентский стак с тегом это заявка
    static int addSlot(CommandContext<CommandSourceStack> context, ModeId id) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int slot = IntegerArgumentType.getInteger(context, ModeCommand.SLOT);
        ItemStack stack = slot < player.getInventory().getContainerSize() ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
        if (stack.isEmpty()) return fail(context, Component.translatable("battlecraft.mode.kit.empty_slot"));
        return addEntry(context, id, KitEntry.of(stack, stack.getCount()), stack);
    }

    static int addItem(CommandContext<CommandSourceStack> context, ModeId id, int count) throws CommandSyntaxException {
        ItemStack stack = ItemArgument.getItem(context, ModeCommand.ITEM).createItemStack(1, false);
        return addEntry(context, id, KitEntry.of(stack, count), stack);
    }

    private static int addEntry(CommandContext<CommandSourceStack> context, ModeId id, KitEntry entry, ItemStack shown) {
        if (ModeBook.mode(id).kit().size() >= ModeLimits.KIT_ENTRIES_MAX) {
            return fail(context, Component.translatable("battlecraft.mode.kit.full", ModeLimits.KIT_ENTRIES_MAX));
        }
        if (entry.tag().length() > ModeLimits.TEXT_MAX) {
            return fail(context, Component.translatable("battlecraft.mode.kit.tag_too_long", ModeLimits.TEXT_MAX));
        }
        if (!ModeBook.edit(id, mode -> mode.addKit(entry))) return closed(context);
        return reply(context, Component.translatable("battlecraft.mode.kit.added", shown.getHoverName(), entry.count(), name(id)));
    }

    static int clearKit(CommandContext<CommandSourceStack> context, ModeId id) {
        if (!ModeBook.edit(id, mode -> mode.kit().clear())) return closed(context);
        return reply(context, Component.translatable("battlecraft.mode.kit.cleared", name(id)));
    }

    static int removeKit(CommandContext<CommandSourceStack> context, ModeId id) {
        int index = IntegerArgumentType.getInteger(context, ModeCommand.INDEX);
        return onEntry(context, id, index, kit -> kit.remove(index), "battlecraft.mode.kit.removed");
    }

    static int countKit(CommandContext<CommandSourceStack> context, ModeId id) {
        int index = IntegerArgumentType.getInteger(context, ModeCommand.INDEX);
        int count = IntegerArgumentType.getInteger(context, ModeCommand.COUNT);
        return onEntry(context, id, index, kit -> kit.set(index, kit.get(index).withCount(count)),
                "battlecraft.mode.kit.counted");
    }

    // WHY: перенос это «вынуть и вставить»: номер назначения считается после изъятия
    static int moveKit(CommandContext<CommandSourceStack> context, ModeId id) {
        int index = IntegerArgumentType.getInteger(context, ModeCommand.INDEX);
        int to = IntegerArgumentType.getInteger(context, ModeCommand.TO);
        return onEntry(context, id, index, kit -> kit.add(Math.min(to, kit.size() - 1), kit.remove(index)),
                "battlecraft.mode.kit.moved");
    }

    private static int onEntry(CommandContext<CommandSourceStack> context, ModeId id, int index,
                               Consumer<List<KitEntry>> change, String key) {
        if (index >= ModeBook.mode(id).kit().size()) {
            return fail(context, Component.translatable("battlecraft.mode.kit.no_entry", index));
        }
        if (!ModeBook.edit(id, mode -> change.accept(mode.kit()))) return closed(context);
        return reply(context, Component.translatable(key, index, name(id)));
    }

    private static int change(CommandContext<CommandSourceStack> context, ModeId id, Consumer<MatchMode> change,
                              Component field, String value) {
        if (!ModeBook.edit(id, change)) return closed(context);
        return reply(context, Component.translatable("battlecraft.mode.changed", name(id), field, value));
    }

    private static Component name(ModeId id) {
        return Component.translatable(id.translationKey());
    }

    private static int reply(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendSuccess(() -> message.copy().withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendFailure(message);
        return 0;
    }

    private static int closed(CommandContext<CommandSourceStack> context) {
        return fail(context, Component.translatable("battlecraft.mode.error.closed"));
    }
}
