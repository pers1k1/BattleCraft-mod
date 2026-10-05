package com.persiki84.zones.client.menu;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.client.ClientGameData;
import com.persiki84.zones.shop.ShopEntry;
import com.persiki84.zones.shop.ShopSchedule;
import net.minecraft.network.chat.Component;

// WHY: клиент считает окно продажи по часам матча, которые сервер присылает со снимком фазы: так
// WHY: замок снимается в ту же секунду без лишнего пакета. Решает всё равно сервер при покупке
public final class ShopClock {
    private ShopClock() {}

    public static long elapsed() {
        return ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.ACTIVE
                ? ClientGameData.getMatchElapsedMillis() : ShopSchedule.OUTSIDE_MATCH;
    }

    public static ShopSchedule.State state(ShopEntry entry) {
        return ShopSchedule.state(entry, elapsed(), ClientGameData.isSoftDisabled());
    }

    public static boolean open(ShopEntry entry) {
        return state(entry) == ShopSchedule.State.OPEN;
    }

    public static Component waiting(ShopEntry entry) {
        long elapsed = elapsed();
        if (elapsed < 0L) {
            return Component.translatable("zones.shop.opens_match", ShopSchedule.clock(entry.opensAfter()));
        }
        return Component.translatable("zones.shop.opens_in", ShopSchedule.clock(ShopSchedule.secondsToOpen(entry, elapsed)));
    }

    public static Component label(ShopEntry entry) {
        return switch (state(entry)) {
            case WAITING -> waiting(entry);
            case CLOSED -> Component.translatable("zones.shop.closed");
            case OPEN -> Component.empty();
        };
    }
}
