package com.persiki84.battlecraft;

import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

// WHY: исход матча один объект: победитель и причина. Раньше каждая развилка конца матча сама
// WHY: решала, слать ли баннер, и сдача с остановкой оператором уходили без него и без причины
public record MatchVerdict(@Nullable String winner, Component reason, boolean stopped) {

    public static MatchVerdict finalPoint(String team, boolean sudden) {
        return new MatchVerdict(team, reason(sudden ? "final_sudden" : "final"), false);
    }

    public static MatchVerdict scoreTarget(String team, int target) {
        return new MatchVerdict(team, reason("target", target), false);
    }

    public static MatchVerdict timeCap(@Nullable String team, int minutes) {
        return new MatchVerdict(team, reason(team == null ? "cap.draw" : "cap", minutes), false);
    }

    public static MatchVerdict suddenOver(@Nullable String team) {
        return new MatchVerdict(team, reason(team == null ? "sudden.draw" : "sudden"), false);
    }

    // WHY: сдача без соперника в сети не ничья: победителя назвать некому, матч просто остановлен
    public static MatchVerdict surrender(@Nullable String winner, String loser) {
        return new MatchVerdict(winner, reason("surrender", loser), winner == null);
    }

    public static MatchVerdict operator() {
        return new MatchVerdict(null, reason("operator"), true);
    }

    private static Component reason(String id, Object... arguments) {
        return Component.translatable("battlecraft.outcome." + id, arguments);
    }
}
