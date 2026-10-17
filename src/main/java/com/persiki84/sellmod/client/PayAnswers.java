package com.persiki84.sellmod.client;

import com.persiki84.sellmod.SellMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// WHY: ответ на перевод приходит системным сообщением, а окно перевода обязано показать его у себя:
// WHY: отказ сервера (не хватает, игрок вышел, у него нет места) иначе ушёл бы тостом мимо окна.
// WHY: Слушаем раньше разбора тостов и с отменёнными: сам тост этот обработчик не трогает
@Mod.EventBusSubscriber(modid = SellMod.MODID, value = Dist.CLIENT)
public final class PayAnswers {
    private static final String PREFIX = "sellmod.pay.";
    private static final String SENT = "sellmod.pay.sent";
    private static final int DEPTH = 3;

    private static Component answer;
    private static boolean refused;
    private static long arrivedAt;

    private PayAnswers() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        String key = payKey(event.getMessage(), 0);
        if (key == null || key.equals("sellmod.pay.received")) return;

        answer = event.getMessage();
        refused = !key.equals(SENT);
        arrivedAt = System.currentTimeMillis();
    }

    private static String payKey(Component message, int depth) {
        if (message.getContents() instanceof TranslatableContents contents && contents.getKey().startsWith(PREFIX)) {
            return contents.getKey();
        }
        if (depth >= DEPTH) return null;
        for (Component sibling : message.getSiblings()) {
            String found = payKey(sibling, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    public static Component since(long sentAt) {
        return answer != null && arrivedAt >= sentAt ? answer : null;
    }

    public static boolean refused() {
        return refused;
    }

    public static long arrivedAt() {
        return arrivedAt;
    }
}
