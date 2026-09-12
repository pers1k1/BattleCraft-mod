package com.persiki84.shared;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public final class ActionGate {
    private ActionGate() {}

    // WHY: клиентский пакет это заявка, и частота заявок принадлежит клиенту: без счёта по
    // WHY: серверному тику одно действие игрока превращается в поток записей на диск и рассылок
    public static boolean allow(ServerPlayer player, String key, int ticks) {
        int now = player.server.getTickCount();
        CompoundTag data = player.getPersistentData();
        int last = data.getInt(key);

        if (last <= now && now - last < ticks) return false;

        data.putInt(key, now);
        return true;
    }
}
