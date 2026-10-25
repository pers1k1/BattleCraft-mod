package com.persiki84.battlecraft.mode;

import com.persiki84.battlecraft.network.PacketHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// WHY: лобби и счёт уходят только на изменении и при входе: менеджер шлёт фазу каждую секунду, и
// WHY: дублировать туда голоса и счёт значило бы гнать неизменное всем раз в секунду
final class ModeSync {
    private static int sentScoreVersion = Integer.MIN_VALUE;

    private ModeSync() {}

    static void lobbyToAll(@Nullable MinecraftServer server) {
        lobbyToAll(server, null);
    }

    static void lobbyToAll(@Nullable MinecraftServer server, @Nullable UUID leaving) {
        if (server == null) return;
        int[] votes = ModeVotes.tally(server, leaving);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(leaving)) continue;
            send(player, lobbyFor(player, votes));
        }
    }

    static void lobbyTo(ServerPlayer player) {
        send(player, lobbyFor(player, ModeVotes.tally(player.getServer(), null)));
    }

    private static S2CModeLobbyPacket lobbyFor(ServerPlayer player, int[] votes) {
        ModeId mine = ModeVotes.mine(player.getUUID());
        return new S2CModeLobbyPacket(ModeBook.active().ordinal(), ModeBook.locked(),
                mine == null ? S2CModeLobbyPacket.NONE : mine.ordinal(), votes);
    }

    static void scoreIfChanged() {
        if (sentScoreVersion == MatchScore.version()) return;
        sentScoreVersion = MatchScore.version();
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), score());
    }

    static void scoreTo(ServerPlayer player) {
        send(player, score());
    }

    private static S2CMatchScorePacket score() {
        MatchMode mode = MatchScore.mode();
        if (mode == null) return S2CMatchScorePacket.idle();

        List<S2CMatchScorePacket.Team> teams = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : MatchScore.scores().entrySet()) {
            if (entry.getKey().length() <= S2CMatchScorePacket.TEAM_NAME_MAX) {
                teams.add(new S2CMatchScorePacket.Team(entry.getKey(), entry.getValue()));
            }
        }
        return new S2CMatchScorePacket(true, mode.id().ordinal(), mode.scoreTarget(), mode.capMinutes(),
                MatchScore.suddenEndMillis(), teams);
    }

    private static void send(ServerPlayer player, Object packet) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
