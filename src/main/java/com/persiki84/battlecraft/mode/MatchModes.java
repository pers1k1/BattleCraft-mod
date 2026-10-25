package com.persiki84.battlecraft.mode;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.modules.ModuleEvents;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.shared.ActionGate;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

// WHY: единственная дверь ядра матча в режимы: менеджер зовёт её на старте, после команд старта,
// WHY: на конце и при сохранении, а остальное устройство режима ему знать незачем
public final class MatchModes {
    private static final String VOTE_GATE = "battlecraft_mode_vote";
    private static final String TOLD_GATE = "battlecraft_mode_vote_told";
    private static final int VOTE_TICKS = 10;
    // WHY: отказ по частоте говорится не чаще окна сторожа; между ними заявка отклоняется молча
    static final Component SILENT_REFUSAL = Component.empty();

    private MatchModes() {}

    // WHY: режим выбирается до события старта: по нему модули сбрасывают состояние, а сброс счетов
    // WHY: обязан уже видеть стартовые деньги выбранного режима
    public static void matchStarting(MinecraftServer server) {
        ModeId chosen = ModeVotes.winner(server);
        MatchScore.begin(ModeBook.mode(chosen).copy());
        CapturePointManager.syncPoints();
        ModeVotes.clear();
        ModuleEvents.syncToAll(server);
        ModeSync.scoreIfChanged();
        ModeSync.lobbyToAll(server);
        server.getPlayerList().broadcastSystemMessage(Component.translatable("battlecraft.mode.chosen",
                Component.translatable(chosen.translationKey())).withStyle(ChatFormatting.AQUA), false);
    }

    public static void matchStarted(MinecraftServer server) {
        MatchMode mode = MatchScore.mode();
        if (mode != null) ModeKit.handOut(server, mode);
    }

    public static void matchEnded(MinecraftServer server) {
        MatchScore.end();
        CapturePointManager.forceFinalOpen(false);
        CapturePointManager.syncPoints();
        ModuleEvents.syncToAll(server);
        ModeSync.scoreIfChanged();
    }

    public static void lobbyReset() {
        ModeVotes.clear();
    }

    public static long startMoney() {
        MatchMode mode = MatchScore.mode();
        return mode == null ? 0L : mode.startMoney();
    }

    // WHY: в матче точка без своего числа платит по идущему режиму, в лобби - по режиму по умолчанию:
    // WHY: именно его увидит игрок, если голос ничего не сдвинет
    public static int pointScore() {
        MatchMode running = MatchScore.mode();
        return running != null ? running.pointScore() : ModeBook.mode(ModeBook.active()).pointScore();
    }

    // WHY: правило «своё число или из режима» живёт в одном месте: окно точки у игрока и начисление
    // WHY: счёта обязаны сходиться
    public static int pointWorth(CapturePoint point) {
        int own = point.getScorePerMinute();
        return own == CapturePoint.SCORE_BY_MODE ? pointScore() : own;
    }

    public static Component scoreLine(MinecraftServer server) {
        return ScoreLine.of(server);
    }

    public static CompoundTag save() {
        return MatchScore.save();
    }

    public static void restore(CompoundTag saved, boolean running) {
        MatchScore.restore(saved, running);
    }

    // WHY: голос это заявка: сторож частоты идёт первым, затем фаза, ядро и замок оператора.
    // WHY: null значит «принят», любой компонент - отказ
    @Nullable
    static Component vote(ServerPlayer player, ModeId mode) {
        if (!ActionGate.allow(player, VOTE_GATE, VOTE_TICKS)) {
            return ActionGate.allow(player, TOLD_GATE, VOTE_TICKS)
                    ? Component.translatable("battlecraft.error.too_fast") : SILENT_REFUSAL;
        }
        BattleCraftManager manager = BattleCraftManager.getInstance();
        if (manager.isSoftDisabled() || manager.getPhase() != BattleCraftManager.GamePhase.LOBBY) {
            return Component.translatable("battlecraft.error.match_running");
        }
        if (ModeBook.locked()) return Component.translatable("battlecraft.mode.error.locked");

        ModeVotes.cast(player.getUUID(), mode);
        ModeSync.lobbyToAll(player.getServer());
        return null;
    }
}
