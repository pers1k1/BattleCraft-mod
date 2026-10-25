package com.persiki84.battlecraft.mode;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.LobbyRoster;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.network.MatchVictoryPacket;
import com.persiki84.capturepoints.network.PacketHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

// WHY: время матча берётся у менеджера (стена минус простой сервера), а не тиками: и потолок, и
// WHY: поминутные очки идут от тех же часов, что видит игрок в HUD и что лежат в match.dat
final class MatchClock {
    private static final long MINUTE_MILLIS = 60_000L;
    // WHY: после долгого простоя в одном тике не начисляется больше пары минут разом, остальное
    // WHY: догоняется следующими секундами: обход точек на каждую пропущенную минуту в одном тике дорог
    private static final int CATCH_UP_MINUTES = 2;
    private static final Standing STANDING = new Standing();

    private MatchClock() {}

    static void tick(MinecraftServer server) {
        BattleCraftManager manager = BattleCraftManager.getInstance();
        MatchMode mode = MatchScore.mode();
        if (mode == null || manager.getPhase() != BattleCraftManager.GamePhase.ACTIVE || manager.isSoftDisabled()) return;

        long elapsed = manager.matchElapsedMillis();
        awardHeldPoints(mode, elapsed);
        holdSuddenDeath();
        String winner = decided(server, mode, elapsed);
        if (winner == null) return;
        finish(server, winner.isEmpty() ? null : winner);
    }

    private static void awardHeldPoints(MatchMode mode, long elapsed) {
        long minute = elapsed / MINUTE_MILLIS;
        for (int step = 0; step < CATCH_UP_MINUTES && MatchScore.scoredMinute() < minute; step++) {
            MatchScore.scoredMinute(MatchScore.scoredMinute() + 1L);
            if (mode.pointScore() <= 0 || !ModuleSwitches.allows(ModuleId.CAPTURE_POINTS)) continue;
            for (CapturePoint point : CapturePointManager.getAllPoints()) {
                MatchScore.add(point.getOwnerTeam(), mode.pointScore());
            }
        }
    }

    // WHY: флаг открытой финальной не хранится в points.dat; после рестарта посреди внезапной смерти
    // WHY: его ставит заново счёт матча, который и помнит, что она идёт
    private static void holdSuddenDeath() {
        if (MatchScore.suddenEndMillis() != MatchScore.NO_SUDDEN && !CapturePointManager.isFinalForcedOpen()) {
            CapturePointManager.forceFinalOpen(true);
        }
    }

    // WHY: пустая строка значит «матч кончен ничьей», null - «матч идёт дальше»
    @Nullable
    private static String decided(MinecraftServer server, MatchMode mode, long elapsed) {
        Standing standing = STANDING.measure(server);
        if (mode.scoreTarget() > 0 && standing.clearLeader() && standing.top >= mode.scoreTarget()) {
            return standing.leader;
        }
        if (mode.capMinutes() <= 0 || elapsed < mode.capMinutes() * MINUTE_MILLIS) return null;
        if (MatchScore.suddenEndMillis() != MatchScore.NO_SUDDEN) {
            return elapsed >= MatchScore.suddenEndMillis() ? standing.winnerOrDraw() : null;
        }
        if (mode.suddenMinutes() > 0 && standing.top - standing.second < mode.suddenMargin()) {
            beginSuddenDeath(server, elapsed + mode.suddenMinutes() * MINUTE_MILLIS, mode.suddenMinutes());
            return null;
        }
        return standing.winnerOrDraw();
    }

    private static void beginSuddenDeath(MinecraftServer server, long endMillis, int minutes) {
        MatchScore.suddenEndMillis(endMillis);
        CapturePointManager.forceFinalOpen(true);
        server.getPlayerList().broadcastSystemMessage(Component.translatable("battlecraft.mode.sudden", minutes)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
    }

    private static void finish(MinecraftServer server, @Nullable String winner) {
        if (winner != null) {
            PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new MatchVictoryPacket(winner));
        }
        BattleCraftManager.getInstance().stopMatch(server, winner);
    }

    private static final class Standing {
        @Nullable
        private String leader;
        private int top = -1;
        private int second = -1;
        private boolean tied;

        private Standing measure(MinecraftServer server) {
            leader = null;
            top = -1;
            second = -1;
            tied = false;
            List<String> teams = LobbyRoster.pool(server);
            for (String team : teams) {
                consider(team, MatchScore.scoreOf(team));
            }
            if (second < 0) second = 0;
            return this;
        }

        private boolean clearLeader() {
            return leader != null && !tied;
        }

        private void consider(String team, int score) {
            if (score > top) {
                second = top;
                top = score;
                leader = team;
                tied = false;
            } else if (score == top) {
                tied = true;
                second = score;
            } else if (score > second) {
                second = score;
            }
        }

        private String winnerOrDraw() {
            return clearLeader() ? leader : "";
        }
    }
}
