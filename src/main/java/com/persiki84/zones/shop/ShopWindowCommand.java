package com.persiki84.zones.shop;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

// WHY: окно продажи правится двумя ветками, «opens» и «closes», в секундах от старта матча; ноль
// WHY: снимает свою границу. Закрытие не раньше открытия: иначе товар не продавался бы никогда
final class ShopWindowCommand {
    private static final String SECONDS = "seconds";

    private ShopWindowCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> branch(String verb, boolean opening) {
        return Commands.literal(verb).then(ShopCommand.sectionNode(ShopCommand.entryNode()
                .then(Commands.argument(SECONDS, IntegerArgumentType.integer(0, ShopEntry.WINDOW_LIMIT))
                        .executes(context -> set(context, opening)))));
    }

    // WHY: инспектор шлёт открытие и закрытие по очереди, открытие первым. Сдвиг окна целиком
    // WHY: (0-5 мин в 10-20 мин) иначе упирался бы в старое закрытие, и открытие отклонялось бы:
    // WHY: перекрытое открытием закрытие снимается, следом приходит новое
    private static int keptClose(ShopEntry entry, int opens) {
        return entry.windowValid(opens, entry.closesAfter()) ? entry.closesAfter() : 0;
    }

    private static int set(CommandContext<CommandSourceStack> context, boolean opening) {
        ShopEntry entry = ShopCommand.requireEntry(context);
        if (entry == null) return 0;

        int seconds = IntegerArgumentType.getInteger(context, SECONDS);
        int opens = opening ? seconds : entry.opensAfter();
        int closes = opening ? keptClose(entry, seconds) : seconds;
        if (!entry.windowValid(opens, closes)) {
            context.getSource().sendFailure(Component.translatable("zones.shop.error.window",
                    ShopSchedule.clock(opens), ShopSchedule.clock(closes)));
            return 0;
        }
        entry.setWindow(opens, closes);
        ShopCatalog.persist();
        String key = "zones.shop.success." + (opening ? "opens" : "closes") + (seconds > 0 ? "_set" : "_off");
        return ShopCommand.report(context, key, entry.id(), ShopSchedule.clock(seconds));
    }
}
