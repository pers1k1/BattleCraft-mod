package com.persiki84.knockdown.network;

import com.persiki84.shared.ActionGate;
import net.minecraft.server.level.ServerPlayer;

public final class ReviveRate {
    private static final String TICK_KEY = "reviveTick";
    private static final int ONE_TICK = 1;

    private ReviveRate() {}

    // WHY: прогресс подъёма начислялся на каждый принятый пакет, а клиент шлёт их сам: пачка
    // WHY: пакетов за тик поднимала мгновенно и тратила один шприц вместо полного каста
    public static boolean allow(ServerPlayer player) {
        return ActionGate.allow(player, TICK_KEY, ONE_TICK);
    }
}
