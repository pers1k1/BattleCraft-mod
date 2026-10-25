package com.persiki84.battlecraft.mode;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// WHY: голос живёт только в лобби и только в памяти: перезапуск сервера между матчами не повод
// WHY: держать прошлое мнение. Считаются лишь игроки онлайн, уходящий исключается явно, потому что
// WHY: Forge шлёт выход до удаления игрока из PlayerList
public final class ModeVotes {
    private static final Map<UUID, ModeId> votes = new HashMap<>();

    private ModeVotes() {}

    static void cast(UUID player, ModeId mode) {
        votes.put(player, mode);
    }

    static void forget(UUID player) {
        votes.remove(player);
    }

    public static void clear() {
        votes.clear();
    }

    @Nullable
    static ModeId mine(UUID player) {
        return votes.get(player);
    }

    static int[] tally(MinecraftServer server, @Nullable UUID leaving) {
        int[] counts = new int[ModeId.values().length];
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(leaving)) continue;
            ModeId vote = votes.get(player.getUUID());
            if (vote != null) counts[vote.ordinal()]++;
        }
        return counts;
    }

    // WHY: закреплённый оператором режим не голосуется; при равенстве остаётся выбранный в /bc, а без
    // WHY: голосов вовсе - тоже он: молчащее лобби не должно менять режим, заданный оператором
    static ModeId winner(MinecraftServer server) {
        ModeId active = ModeBook.active();
        if (ModeBook.locked()) return active;

        int[] counts = tally(server, null);
        ModeId best = active;
        for (ModeId mode : ModeId.values()) {
            if (counts[mode.ordinal()] > counts[best.ordinal()]) best = mode;
        }
        return best;
    }
}
