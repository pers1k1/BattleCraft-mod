package com.persiki84.battlecraft.radio;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// WHY: два потока на один эфир - серверный тик решает, кто кого слышит, а поток голосового
// WHY: чата шлёт звук пятьдесят раз в секунду и не имеет права ни читать инвентари, ни обходить
// WHY: список игроков. Поэтому тик кладёт сюда готовый список получателей, а поток только берёт
public final class RadioAir {
    private static final long HOLD_MS = 1200L;
    private static final UUID[] NOBODY = new UUID[0];

    private static final Map<UUID, Long> until = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID[]> routes = new ConcurrentHashMap<>();

    private RadioAir() {}

    // WHY: нажатие сразу же собирает маршрут, а не ждёт ближайшего обхода: первый кадр звука
    // WHY: приходит раньше него, и без этого у каждой передачи срезался бы первый слог
    public static void set(ServerPlayer player, boolean talking) {
        UUID id = player.getUUID();
        if (!talking) {
            forget(id);
            return;
        }

        until.put(id, System.currentTimeMillis() + HOLD_MS);
        RadioRelay.arm(player);
    }

    public static boolean onAir(UUID speaker) {
        Long end = until.get(speaker);
        return end != null && System.currentTimeMillis() < end;
    }

    public static void route(UUID speaker, UUID[] listeners) {
        if (listeners.length == 0) {
            routes.remove(speaker);
            return;
        }
        routes.put(speaker, listeners);
    }

    public static UUID[] route(UUID speaker) {
        UUID[] listeners = routes.get(speaker);
        return listeners == null ? NOBODY : listeners;
    }

    public static void forget(UUID speaker) {
        until.remove(speaker);
        routes.remove(speaker);
    }
}
